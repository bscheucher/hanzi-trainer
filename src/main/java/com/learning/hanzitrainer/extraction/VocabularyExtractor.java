package com.learning.hanzitrainer.extraction;

import java.util.Optional;

/** Reads vocabulary from a textbook page image. All LLM access goes through this interface (CLAUDE.md). */
public interface VocabularyExtractor {

    ExtractionResult extract(byte[] jpegImage);

    String modelName();

    /** Empty when the extractor can be called; otherwise which setting is missing (endpoints answer 503). */
    default Optional<String> unavailableReason() {
        return Optional.empty();
    }
}
