package com.learning.hanzitrainer.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** @param rating 1 Again, 2 Hard, 3 Good, 4 Easy */
public record RateRequest(@NotNull @Min(1) @Max(4) Integer rating) {
}
