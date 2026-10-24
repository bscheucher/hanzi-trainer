package com.learning.hanzitrainer.exercise;

import com.learning.hanzitrainer.word.WordDto;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Writes sentence exercises with an LLM (spec 07). All LLM access goes through this interface (CLAUDE.md). */
public interface ExerciseGenerator {

    /**
     * @param type         {@code FILL_BLANK}, {@code WORD_ORDER} or {@code TRANSLATION}
     * @param targetWords  every exercise uses at least one of them
     * @param allowedWords the only words the sentences may use
     * @param count        how many exercises to write; the model may return fewer or more
     * @throws com.learning.hanzitrainer.common.UnreadableReplyException if the reply cannot be read; the job
     *                                                                   counts it as one rejection and retries
     */
    List<GeneratedExercise> generate(ExerciseType type, List<WordDto> targetWords, Set<String> allowedWords,
                                     int count);

    /** Stored as {@code exercise.generated_by}. */
    String modelName();

    /** Empty when the generator can be called; otherwise which setting is missing (endpoints answer 503). */
    default Optional<String> unavailableReason() {
        return Optional.empty();
    }
}
