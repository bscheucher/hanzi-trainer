package com.learning.hanzitrainer.review;

import java.time.Instant;

/**
 * The scheduling fields of a review card, without JPA (spec 09).
 *
 * @param step       index into the learning or relearning steps; null in REVIEW (and NEW)
 * @param stability  null until the first review
 * @param difficulty null until the first review
 * @param lastReview null for a NEW card
 */
public record CardSnapshot(CardState state, Integer step, Double stability, Double difficulty, Instant due,
                           Instant lastReview, int reps, int lapses) {

    /** A card that was never reviewed, due at {@code due}. */
    public static CardSnapshot newCard(Instant due) {
        return new CardSnapshot(CardState.NEW, null, null, null, due, null, 0, 0);
    }
}
