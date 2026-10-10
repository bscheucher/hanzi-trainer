package com.learning.hanzitrainer.exercise;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record ExerciseWordId(
        @Column(name = "exercise_id") Long exerciseId,
        @Column(name = "word_id") Long wordId) {
}
