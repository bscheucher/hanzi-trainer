package com.learning.hanzitrainer.review;

import com.learning.hanzitrainer.common.ConflictException;
import com.learning.hanzitrainer.exercise.AnswerResult;
import com.learning.hanzitrainer.exercise.ExerciseDto;
import com.learning.hanzitrainer.exercise.ExerciseType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spec 03, "Review (SRS)". */
@WebMvcTest(ReviewController.class)
class ReviewControllerTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReviewService reviews;

    @Test
    void dueItemsCarryTheExerciseAndPreviewIntervalsButNoWord() throws Exception {
        ExerciseDto tone = new ExerciseDto(null, ExerciseType.TONE,
                JSON.valueToTree(Map.of("hanzi", "朋友", "syllables", List.of("peng", "you"))), List.of(5L));
        when(reviews.due(30)).thenReturn(List.of(new ReviewItemDto(9, CardType.TONE, 5, tone,
                Map.of("1", "PT1M", "2", "PT5M30S", "3", "PT10M", "4", "P8D"))));

        mockMvc.perform(get("/api/reviews/due"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cardId").value(9))
                .andExpect(jsonPath("$[0].cardType").value("TONE"))
                .andExpect(jsonPath("$[0].wordId").value(5))
                .andExpect(jsonPath("$[0].exercise.id").doesNotExist())
                .andExpect(jsonPath("$[0].exercise.type").value("TONE"))
                .andExpect(jsonPath("$[0].previewIntervals.4").value("P8D"))
                .andExpect(jsonPath("$[0].word").doesNotExist());
    }

    @Test
    void statsHaveCountsPerStateAndType() throws Exception {
        when(reviews.stats()).thenReturn(new ReviewStatsDto(4, 6, 2, 13,
                Map.of(CardState.NEW, 9L, CardState.LEARNING, 1L, CardState.REVIEW, 0L, CardState.RELEARNING, 0L),
                Map.of(CardType.RECOGNITION, 4L, CardType.WRITING, 3L, CardType.TONE, 3L)));

        mockMvc.perform(get("/api/reviews/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueNow").value(4))
                .andExpect(jsonPath("$.dueToday").value(6))
                .andExpect(jsonPath("$.reviewedToday").value(2))
                .andExpect(jsonPath("$.newCardsLeftToday").value(13))
                .andExpect(jsonPath("$.byState.NEW").value(9))
                .andExpect(jsonPath("$.byType.WRITING").value(3));
    }

    @Test
    void answerGradesAndRateReturnsTheCard() throws Exception {
        when(reviews.answer(eq(9L), any())).thenReturn(new AnswerResult(true, 1, JSON.readTree("{}"), null, 4));
        when(reviews.rate(9L, 3)).thenReturn(new CardDto(9, 5, CardType.TONE, CardState.LEARNING,
                Instant.parse("2026-01-05T08:10:00Z"), 2.3065, 2.1181, 1, 0));

        mockMvc.perform(post("/api/reviews/9/answer").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"response\": {\"tones\": [2, 5]}, \"durationMs\": 3000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestedRating").value(4));
        mockMvc.perform(post("/api/reviews/9/rate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\": 3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("LEARNING"))
                .andExpect(jsonPath("$.due").value("2026-01-05T08:10:00Z"))
                .andExpect(jsonPath("$.reps").value(1));
    }

    @Test
    void ratingsOutsideOneToFourAre400AndARepeatIs409() throws Exception {
        for (String body : List.of("{\"rating\": 0}", "{\"rating\": 5}", "{}")) {
            mockMvc.perform(post("/api/reviews/9/rate").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(reviews);

        when(reviews.rate(9L, 3)).thenThrow(new ConflictException("Card 9 was rated less than 5 s ago"));
        mockMvc.perform(post("/api/reviews/9/rate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\": 3}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Card 9 was rated less than 5 s ago"));
    }

    @Test
    void forecastDefaultsToThirtyDays() throws Exception {
        when(reviews.forecast(30)).thenReturn(List.of(new ForecastDayDto(LocalDate.of(2026, 1, 5), 2)));

        mockMvc.perform(get("/api/reviews/forecast"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].date").value("2026-01-05"))
                .andExpect(jsonPath("$[0].count").value(2));
    }
}
