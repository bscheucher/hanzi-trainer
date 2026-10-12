package com.learning.hanzitrainer.exercise;

/** Suggested FSRS rating for an answer (spec 07, shared with spec 09): 1 Again, 2 Hard, 3 Good, 4 Easy. */
final class SuggestedRating {

    static final long EASY_MS = 5_000;
    static final long EASY_WRITING_MS = 8_000;
    /** More total stroke mistakes than this make a correct handwriting answer Hard. */
    static final int HARD_STROKE_MISTAKES = 2;

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

    /** Handwriting: hints or more than 2 stroke mistakes make it Hard; Easy needs no mistakes and under 8 s. */
    static int writing(double score, long durationMs, int strokeMistakes, int hintsUsed) {
        if (score < 0.5) {
            return 1;
        }
        if (score < 1 || strokeMistakes > HARD_STROKE_MISTAKES || hintsUsed > 0) {
            return 2;
        }
        return strokeMistakes == 0 && durationMs < EASY_WRITING_MS ? 4 : 3;
    }
}
