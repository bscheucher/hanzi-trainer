package com.learning.hanzitrainer.review;

import java.util.Map;

/**
 * The review dashboard (spec 09). Due counts include NEW cards only up to {@code newCardsLeftToday}, and leave out
 * cards that cannot be asked (see {@code ReviewService}).
 */
public record ReviewStatsDto(long dueNow, long dueToday, long reviewedToday, long newCardsLeftToday,
                             Map<CardState, Long> byState, Map<CardType, Long> byType) {
}
