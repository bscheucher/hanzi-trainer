package com.learning.hanzitrainer.word;

import com.learning.hanzitrainer.cedict.CedictImporter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Small CC-CEDICT sample plus a textbook with two lessons, for confirmation and candidate tests. */
final class VocabularyFixture {

    static final String CEDICT = """
            學習 学习 [xue2 xi2] /to learn/to study/
            朋友 朋友 [peng2 you5] /friend/
            了 了 [le5] /(completed action marker)/
            了 了 [liao3] /to finish/to understand/
            瞭 了 [liao4] /(of eyesight) clear/
            不是 不是 [bu4 shi4] /no/is not/
            不是 不是 [bu4 shi5] /fault; blame/
            北京 北京 [Bei3 jing1] /Beijing/
            一點兒 一点儿 [yi1 dian3 r5] /a little/
            """;

    final long textbookId;
    final long lesson1;
    final long lesson2;

    private VocabularyFixture(long textbookId, long lesson1, long lesson2) {
        this.textbookId = textbookId;
        this.lesson1 = lesson1;
        this.lesson2 = lesson2;
    }

    static VocabularyFixture create(JdbcTemplate jdbc, CedictImporter importer, Path dir) {
        try {
            importer.importFile(Files.writeString(dir.resolve("cedict.u8"), CEDICT));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        long textbook = jdbc.queryForObject("INSERT INTO textbook (title) VALUES ('HSK 1') RETURNING id", Long.class);
        long l1 = jdbc.queryForObject("INSERT INTO lesson (textbook_id, lesson_no) VALUES (?, 1) RETURNING id",
                Long.class, textbook);
        long l2 = jdbc.queryForObject("INSERT INTO lesson (textbook_id, lesson_no) VALUES (?, 2) RETURNING id",
                Long.class, textbook);
        return new VocabularyFixture(textbook, l1, l2);
    }

    long page(JdbcTemplate jdbc, long lessonId) {
        return jdbc.queryForObject("""
                INSERT INTO page_image (lesson_id, storage_path, mime_type, extraction_status)
                VALUES (?, 'x.jpg', 'image/jpeg', 'DONE') RETURNING id""", Long.class, lessonId);
    }

    static void cleanUp(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM word");
        jdbc.update("DELETE FROM textbook");
        jdbc.update("DELETE FROM cedict_entry");
    }
}
