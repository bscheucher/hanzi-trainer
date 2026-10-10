package com.learning.hanzitrainer.page;

import com.learning.hanzitrainer.extraction.ExtractionResult;

import java.time.Instant;
import java.util.List;

/**
 * @param candidates extraction items enriched with the CEDICT check; filled from M3 (spec 06), null until then
 * @param confirmed  true once vocabulary from this page was confirmed
 */
public record PageDto(
        Long id,
        Long lessonId,
        Integer pageNo,
        ExtractionStatus status,
        ExtractionResult extraction,
        List<Object> candidates,
        boolean confirmed,
        String errorMessage,
        Instant createdAt) {
}
