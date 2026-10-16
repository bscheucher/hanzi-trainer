package com.learning.hanzitrainer.exercise;

import com.learning.hanzitrainer.common.ServiceUnavailableException;

import java.util.Optional;

/** Stands in when the selected LLM provider is not configured, so the app still starts (spec 01). */
public class UnavailableTranslationGrader implements TranslationGrader {

    private final String reason;

    public UnavailableTranslationGrader(String reason) {
        this.reason = reason;
    }

    @Override
    public TranslationGrade grade(String source, String reference, String answer) {
        throw new ServiceUnavailableException(reason);
    }

    @Override
    public Optional<String> unavailableReason() {
        return Optional.of(reason);
    }
}
