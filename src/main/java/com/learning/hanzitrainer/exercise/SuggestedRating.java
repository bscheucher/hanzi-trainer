package com.learning.hanzitrainer.exercise;

/**
 * Suggested FSRS rating for an answer (spec 07, shared with spec 09): 1 Again, 2 Hard, 3 Good, 4 Easy.
 * Handwriting adds stroke mistakes and hints in M6.
 */
final class SuggestedRating {

    static final long EASY_MS = 5_000;

    private SuggestedRating() {
    }

    static int of(double score, long durationMs) {
        if (score < 0.5) {
            return 1;
        }
        if (score < 1) {
            return 2;
        }
        return durationMs < EASY_MS ? 4 : 3;
    }
}
