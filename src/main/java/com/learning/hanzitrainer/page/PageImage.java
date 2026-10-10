package com.learning.hanzitrainer.page;

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

/** One scanned textbook page; holds the unreviewed LLM output in {@code rawExtraction}. */
@Entity
@Table(name = "page_image")
public class PageImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @Column(name = "page_no")
    private Integer pageNo;

    @Column(name = "storage_path", nullable = false)
    private String storagePath;

    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "extraction_status", nullable = false)
    private ExtractionStatus extractionStatus = ExtractionStatus.PENDING;

    @Column(name = "extraction_model")
    private String extractionModel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_extraction")
    private JsonNode rawExtraction;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected PageImage() {
    }

    public PageImage(Lesson lesson, Integer pageNo, String storagePath, String mimeType) {
        this.lesson = lesson;
        this.pageNo = pageNo;
        this.storagePath = storagePath;
        this.mimeType = mimeType;
    }

    public Long getId() { return id; }
    public Lesson getLesson() { return lesson; }
    public Integer getPageNo() { return pageNo; }
    public String getStoragePath() { return storagePath; }
    public String getMimeType() { return mimeType; }
    public ExtractionStatus getExtractionStatus() { return extractionStatus; }
    public void setExtractionStatus(ExtractionStatus extractionStatus) { this.extractionStatus = extractionStatus; }
    public String getExtractionModel() { return extractionModel; }
    public void setExtractionModel(String extractionModel) { this.extractionModel = extractionModel; }
    public JsonNode getRawExtraction() { return rawExtraction; }
    public void setRawExtraction(JsonNode rawExtraction) { this.rawExtraction = rawExtraction; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(Instant confirmedAt) { this.confirmedAt = confirmedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
