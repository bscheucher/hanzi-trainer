package com.learning.hanzitrainer.page;

import com.learning.hanzitrainer.extraction.ExtractionResult;
import com.learning.hanzitrainer.word.CandidateDto;

import java.time.Instant;
import java.util.List;

/**
 * @param candidates extraction items enriched for review (spec 06); only in {@code GET /api/pages/{id}} of a DONE page
 * @param confirmed  true once vocabulary from this page was confirmed
 */
public record PageDto(
        Long id,
        Long lessonId,
        Integer pageNo,
        ExtractionStatus status,
        ExtractionResult extraction,
        List<CandidateDto> candidates,
        boolean confirmed,
        String errorMessage,
        Instant createdAt) {
}
