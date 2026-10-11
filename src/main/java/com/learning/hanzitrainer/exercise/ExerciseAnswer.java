package com.learning.hanzitrainer.exercise;

import java.util.List;

/** Answer key for grading, stored as {@code exercise.answer}; never sent to the client before answering. */
public sealed interface ExerciseAnswer {

    /** @param correct the word's {@code meaningEn} verbatim */
    record HanziToMeaning(String correct) implements ExerciseAnswer {
    }

    /** @param tones citation tones per asked syllable, neutral = 5 */
    record Tone(List<Integer> tones) implements ExerciseAnswer {
    }
}
