package com.learning.hanzitrainer.word;

import com.learning.hanzitrainer.TestcontainersConfiguration;
import com.learning.hanzitrainer.cedict.CedictImporter;
import com.learning.hanzitrainer.cedict.CedictMatch.Status;
import com.learning.hanzitrainer.cedict.CedictReadingDto;
import com.learning.hanzitrainer.extraction.ExtractionResult;
import com.learning.hanzitrainer.extraction.ExtractionResult.ExtractedWord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CandidateServiceTests {

    @Autowired
    private CandidateService candidates;
    @Autowired
    private ConfirmationService confirmation;
    @Autowired
    private CedictImporter importer;
    @Autowired
    private JdbcTemplate jdbc;

    @TempDir
    Path dir;

    private VocabularyFixture fx;

    @BeforeEach
    void setUp() {
        fx = VocabularyFixture.create(jdbc, importer, dir);
    }

    @AfterEach
    void cleanUp() {
        VocabularyFixture.cleanUp(jdbc);
    }

    @Test
    void enrichesEveryExtractedWord() {
        List<CandidateDto> result = candidates.candidates(fx.lesson1, extraction(
                word("朋友", "péngyou", 0.98),
                word("不是", "bú shì", 0.9),
                word("了", "lè", 0.5),
                word("龘", "dá", 0.4),
                word("学习", "xue", 0.9)));

        assertThat(result).extracting(CandidateDto::index).containsExactly(0, 1, 2, 3, 4);
        assertThat(result).extracting(CandidateDto::cedictStatus).containsExactly(
                Status.MATCH, Status.MATCH, Status.PINYIN_MISMATCH, Status.NOT_FOUND, Status.PINYIN_MISMATCH);
        assertThat(result.get(1).pinyin()).as("sandhi resolved to dictionary tones").isEqualTo("bùshì");
        assertThat(result.get(2).cedictReadings()).extracting(CedictReadingDto::pinyin)
                .containsExactly("le", "liǎo", "liào");
        assertThat(result.get(4).pinyinNumbered()).isNull();
        assertThat(result.get(4).pinyinError()).contains("2 syllable");
        assertThat(result).allSatisfy(c -> {
            assertThat(c.existingWordId()).isNull();
            assertThat(c.alreadyInLesson()).isFalse();
        });
    }

    @Test
    void marksKnownWordsAndWordsAlreadyInTheLesson() {
        confirmation.confirm(fx.lesson1, new ConfirmRequest(null,
                List.of(new ConfirmItem("朋友", "péngyou", "friend", null, null, null, false))));

        CandidateDto inLesson = candidates.candidates(fx.lesson1, extraction(word("朋友", "péngyou", 1))).getFirst();
        CandidateDto otherLesson = candidates.candidates(fx.lesson2, extraction(word("朋友", "péngyou", 1))).getFirst();

        assertThat(inLesson.existingWordId()).isNotNull();
        assertThat(inLesson.alreadyInLesson()).isTrue();
        assertThat(otherLesson.existingWordId()).isEqualTo(inLesson.existingWordId());
        assertThat(otherLesson.alreadyInLesson()).isFalse();
    }

    private static ExtractionResult extraction(ExtractedWord... words) {
        return new ExtractionResult("VOCABULARY_LIST", List.of(words), List.of(), List.of(), List.of());
    }

    private static ExtractedWord word(String hanzi, String pinyin, double confidence) {
        return new ExtractedWord(hanzi, pinyin, true, "meaning", null, null, null, confidence);
    }
}
