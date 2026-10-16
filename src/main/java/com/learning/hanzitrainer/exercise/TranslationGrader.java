package com.learning.hanzitrainer.exercise;

import java.util.Optional;

/** Grades a free translation into Chinese with an LLM (spec 07, {@code TRANSLATION}). */
public interface TranslationGrader {

    /**
     * @param source    the German or English sentence the learner translated
     * @param reference the stored Chinese translation, one of possibly many correct ones
     * @param answer    what the learner wrote
     */
    TranslationGrade grade(String source, String reference, String answer);

    /** Empty when the grader can be called; otherwise which setting is missing (endpoints answer 503). */
    default Optional<String> unavailableReason() {
        return Optional.empty();
    }

    /**
     * @param score     0..1
     * @param feedback  English, at most two sentences
     * @param corrected the learner's sentence with the fewest changes that make it correct
     */
    record TranslationGrade(boolean correct, double score, String feedback, String corrected) {
    }
}
