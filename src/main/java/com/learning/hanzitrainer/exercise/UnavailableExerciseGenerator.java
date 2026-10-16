package com.learning.hanzitrainer.exercise;

import com.learning.hanzitrainer.common.ServiceUnavailableException;
import com.learning.hanzitrainer.word.WordDto;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Stands in when the selected LLM provider is not configured, so the app still starts (spec 01). */
public class UnavailableExerciseGenerator implements ExerciseGenerator {

    private final String reason;

    public UnavailableExerciseGenerator(String reason) {
        this.reason = reason;
    }

    @Override
    public List<GeneratedExercise> generate(ExerciseType type, List<WordDto> targetWords, Set<String> allowedWords,
                                            int count) {
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
