package com.learning.hanzitrainer.exercise;

import com.learning.hanzitrainer.TestcontainersConfiguration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** V2 fills {@code exercise.sentence} for exercises stored before it, and removes duplicates (spec 02). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SentenceMigrationTests {

    private static final String SCHEMA = "sentence_migration";

    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void dropSchema() {
        jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    @Test
    void fillsTheSentenceAsTheJobComputesItAndKeepsTheOldestDuplicate() {
        migrate("1");
        long textbook = jdbc.queryForObject("INSERT INTO " + SCHEMA + ".textbook (title) VALUES ('t') RETURNING id",
                Long.class);
        long lesson = jdbc.queryForObject("INSERT INTO " + SCHEMA + ".lesson (textbook_id, lesson_no) VALUES (?, 1) "
                + "RETURNING id", Long.class, textbook);
        long fillBlank = insert(lesson, "FILL_BLANK", "{\"correct\": \"朋友\", \"fullSentence\": \"我的朋友。\"}");
        long duplicate = insert(lesson, "FILL_BLANK", "{\"correct\": \"朋友\", \"fullSentence\": \"我的朋友。\"}");
        long wordOrder = insert(lesson, "WORD_ORDER",
                "{\"accepted\": [[\"明天\", \"我\", \"去\", \"学校\"], [\"我\", \"明天\", \"去\", \"学校\"]]}");
        long translation = insert(lesson, "TRANSLATION", "{\"reference\": \"我去学校。\"}");
        long tone = insert(lesson, "TONE", "{\"tones\": [3]}");

        migrate("2");

        Map<Long, String> sentences = new HashMap<>();
        jdbc.query("SELECT id, sentence FROM " + SCHEMA + ".exercise",
                rs -> { sentences.put(rs.getLong("id"), rs.getString("sentence")); });
        assertThat(sentences).containsOnlyKeys(fillBlank, wordOrder, translation, tone); // the newer duplicate is gone
        assertThat(sentences.get(fillBlank)).isEqualTo("我的朋友。");
        assertThat(sentences.get(wordOrder)).isEqualTo("明天我去学校"); // the first accepted order, as the job joins it
        assertThat(sentences.get(translation)).isEqualTo("我去学校。");
        assertThat(sentences.get(tone)).isNull();
        assertThat(duplicate).isNotIn(sentences.keySet());

        assertThatThrownBy(() -> jdbc.update("INSERT INTO " + SCHEMA + ".exercise (lesson_id, type, prompt, answer, "
                + "sentence, generated_by) VALUES (?, 'TRANSLATION', '{}', '{}', '我去学校。', 'm')", lesson))
                .isInstanceOf(DataIntegrityViolationException.class); // unique per lesson and type
        assertThatThrownBy(() -> jdbc.update("INSERT INTO " + SCHEMA + ".exercise (lesson_id, type, prompt, answer, "
                + "generated_by) VALUES (?, 'TRANSLATION', '{}', '{}', 'm')", lesson))
                .isInstanceOf(DataIntegrityViolationException.class); // a sentence type needs its sentence
    }

    private long insert(long lesson, String type, String answer) {
        return jdbc.queryForObject("INSERT INTO " + SCHEMA + ".exercise (lesson_id, type, prompt, answer, generated_by) "
                + "VALUES (?, ?, '{}', ?::jsonb, 'm') RETURNING id", Long.class, lesson, type, answer);
    }

    private void migrate(String target) {
        Flyway.configure().dataSource(dataSource).schemas(SCHEMA).locations("classpath:db/migration").target(target)
                .load().migrate();
    }
}
