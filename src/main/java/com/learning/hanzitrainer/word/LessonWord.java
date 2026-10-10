package com.learning.hanzitrainer.word;

import com.learning.hanzitrainer.page.PageImage;
import com.learning.hanzitrainer.textbook.Lesson;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

/** A word introduced by a lesson, with the page it came from and an example sentence. */
@Entity
@Table(name = "lesson_word")
public class LessonWord {

    @EmbeddedId
    private LessonWordId id;

    @MapsId("lessonId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id")
    private Lesson lesson;

    @MapsId("wordId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "word_id")
    private Word word;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_page_id")
    private PageImage sourcePage;

    @Column(name = "example_sentence")
    private String exampleSentence;

    protected LessonWord() {
    }

    public LessonWord(Lesson lesson, Word word) {
        this.id = new LessonWordId(lesson.getId(), word.getId());
        this.lesson = lesson;
        this.word = word;
    }

    public LessonWordId getId() { return id; }
    public Lesson getLesson() { return lesson; }
    public Word getWord() { return word; }
    public PageImage getSourcePage() { return sourcePage; }
    public void setSourcePage(PageImage sourcePage) { this.sourcePage = sourcePage; }
    public String getExampleSentence() { return exampleSentence; }
    public void setExampleSentence(String exampleSentence) { this.exampleSentence = exampleSentence; }
}
