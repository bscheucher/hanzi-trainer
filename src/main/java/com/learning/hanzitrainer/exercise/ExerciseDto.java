package com.learning.hanzitrainer.exercise;

import tools.jackson.databind.JsonNode;

import java.util.List;

/** An exercise as the player sees it: the answer is not included (spec 03). */
public record ExerciseDto(Long id, ExerciseType type, JsonNode prompt, List<Long> wordIds) {
}
