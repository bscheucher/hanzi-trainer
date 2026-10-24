package com.learning.hanzitrainer.review;

/**
 * A card after a review.
 *
 * @param elapsedDays   whole days since the previous review, 0 for a NEW card ({@code review_log.elapsed_days})
 * @param scheduledDays whole days until the new due, 0 within learning steps ({@code review_log.scheduled_days})
 */
public record ScheduledCard(CardSnapshot card, long elapsedDays, long scheduledDays) {
}
