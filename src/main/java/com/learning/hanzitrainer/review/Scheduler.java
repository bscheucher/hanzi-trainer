package com.learning.hanzitrainer.review;

import java.time.Instant;

/** Schedules the next review of a card (spec 09). Pure Java: no Spring, no database. */
public interface Scheduler {

    ScheduledCard review(CardSnapshot card, Rating rating, Instant now);
}
