package com.learning.hanzitrainer.word;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record LessonWordId(
        @Column(name = "lesson_id") Long lessonId,
        @Column(name = "word_id") Long wordId) {
}
