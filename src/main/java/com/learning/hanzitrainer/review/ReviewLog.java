package com.learning.hanzitrainer.review;

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

/** One rating ever given to a card. Append-only. */
@Entity
@Table(name = "review_log")
public class ReviewLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_id", nullable = false)
    private ReviewCard card;

    /** 1 Again, 2 Hard, 3 Good, 4 Easy. */
    @Column(nullable = false)
    private short rating;

    @Enumerated(EnumType.STRING)
    @Column(name = "state_before", nullable = false)
    private CardState stateBefore;

    @Column(name = "elapsed_days", nullable = false)
    private int elapsedDays;

    @Column(name = "scheduled_days", nullable = false)
    private int scheduledDays;

    @Column(name = "reviewed_at", nullable = false, updatable = false)
    private Instant reviewedAt;

    protected ReviewLog() {
    }

    public ReviewLog(ReviewCard card, short rating, CardState stateBefore, int elapsedDays, int scheduledDays,
                     Instant reviewedAt) {
        this.card = card;
        this.rating = rating;
        this.stateBefore = stateBefore;
        this.elapsedDays = elapsedDays;
        this.scheduledDays = scheduledDays;
        this.reviewedAt = reviewedAt;
    }

    public Long getId() { return id; }
    public ReviewCard getCard() { return card; }
    public short getRating() { return rating; }
    public CardState getStateBefore() { return stateBefore; }
    public int getElapsedDays() { return elapsedDays; }
    public int getScheduledDays() { return scheduledDays; }
    public Instant getReviewedAt() { return reviewedAt; }
}
