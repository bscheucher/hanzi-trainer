package com.learning.hanzitrainer.page;

import com.learning.hanzitrainer.common.ConflictException;
import com.learning.hanzitrainer.common.NotFoundException;
import com.learning.hanzitrainer.common.ServiceUnavailableException;
import com.learning.hanzitrainer.common.UnsupportedMediaTypeException;
import com.learning.hanzitrainer.extraction.VocabularyExtractor;
import com.learning.hanzitrainer.textbook.Lesson;
import com.learning.hanzitrainer.textbook.LessonRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Upload, re-extraction and deletion of page images (spec 05, "Upload flow"). */
@Service
@Transactional
public class PageService {

    private final PageImageRepository pages;
    private final LessonRepository lessons;
    private final FileStorage storage;
    private final VocabularyExtractor extractor;
    private final PageMapper mapper;
    private final ApplicationEventPublisher events;

    public PageService(PageImageRepository pages, LessonRepository lessons, FileStorage storage,
                       VocabularyExtractor extractor, PageMapper mapper, ApplicationEventPublisher events) {
        this.pages = pages;
        this.lessons = lessons;
        this.storage = storage;
        this.extractor = extractor;
        this.mapper = mapper;
        this.events = events;
    }

    public PageDto upload(long lessonId, byte[] content, Integer pageNo) {
        Lesson lesson = lessons.findById(lessonId).orElseThrow(() -> new NotFoundException("Lesson", lessonId));
        requireExtractor();
        ImageType type = ImageType.detect(content)
                .orElseThrow(() -> new UnsupportedMediaTypeException("Upload a JPEG or PNG image."));

        String path = storage.store(lessonId, type.extension(), content);
        deleteFileOnRollback(path);
        PageImage page = pages.save(new PageImage(lesson, pageNo, path, type.mimeType()));
        events.publishEvent(new PageUploadedEvent(page.getId()));
        return mapper.toDto(page);
    }

    @Transactional(readOnly = true)
    public PageDto get(long pageId) {
        return mapper.toDto(find(pageId));
    }

    @Transactional(readOnly = true)
    public StoredImage image(long pageId) {
        PageImage page = find(pageId);
        return new StoredImage(storage.read(page.getStoragePath()), page.getMimeType());
    }

    /** Keeps the previous result until the new one is stored; confirmed words and links are untouched. */
    public PageDto rerun(long pageId) {
        PageImage page = find(pageId);
        if (page.getExtractionStatus() == ExtractionStatus.RUNNING) {
            throw new ConflictException("Extraction of page " + pageId + " is already running");
        }
        requireExtractor();
        page.setExtractionStatus(ExtractionStatus.PENDING);
        page.setErrorMessage(null);
        events.publishEvent(new PageUploadedEvent(pageId));
        return mapper.toDto(page);
    }

    public void delete(long pageId) {
        PageImage page = find(pageId);
        pages.delete(page);
        String path = page.getStoragePath();
        afterCommit(() -> storage.delete(path));
    }

    private PageImage find(long pageId) {
        return pages.findById(pageId).orElseThrow(() -> new NotFoundException("Page", pageId));
    }

    private void requireExtractor() {
        extractor.unavailableReason().ifPresent(reason -> {
            throw new ServiceUnavailableException(reason);
        });
    }

    private void deleteFileOnRollback(String path) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    storage.delete(path);
                }
            }
        });
    }

    private static void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    public record StoredImage(byte[] content, String mimeType) {
    }
}
