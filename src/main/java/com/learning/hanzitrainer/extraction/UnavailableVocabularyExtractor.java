package com.learning.hanzitrainer.extraction;

import com.learning.hanzitrainer.common.ServiceUnavailableException;

import java.util.Optional;

/** Stands in when the selected LLM provider is not configured, so the app still starts (spec 01). */
public class UnavailableVocabularyExtractor implements VocabularyExtractor {

    private final String reason;

    public UnavailableVocabularyExtractor(String reason) {
        this.reason = reason;
    }

    @Override
    public ExtractionResult extract(byte[] jpegImage) {
        throw new ServiceUnavailableException(reason);
    }

    @Override
    public String modelName() {
        return "unavailable";
    }

    @Override
    public Optional<String> unavailableReason() {
        return Optional.of(reason);
    }
}
