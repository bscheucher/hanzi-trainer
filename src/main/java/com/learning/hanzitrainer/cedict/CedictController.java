package com.learning.hanzitrainer.cedict;

import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dictionary")
public class CedictController {

    static final int MAX_RESULTS = 20;

    private final CedictService cedictService;

    public CedictController(CedictService cedictService) {
        this.cedictService = cedictService;
    }

    /** Exact match on the simplified form (pinyin search is out of scope for v1). */
    @GetMapping
    public List<CedictEntryDto> lookup(@RequestParam @NotBlank String q) {
        return cedictService.lookup(q).stream()
                .limit(MAX_RESULTS)
                .map(CedictEntryDto::from)
                .toList();
    }
}
