package com.learning.hanzitrainer.review;

import java.time.Instant;

public record CardDto(long id, long wordId, CardType cardType, CardState state, Instant due, Double stability,
                      Double difficulty, int reps, int lapses) {

    static CardDto from(ReviewCard card) {
        return new CardDto(card.getId(), card.getWord().getId(), card.getCardType(), card.getState(), card.getDue(),
                card.getStability(), card.getDifficulty(), card.getReps(), card.getLapses());
    }
}
