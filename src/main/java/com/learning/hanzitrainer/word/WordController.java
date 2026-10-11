package com.learning.hanzitrainer.word;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class WordController {

    static final int DEFAULT_PAGE_SIZE = 50;
    static final int MAX_PAGE_SIZE = 200;

    private final ConfirmationService confirmationService;
    private final WordService wordService;

    public WordController(ConfirmationService confirmationService, WordService wordService) {
        this.confirmationService = confirmationService;
        this.wordService = wordService;
    }

    @PostMapping("/lessons/{lessonId}/words/confirm")
    public ConfirmResult confirm(@PathVariable long lessonId, @Valid @RequestBody ConfirmRequest request) {
        return confirmationService.confirm(lessonId, request);
    }

    @DeleteMapping("/lessons/{lessonId}/words/{wordId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlink(@PathVariable long lessonId, @PathVariable long wordId) {
        wordService.unlink(lessonId, wordId);
    }

    /** Serialised as {@code Paged<WordDto>} ({@code VIA_DTO}, see {@code WebConfig}). */
    @GetMapping("/words")
    public Page<WordDto> search(@RequestParam(required = false) Long lessonId,
                                @RequestParam(required = false) String q,
                                @RequestParam(defaultValue = "0") @Min(0) int page,
                                @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        return wordService.search(lessonId, q, PageRequest.of(page, size));
    }

    @GetMapping("/words/{id}")
    public WordDetailDto detail(@PathVariable long id) {
        return wordService.detail(id);
    }

    @PatchMapping("/words/{id}")
    public WordDto update(@PathVariable long id, @Valid @RequestBody UpdateWordRequest request) {
        return wordService.update(id, request);
    }

    @DeleteMapping("/words/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        wordService.delete(id);
    }
}
