package com.learning.hanzitrainer.exercise;

import java.util.List;

/**
 * One exercise as the LLM writes it (spec 07, "LLM generation"), before {@link GeneratedExercises} checks it and
 * turns it into a stored prompt and answer. These records are also the structured-output schema sent to the model.
 */
public sealed interface GeneratedExercise {

    /**
     * @param sentence    the sentence with the target word replaced by {@code ___}
     * @param correct     the word that fills the blank
     * @param distractors three words that do not fit the sentence
     */
    record FillBlank(String sentence, String translationEn, String correct, List<String> distractors)
            implements GeneratedExercise {
    }

    /** @param accepted every grammatically acceptable order of the same tokens, the most natural first */
    record WordOrder(List<List<String>> accepted, String translationEn) implements GeneratedExercise {
    }

    /** @param reference the Chinese sentence; {@code sourceDe} and {@code sourceEn} translate it */
    record Translation(String sourceDe, String sourceEn, String reference) implements GeneratedExercise {
    }
}
