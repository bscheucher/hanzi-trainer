package com.learning.hanzitrainer.exercise;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * @param types null or empty = the default mix
 * @param size  null = {@code hanzi.exercises.default-session-size}
 */
public record PracticeSessionRequest(@NotNull Long lessonId, List<ExerciseType> types,
                                     @Min(1) @Max(100) Integer size) {
}
