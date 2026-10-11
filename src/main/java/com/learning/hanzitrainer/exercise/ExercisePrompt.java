package com.learning.hanzitrainer.exercise;

import java.util.List;

/**
 * What the player renders, per exercise type (spec 07). Stored as {@code exercise.prompt}; the exercise
 * {@code type} column is the discriminator, so the JSON carries no type field (spec 02).
 */
public sealed interface ExercisePrompt {

    record HanziToMeaning(String hanzi, List<String> options) implements ExercisePrompt {
    }

    record Tone(String hanzi, List<String> syllables) implements ExercisePrompt {
    }
}
