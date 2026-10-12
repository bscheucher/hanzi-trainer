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

    /**
     * @param hanzi needed by Hanzi Writer on the client; not displayed until the word is written
     * @param pinyin tone-marked display form
     */
    record MeaningToHanzi(String hanzi, String meaningEn, String meaningDe, String pinyin, int characterCount)
            implements ExercisePrompt {
    }
}
