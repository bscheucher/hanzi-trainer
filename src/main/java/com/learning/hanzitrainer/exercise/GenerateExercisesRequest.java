package com.learning.hanzitrainer.exercise;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * @param types LLM types only: {@code FILL_BLANK}, {@code WORD_ORDER}, {@code TRANSLATION}
 * @param count exercises per type
 */
public record GenerateExercisesRequest(@NotEmpty List<@NotNull ExerciseType> types,
                                       @NotNull @Min(1) @Max(GenerateExercisesRequest.MAX_COUNT) Integer count) {

    static final int MAX_COUNT = 20;
}
