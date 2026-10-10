package com.learning.hanzitrainer.textbook;

import com.learning.hanzitrainer.TestcontainersConfiguration;
import com.learning.hanzitrainer.common.ConflictException;
import com.learning.hanzitrainer.common.NotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TextbookServiceTests {

    @Autowired
    private TextbookService service;
    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM textbook");
        jdbc.update("DELETE FROM word");
    }

    @Test
    void createsAndListsTextbooksWithLessonCounts() {
        TextbookDto hsk1 = service.createTextbook(new CreateTextbookRequest(" HSK Standard Course 1 ", " "));
        TextbookDto hsk2 = service.createTextbook(new CreateTextbookRequest("HSK Standard Course 2", "2nd"));
        service.createLesson(hsk1.id(), new CreateLessonRequest(1, "你好"));
        service.createLesson(hsk1.id(), new CreateLessonRequest(2, null));

        assertThat(service.listTextbooks()).containsExactly(
                new TextbookDto(hsk1.id(), "HSK Standard Course 1", null, 2),
                new TextbookDto(hsk2.id(), "HSK Standard Course 2", "2nd", 0));
    }

    @Test
    void lessonsAreOrderedByNumber() {
        long textbookId = service.createTextbook(new CreateTextbookRequest("HSK 1", null)).id();
        service.createLesson(textbookId, new CreateLessonRequest(3, "三"));
        service.createLesson(textbookId, new CreateLessonRequest(1, "一"));

        assertThat(service.listLessons(textbookId)).extracting(LessonSummaryDto::lessonNo).containsExactly(1, 3);
    }

    @Test
    void duplicateLessonNumberIsConflict() {
        long textbookId = service.createTextbook(new CreateTextbookRequest("HSK 1", null)).id();
        service.createLesson(textbookId, new CreateLessonRequest(1, null));

        assertThatThrownBy(() -> service.createLesson(textbookId, new CreateLessonRequest(1, "again")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void unknownIdsAreNotFound() {
        assertThatThrownBy(() -> service.listLessons(999_999)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.getLesson(999_999)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.deleteLesson(999_999)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void lessonDetailCountsPagesWordsAndPendingReviews() {
        long textbookId = service.createTextbook(new CreateTextbookRequest("HSK 1", null)).id();
        long lessonId = service.createLesson(textbookId, new CreateLessonRequest(1, "你好")).id();
        jdbc.update("""
                INSERT INTO page_image (lesson_id, page_no, storage_path, mime_type, extraction_status, confirmed_at)
                VALUES (?, 2, 'a.jpg', 'image/jpeg', 'DONE', NULL),
                       (?, 1, 'b.jpg', 'image/jpeg', 'DONE', now()),
                       (?, NULL, 'c.jpg', 'image/jpeg', 'FAILED', NULL)""", lessonId, lessonId, lessonId);
        long wordId = jdbc.queryForObject("""
                INSERT INTO word (hanzi, pinyin, pinyin_numbered, meaning_en) VALUES ('你好', 'nǐhǎo', 'ni3 hao3', 'hello')
                RETURNING id""", Long.class);
        jdbc.update("INSERT INTO lesson_word (lesson_id, word_id) VALUES (?, ?)", lessonId, wordId);

        LessonDetailDto detail = service.getLesson(lessonId);

        assertThat(detail.lessonNo()).isEqualTo(1);
        assertThat(detail.pageCount()).isEqualTo(3);
        assertThat(detail.wordCount()).isEqualTo(1);
        assertThat(detail.pendingReviewPages()).isEqualTo(1);
        assertThat(detail.pages()).extracting(p -> p.pageNo()).containsExactly(1, 2, null);
        assertThat(detail.pages().getFirst().confirmed()).isTrue();
        assertThat(detail.words()).singleElement().satisfies(w -> {
            assertThat(w.hanzi()).isEqualTo("你好");
            assertThat(w.pinyinNumbered()).isEqualTo("ni3 hao3");
        });
    }

    @Test
    void deletingLessonKeepsWords() {
        long textbookId = service.createTextbook(new CreateTextbookRequest("HSK 1", null)).id();
        long lessonId = service.createLesson(textbookId, new CreateLessonRequest(1, null)).id();
        long wordId = jdbc.queryForObject("""
                INSERT INTO word (hanzi, pinyin, pinyin_numbered) VALUES ('好', 'hǎo', 'hao3') RETURNING id""",
                Long.class);
        jdbc.update("INSERT INTO lesson_word (lesson_id, word_id) VALUES (?, ?)", lessonId, wordId);

        service.deleteLesson(lessonId);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM lesson", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM lesson_word", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM word", Integer.class)).isEqualTo(1);
    }
}
