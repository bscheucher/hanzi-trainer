package com.learning.hanzitrainer.review;

import com.learning.hanzitrainer.word.Word;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/** FSRS state of one word for one skill. */
@Entity
@Table(name = "review_card")
public class ReviewCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "word_id", nullable = false)
    private Word word;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false)
    private CardType cardType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CardState state = CardState.NEW;

    /** Index into the learning/relearning steps; null in REVIEW. */
    private Integer step;

    private Double stability;

    private Double difficulty;

    @Column(nullable = false)
    private Instant due = Instant.now();

    @Column(nullable = false)
    private int reps;

    @Column(nullable = false)
    private int lapses;

    @Column(name = "last_review")
    private Instant lastReview;

    protected ReviewCard() {
    }

    public ReviewCard(Word word, CardType cardType) {
        this.word = word;
        this.cardType = cardType;
    }

    public Long getId() { return id; }
    public Word getWord() { return word; }
    public CardType getCardType() { return cardType; }
    public CardState getState() { return state; }
    public void setState(CardState state) { this.state = state; }
    public Integer getStep() { return step; }
    public void setStep(Integer step) { this.step = step; }
    public Double getStability() { return stability; }
    public void setStability(Double stability) { this.stability = stability; }
    public Double getDifficulty() { return difficulty; }
    public void setDifficulty(Double difficulty) { this.difficulty = difficulty; }
    public Instant getDue() { return due; }
    public void setDue(Instant due) { this.due = due; }
    public int getReps() { return reps; }
    public void setReps(int reps) { this.reps = reps; }
    public int getLapses() { return lapses; }
    public void setLapses(int lapses) { this.lapses = lapses; }
    public Instant getLastReview() { return lastReview; }
    public void setLastReview(Instant lastReview) { this.lastReview = lastReview; }
}
