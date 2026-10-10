package com.learning.hanzitrainer.extraction;

import com.learning.hanzitrainer.page.ExtractionStatus;
import com.learning.hanzitrainer.page.PageImageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * Extraction jobs live only in memory, so after a restart no job will finish pages left PENDING or RUNNING.
 * They are marked FAILED so the user can re-run them. Runs before the web server accepts uploads.
 */
@Component
public class ExtractionRecovery implements SmartInitializingSingleton {

    static final String MESSAGE = "Interrupted by restart";
    private static final Logger log = LoggerFactory.getLogger(ExtractionRecovery.class);

    private final PageImageRepository pages;
    private final TransactionTemplate tx;

    public ExtractionRecovery(PageImageRepository pages, TransactionTemplate tx) {
        this.pages = pages;
        this.tx = tx;
    }

    @Override
    public void afterSingletonsInstantiated() {
        Integer failed = tx.execute(s -> pages.failAll(
                List.of(ExtractionStatus.PENDING, ExtractionStatus.RUNNING), ExtractionStatus.FAILED, MESSAGE));
        if (failed != null && failed > 0) {
            log.warn("Marked {} page(s) with unfinished extraction as FAILED", failed);
        }
    }
}
