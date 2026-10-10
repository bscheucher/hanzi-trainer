package com.learning.hanzitrainer.cedict;

import com.learning.hanzitrainer.TestcontainersConfiguration;
import com.learning.hanzitrainer.common.ServiceUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Imports a small CEDICT sample into PostgreSQL. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CedictImporterTests {

    private static final String SAMPLE = """
            # CC-CEDICT sample
            # License: CC BY-SA 4.0
            學習 学习 [xue2 xi2] /to learn/to study/
            了 了 [le5] /(completed action marker)/
            了 了 [liao3] /to finish/to understand/
            女 女 [nu:3] /female/woman/daughter/
            北京 北京 [Bei3 jing1] /Beijing/
            乹 干 [gan1] /old variant of 乾|干[gan1]/
            干 干 [gan1] /to concern oneself with/
            this line is broken
            卡拉OK 卡拉OK [ka3 la1 O K] /karaoke (loanword)/
            """;

    @Autowired
    private CedictImporter importer;

    @Autowired
    private CedictService cedictService;

    @Autowired
    private JdbcTemplate jdbc;

    @TempDir
    Path dir;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM word");
        jdbc.update("DELETE FROM cedict_entry");
    }

    @Test
    void importsAllParsableLines() throws IOException {
        var result = importer.importFile(sample());

        assertThat(result.rows()).isEqualTo(8);
        assertThat(cedictService.lookup("女")).singleElement().satisfies(e -> {
            assertThat(e.getPinyinNumbered()).isEqualTo("nv3");
            assertThat(e.getPinyin()).isEqualTo("nǚ");
            assertThat(e.getDefinitions()).containsExactly("female", "woman", "daughter");
        });
        assertThat(cedictService.lookup("北京").getFirst().getPinyin()).isEqualTo("Běijīng");
    }

    @Test
    void secondImportYieldsSameContentAndRelinksWords() throws IOException {
        Path file = sample();
        importer.importFile(file);
        List<Map<String, Object>> firstContent = content();
        long xuexiId = cedictService.lookup("学习").getFirst().getId();
        jdbc.update("""
                INSERT INTO word (hanzi, pinyin, pinyin_numbered, cedict_entry_id) VALUES
                    ('学习', 'xuéxí', 'xue2 xi2', ?),
                    ('干', 'gān', 'gan1', NULL),
                    ('龘', 'dá', 'da2', NULL)""", xuexiId);

        var second = importer.importFile(file);

        assertThat(second.rows()).isEqualTo(8);
        assertThat(content()).isEqualTo(firstContent);
        long newXuexiId = cedictService.lookup("学习").getFirst().getId();
        long lowestGanId = cedictService.lookup("干").getFirst().getId();
        assertThat(linkOf("学习")).isEqualTo(newXuexiId);
        assertThat(linkOf("干")).isEqualTo(lowestGanId);
        assertThat(linkOf("龘")).isNull();
        assertThat(cedictService.lookup("干").getFirst().getTraditional()).isEqualTo("乹");
    }

    @Test
    void missingFileIsServiceUnavailable() {
        assertThatThrownBy(() -> importer.importFile(dir.resolve("missing.u8")))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("missing.u8");
    }

    private Path sample() throws IOException {
        return Files.writeString(dir.resolve("cedict_ts.u8"), SAMPLE);
    }

    private List<Map<String, Object>> content() {
        return jdbc.queryForList("""
                SELECT simplified, traditional, pinyin_numbered, pinyin, array_to_string(definitions, '/') AS defs
                FROM cedict_entry ORDER BY simplified, traditional, pinyin_numbered, defs""");
    }

    private Long linkOf(String hanzi) {
        return jdbc.queryForObject("SELECT cedict_entry_id FROM word WHERE hanzi = ?", Long.class, hanzi);
    }
}
