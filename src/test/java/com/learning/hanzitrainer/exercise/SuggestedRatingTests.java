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

    @Test
    void handwritingIsHardWithHintsOrMoreThanTwoMistakesAndEasyOnlyWithoutMistakes() {
        assertThat(SuggestedRating.writing(0, 1_000, 8, 0)).isEqualTo(1);
        assertThat(SuggestedRating.writing(0.5, 1_000, 4, 0)).isEqualTo(2);
        assertThat(SuggestedRating.writing(1, 1_000, 3, 0)).isEqualTo(2);
        assertThat(SuggestedRating.writing(1, 1_000, 0, 1)).isEqualTo(2);
        assertThat(SuggestedRating.writing(1, 1_000, 2, 0)).isEqualTo(3);
        assertThat(SuggestedRating.writing(1, 8_000, 0, 0)).isEqualTo(3);
        assertThat(SuggestedRating.writing(1, 7_999, 0, 0)).isEqualTo(4);
    }
}
