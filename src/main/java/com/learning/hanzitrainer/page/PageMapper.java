package com.learning.hanzitrainer.page;

import com.learning.hanzitrainer.extraction.ExtractionResult;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class PageMapper {

    private final JsonMapper jsonMapper;

    public PageMapper(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public PageDto toDto(PageImage page) {
        return new PageDto(page.getId(), page.getLesson().getId(), page.getPageNo(), page.getExtractionStatus(),
                toResult(page.getRawExtraction()), null, page.getConfirmedAt() != null, page.getErrorMessage(),
                page.getCreatedAt());
    }

    public JsonNode toJson(ExtractionResult result) {
        return jsonMapper.valueToTree(result);
    }

    private ExtractionResult toResult(JsonNode json) {
        return json == null || json.isNull() ? null : jsonMapper.treeToValue(json, ExtractionResult.class);
    }
}
