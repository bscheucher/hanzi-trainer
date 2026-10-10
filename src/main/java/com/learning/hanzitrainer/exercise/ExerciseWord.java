package com.learning.hanzitrainer.exercise;

import com.learning.hanzitrainer.word.Word;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

/** A word practised by an exercise. */
@Entity
@Table(name = "exercise_word")
public class ExerciseWord {

    @EmbeddedId
    private ExerciseWordId id;

    @MapsId("exerciseId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_id")
    private Exercise exercise;

    @MapsId("wordId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "word_id")
    private Word word;

    protected ExerciseWord() {
    }

    public ExerciseWord(Exercise exercise, Word word) {
        this.id = new ExerciseWordId(exercise.getId(), word.getId());
        this.exercise = exercise;
        this.word = word;
    }

    public ExerciseWordId getId() { return id; }
    public Exercise getExercise() { return exercise; }
    public Word getWord() { return word; }
}
