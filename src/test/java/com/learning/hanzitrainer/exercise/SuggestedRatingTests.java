package com.learning.hanzitrainer.exercise;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SuggestedRatingTests {

    @Test
    void followsTheTableInSpec07() {
        assertThat(SuggestedRating.of(0, 1_000)).isEqualTo(1);
        assertThat(SuggestedRating.of(0.49, 1_000)).isEqualTo(1);
        assertThat(SuggestedRating.of(0.5, 1_000)).isEqualTo(2);
        assertThat(SuggestedRating.of(1, 5_000)).isEqualTo(3);
        assertThat(SuggestedRating.of(1, 4_999)).isEqualTo(4);
    }
}
