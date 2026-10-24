package com.learning.hanzitrainer.exercise;

import com.learning.hanzitrainer.textbook.Lesson;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

import java.time.Instant;

/** Stored exercise; {@code type} is the discriminator for the {@code prompt} / {@code answer} JSON shapes. */
@Entity
@Table(name = "exercise")
public class Exercise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExerciseType type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private JsonNode prompt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private JsonNode answer;

    /** The Chinese sentence of a sentence exercise, unique per lesson and type; null for the other types. */
    private String sentence;

    /** {@code "code"} or the model name. */
    @Column(name = "generated_by", nullable = false)
    private String generatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Exercise() {
    }

    public Exercise(Lesson lesson, ExerciseType type, JsonNode prompt, JsonNode answer, String generatedBy) {
        this(lesson, type, prompt, answer, null, generatedBy);
    }

    /** @param sentence set for sentence exercises only (see {@link #getSentence()}) */
    public Exercise(Lesson lesson, ExerciseType type, JsonNode prompt, JsonNode answer, String sentence,
                    String generatedBy) {
        this.lesson = lesson;
        this.type = type;
        this.prompt = prompt;
        this.answer = answer;
        this.sentence = sentence;
        this.generatedBy = generatedBy;
    }

    public Long getId() { return id; }
    public Lesson getLesson() { return lesson; }
    public ExerciseType getType() { return type; }
    public JsonNode getPrompt() { return prompt; }
    public JsonNode getAnswer() { return answer; }
    public String getSentence() { return sentence; }
    public String getGeneratedBy() { return generatedBy; }
    public Instant getCreatedAt() { return createdAt; }
}
