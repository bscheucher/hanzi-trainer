package com.learning.hanzitrainer.page;

import com.learning.hanzitrainer.extraction.ExtractionResult;
import com.learning.hanzitrainer.word.CandidateDto;
import com.learning.hanzitrainer.word.CandidateService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

@Component
public class PageMapper {

    private final JsonMapper jsonMapper;
    private final CandidateService candidateService;

    public PageMapper(JsonMapper jsonMapper, CandidateService candidateService) {
        this.jsonMapper = jsonMapper;
        this.candidateService = candidateService;
    }

    /** Without candidates (lists, upload, polling while not DONE). */
    public PageDto toDto(PageImage page) {
        return toDto(page, false);
    }

    /** With review candidates when extraction is DONE (spec 06); used by {@code GET /api/pages/{id}}. */
    public PageDto toDetailDto(PageImage page) {
        return toDto(page, true);
    }

    private PageDto toDto(PageImage page, boolean withCandidates) {
        ExtractionResult extraction = toResult(page.getRawExtraction());
        long lessonId = page.getLesson().getId();
        List<CandidateDto> candidates = withCandidates && extraction != null
                && page.getExtractionStatus() == ExtractionStatus.DONE
                ? candidateService.candidates(lessonId, extraction) : null;
        return new PageDto(page.getId(), lessonId, page.getPageNo(), page.getExtractionStatus(), extraction,
                candidates, page.getConfirmedAt() != null, page.getErrorMessage(), page.getCreatedAt());
    }

    public JsonNode toJson(ExtractionResult result) {
        return jsonMapper.valueToTree(result);
    }

    private ExtractionResult toResult(JsonNode json) {
        return json == null || json.isNull() ? null : jsonMapper.treeToValue(json, ExtractionResult.class);
    }
}
