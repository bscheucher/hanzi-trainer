package com.learning.hanzitrainer.exercise;

import tools.jackson.databind.JsonNode;

/**
 * Result of answering an exercise (spec 03).
 *
 * @param score           0..1
 * @param expected        the exercise's answer key, revealed after answering
 * @param feedback        short English explanation when wrong, else null
 * @param suggestedRating 1 Again, 2 Hard, 3 Good, 4 Easy; not applied in free practice
 */
public record AnswerResult(boolean correct, double score, JsonNode expected, String feedback, int suggestedRating) {
}
