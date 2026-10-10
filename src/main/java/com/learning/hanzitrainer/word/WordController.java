package com.learning.hanzitrainer.word;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class WordController {

    private final ConfirmationService confirmationService;

    public WordController(ConfirmationService confirmationService) {
        this.confirmationService = confirmationService;
    }

    @PostMapping("/lessons/{lessonId}/words/confirm")
    public ConfirmResult confirm(@PathVariable long lessonId, @Valid @RequestBody ConfirmRequest request) {
        return confirmationService.confirm(lessonId, request);
    }
}
