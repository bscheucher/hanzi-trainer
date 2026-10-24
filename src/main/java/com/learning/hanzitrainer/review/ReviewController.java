package com.learning.hanzitrainer.review;

import com.learning.hanzitrainer.exercise.AnswerRequest;
import com.learning.hanzitrainer.exercise.AnswerResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Spec 03, "Review (SRS)". */
@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviews;

    public ReviewController(ReviewService reviews) {
        this.reviews = reviews;
    }

    @GetMapping("/stats")
    public ReviewStatsDto stats() {
        return reviews.stats();
    }

    @GetMapping("/due")
    public List<ReviewItemDto> due(@RequestParam(defaultValue = "30") int limit) {
        return reviews.due(limit);
    }

    @PostMapping("/{cardId}/answer")
    public AnswerResult answer(@PathVariable long cardId, @Valid @RequestBody AnswerRequest request) {
        return reviews.answer(cardId, request);
    }

    @PostMapping("/{cardId}/rate")
    public CardDto rate(@PathVariable long cardId, @Valid @RequestBody RateRequest request) {
        return reviews.rate(cardId, request.rating());
    }

    @GetMapping("/forecast")
    public List<ForecastDayDto> forecast(@RequestParam(defaultValue = "30") int days) {
        return reviews.forecast(days);
    }
}
