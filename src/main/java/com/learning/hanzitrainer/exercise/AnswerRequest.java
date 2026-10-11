package com.learning.hanzitrainer.exercise;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

/**
 * @param response   type-specific response, see spec 07
 * @param durationMs time from showing the exercise to answering, for the suggested rating
 * @param exerciseId reviews only (M8)
 */
public record AnswerRequest(@NotNull JsonNode response, @NotNull @Min(0) Long durationMs, Long exerciseId) {
}
