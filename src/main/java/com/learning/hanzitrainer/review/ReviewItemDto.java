package com.learning.hanzitrainer.review;

import com.learning.hanzitrainer.exercise.ExerciseDto;

import java.util.Map;

/**
 * A due card with the exercise to ask (spec 03). No WordDto: it would reveal the answer.
 *
 * @param exercise         built on the fly ({@code id} null), or a stored FILL_BLANK ({@code id} set)
 * @param previewIntervals per rating "1".."4", the interval that rating would schedule, as an ISO-8601 duration
 */
public record ReviewItemDto(long cardId, CardType cardType, long wordId, ExerciseDto exercise,
                            Map<String, String> previewIntervals) {
}
