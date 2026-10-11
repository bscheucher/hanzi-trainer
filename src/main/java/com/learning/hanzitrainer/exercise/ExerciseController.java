package com.learning.hanzitrainer.exercise;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ExerciseController {

    private final ExerciseService exerciseService;
    private final PracticeService practiceService;

    public ExerciseController(ExerciseService exerciseService, PracticeService practiceService) {
        this.exerciseService = exerciseService;
        this.practiceService = practiceService;
    }

    @GetMapping("/lessons/{lessonId}/exercises")
    public List<ExerciseDto> list(@PathVariable long lessonId, @RequestParam(required = false) ExerciseType type) {
        return exerciseService.list(lessonId, type);
    }

    @PostMapping("/practice/sessions")
    public PracticeSessionDto createSession(@Valid @RequestBody PracticeSessionRequest request) {
        return practiceService.createSession(request);
    }

    @PostMapping("/exercises/{id}/answer")
    public AnswerResult answer(@PathVariable long id, @Valid @RequestBody AnswerRequest request) {
        return exerciseService.answer(id, request);
    }

    @DeleteMapping("/exercises/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        exerciseService.delete(id);
    }
}
