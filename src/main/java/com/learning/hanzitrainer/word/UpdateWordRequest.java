package com.learning.hanzitrainer.word;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code PATCH /words/{id}}: null leaves a field unchanged; a blank German meaning or part of speech clears it.
 * The English meaning is required for every word, so it cannot be cleared.
 */
public record UpdateWordRequest(
        @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") String meaningEn,
        String meaningDe,
        @Size(max = 50) String partOfSpeech) {
}
