package com.learning.hanzitrainer.extraction;

import com.learning.hanzitrainer.common.HanziProperties;
import com.learning.hanzitrainer.page.ExtractionStatus;
import com.learning.hanzitrainer.page.FileStorage;
import com.learning.hanzitrainer.page.PageImageRepository;
import com.learning.hanzitrainer.page.PageMapper;
import com.learning.hanzitrainer.page.PageUploadedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs extraction for one page: PENDING → RUNNING → DONE | FAILED (spec 05, "Extraction job").
 * At most {@code hanzi.extraction.max-concurrent} model calls run at once; a page waits as PENDING for a slot,
 * and its timeout only starts when its own call starts.
 */
@Component
public class ExtractionJob {

    private static final Logger log = LoggerFactory.getLogger(ExtractionJob.class);
    private static final int MAX_ERROR_LENGTH = 500;
    /** ExecutorService futures interrupt the call on cancel(true), unlike CompletableFuture. */
    private static final ExecutorService MODEL_CALLS = Executors.newVirtualThreadPerTaskExecutor();

    private final PageImageRepository pages;
    private final FileStorage storage;
    private final VocabularyExtractor extractor;
    private final PageMapper mapper;
    private final HanziProperties properties;
    private final TransactionTemplate tx;
    private final Semaphore modelSlots;

    public ExtractionJob(PageImageRepository pages, FileStorage storage, VocabularyExtractor extractor,
                         PageMapper mapper, HanziProperties properties, TransactionTemplate tx) {
        this.pages = pages;
        this.storage = storage;
        this.extractor = extractor;
        this.mapper = mapper;
        this.properties = properties;
        this.tx = tx;
        this.modelSlots = new Semaphore(
                properties.extraction().effectiveMaxConcurrent(properties.llm().provider()), true);
    }

    @Async
    @TransactionalEventListener
    public void onPageUploaded(PageUploadedEvent event) {
        run(event.pageId());
    }

    void run(long pageId) {
        try {
            modelSlots.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        try {
            extract(pageId);
        } finally {
            modelSlots.release();
        }
    }

    private void extract(long pageId) {
        Integer claimed = tx.execute(s -> pages.transition(pageId, ExtractionStatus.PENDING, ExtractionStatus.RUNNING));
        if (claimed == null || claimed == 0) {
            return; // deleted meanwhile, or another run picked it up
        }
        try {
            String path = tx.execute(s -> pages.findById(pageId).map(p -> p.getStoragePath()).orElse(null));
            if (path == null) {
                return;
            }
            byte[] jpeg = ImageProcessor.toJpeg(storage.read(path), properties.extraction().maxImagePx());
            ExtractionResult result = ExtractionResultValidator.validate(callModel(jpeg));
            finish(pageId, ExtractionStatus.DONE, result, null);
            log.info("Extracted {} words from page {} with {}", result.words().size(), pageId, extractor.modelName());
        } catch (TimeoutException e) {
            log.warn("Extraction of page {} timed out", pageId);
            finish(pageId, ExtractionStatus.FAILED, null,
                    "Extraction timed out after " + properties.extraction().timeout().toSeconds() + " s");
        } catch (Exception e) {
            log.error("Extraction of page {} failed", pageId, e);
            finish(pageId, ExtractionStatus.FAILED, null, shortMessage(e));
        }
    }

    private ExtractionResult callModel(byte[] jpeg) throws Exception {
        Future<ExtractionResult> call = MODEL_CALLS.submit(() -> extractor.extract(jpeg));
        try {
            return call.get(properties.extraction().timeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            call.cancel(true); // interrupts the call; the HTTP clients' own timeouts are the backstop
            throw e;
        } catch (ExecutionException e) {
            throw e.getCause() instanceof Exception cause ? cause : e;
        }
    }

    /** On failure the previous result is kept (re-runs replace it only on success). */
    private void finish(long pageId, ExtractionStatus status, ExtractionResult result, String error) {
        tx.executeWithoutResult(s -> pages.findById(pageId).ifPresent(page -> {
            page.setExtractionStatus(status);
            page.setErrorMessage(error);
            if (result != null) {
                page.setRawExtraction(mapper.toJson(result));
                page.setExtractionModel(extractor.modelName());
            }
        }));
    }

    private static String shortMessage(Throwable e) {
        String message = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH - 1) + "…";
    }
}
