package com.learning.hanzitrainer.review;

/** FSRS rating (spec 09); stored as {@code review_log.rating}. */
public enum Rating {
    AGAIN(1), HARD(2), GOOD(3), EASY(4);

    private final int value;

    Rating(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    public static Rating of(int value) {
        for (Rating rating : values()) {
            if (rating.value == value) {
                return rating;
            }
        }
        throw new IllegalArgumentException("rating must be 1..4, not " + value);
    }
}
