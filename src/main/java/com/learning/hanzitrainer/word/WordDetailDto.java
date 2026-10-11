package com.learning.hanzitrainer.word;

import com.learning.hanzitrainer.review.CardState;
import com.learning.hanzitrainer.review.CardType;

import java.time.Instant;
import java.util.List;

/** {@link WordDto} plus the lessons the word appears in and its review cards (spec 03). */
public record WordDetailDto(Long id, String hanzi, String pinyin, String pinyinNumbered, String meaningEn,
                            String meaningDe, String partOfSpeech, boolean verified, List<LessonRef> lessons,
                            List<CardRef> cards) {

    public record LessonRef(Long lessonId, int lessonNo, String textbookTitle, String exampleSentence) {
    }

    public record CardRef(CardType cardType, CardState state, Instant due) {
    }
}
