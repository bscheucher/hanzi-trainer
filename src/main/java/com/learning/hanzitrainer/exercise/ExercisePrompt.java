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

    /** @param sentence the sentence with exactly one {@code ___} blank */
    record FillBlank(String sentence, String translationEn, List<String> options) implements ExercisePrompt {
    }

    /** @param tokens shuffled; never in an accepted order */
    record WordOrder(List<String> tokens, String translationEn) implements ExercisePrompt {
    }

    /**
     * @param sourceLang {@code de} or {@code en}
     * @param hintWords  the lesson words the reference uses
     */
    record Translation(String source, String sourceLang, List<String> hintWords) implements ExercisePrompt {
    }
}
