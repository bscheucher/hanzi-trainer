package com.learning.hanzitrainer.textbook;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "lesson")
public class Lesson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "textbook_id", nullable = false)
    private Textbook textbook;

    @Column(name = "lesson_no", nullable = false)
    private int lessonNo;

    private String title;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Lesson() {
    }

    public Lesson(Textbook textbook, int lessonNo, String title) {
        this.textbook = textbook;
        this.lessonNo = lessonNo;
        this.title = title;
    }

    public Long getId() { return id; }
    public Textbook getTextbook() { return textbook; }
    public int getLessonNo() { return lessonNo; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Instant getCreatedAt() { return createdAt; }
}
