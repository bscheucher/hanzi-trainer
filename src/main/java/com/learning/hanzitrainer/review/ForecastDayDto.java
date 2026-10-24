package com.learning.hanzitrainer.review;

import java.time.LocalDate;

/** Cards falling due on a local day; overdue cards count on today (spec 09, dashboard). */
public record ForecastDayDto(LocalDate date, long count) {
}
