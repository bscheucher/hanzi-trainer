package com.learning.hanzitrainer.textbook;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class TextbookController {

    private final TextbookService textbookService;

    public TextbookController(TextbookService textbookService) {
        this.textbookService = textbookService;
    }

    @GetMapping("/textbooks")
    public List<TextbookDto> listTextbooks() {
        return textbookService.listTextbooks();
    }

    @PostMapping("/textbooks")
    @ResponseStatus(HttpStatus.CREATED)
    public TextbookDto createTextbook(@Valid @RequestBody CreateTextbookRequest request) {
        return textbookService.createTextbook(request);
    }

    @GetMapping("/textbooks/{id}/lessons")
    public List<LessonSummaryDto> listLessons(@PathVariable long id) {
        return textbookService.listLessons(id);
    }

    @PostMapping("/textbooks/{id}/lessons")
    @ResponseStatus(HttpStatus.CREATED)
    public LessonSummaryDto createLesson(@PathVariable long id, @Valid @RequestBody CreateLessonRequest request) {
        return textbookService.createLesson(id, request);
    }

    @GetMapping("/lessons/{id}")
    public LessonDetailDto getLesson(@PathVariable long id) {
        return textbookService.getLesson(id);
    }

    @DeleteMapping("/lessons/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLesson(@PathVariable long id) {
        textbookService.deleteLesson(id);
    }
}
