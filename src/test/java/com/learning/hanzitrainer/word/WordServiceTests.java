package com.learning.hanzitrainer.word;

import com.learning.hanzitrainer.TestcontainersConfiguration;
import com.learning.hanzitrainer.cedict.CedictImporter;
import com.learning.hanzitrainer.common.NotFoundException;
import com.learning.hanzitrainer.review.CardType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** M4 vocabulary browser against PostgreSQL (the search query is native SQL). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class WordServiceTests {

    private static final PageRequest FIRST_PAGE = PageRequest.of(0, 50);

    @Autowired
    private WordService service;
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
        // created one after another, so 北京 is the newest
        confirm(fx.lesson1, new ConfirmItem("学习", "xuéxí", "to study", "lernen", "v.", "我们学习中文。", false));
        confirm(fx.lesson1, new ConfirmItem("了", "le", "completed action marker", null, null, null, false));
        confirm(fx.lesson2, new ConfirmItem("了", "liǎo", "to finish", null, null, null, false));
        confirm(fx.lesson2, new ConfirmItem("北京", "běijīng", "Beijing", "Peking", null, null, false));
    }

    @AfterEach
    void cleanUp() {
        VocabularyFixture.cleanUp(jdbc);
    }

    @Test
    void listsAllWordsNewestFirst() {
        assertThat(hanzi(null, null)).containsExactly("北京", "了", "了", "学习");
    }

    @Test
    void pagesThroughTheResults() {
        Page<WordDto> second = service.search(null, null, PageRequest.of(1, 3));

        assertThat(second.getTotalElements()).isEqualTo(4);
        assertThat(second.getTotalPages()).isEqualTo(2);
        assertThat(second.getContent()).extracting(WordDto::hanzi).containsExactly("学习");
    }

    @Test
    void filtersByLesson() {
        assertThat(pinyin(fx.lesson1, null)).containsExactly("le", "xuéxí");
        assertThat(pinyin(fx.lesson2, null)).containsExactly("Běijīng", "liǎo");
    }

    @Test
    void searchesHanziAndMeaningsCaseInsensitively() {
        assertThat(hanzi(null, "京")).containsExactly("北京");
        assertThat(hanzi(null, "STUDY")).containsExactly("学习");
        assertThat(hanzi(null, "peking")).containsExactly("北京");
    }

    @Test
    void pinyinWithoutTonesIgnoresTones() {
        assertThat(pinyin(null, "xuexi")).containsExactly("xuéxí");
        assertThat(pinyin(null, "Bei Jing")).containsExactly("Běijīng");
        assertThat(pinyin(null, "liao")).containsExactly("liǎo");
    }

    @Test
    void pinyinWithTonesMatchesOnlyThatReading() {
        assertThat(pinyin(null, "xue2xi2")).containsExactly("xuéxí");
        assertThat(pinyin(null, "xue2 xi2")).containsExactly("xuéxí");
        assertThat(pinyin(null, "xue3xi2")).isEmpty();
        assertThat(pinyin(null, "xuéxí")).containsExactly("xuéxí");
        assertThat(pinyin(null, "xuěxí")).isEmpty();
        assertThat(pinyin(null, "běijīng")).containsExactly("Běijīng");
        assertThat(pinyin(null, "liǎo")).containsExactly("liǎo");
        assertThat(pinyin(null, "le5")).containsExactly("le");
    }

    @Test
    void searchCombinesWithTheLessonFilter() {
        assertThat(pinyin(fx.lesson1, "liao")).isEmpty();
        assertThat(pinyin(fx.lesson2, "liao")).containsExactly("liǎo");
    }

    @Test
    void likeWildcardsInTheQueryAreLiteral() {
        assertThat(hanzi(null, "%")).isEmpty();
        assertThat(hanzi(null, "_")).isEmpty();
    }

    @Test
    void detailShowsLessonsWithExampleSentencesAndCards() {
        long xuexi = id("学习");
        confirm(fx.lesson2, new ConfirmItem("学习", "xuéxí", "to study", null, null, null, false));

        WordDetailDto detail = service.detail(xuexi);

        assertThat(detail.hanzi()).isEqualTo("学习");
        assertThat(detail.meaningDe()).isEqualTo("lernen");
        assertThat(detail.lessons()).extracting(WordDetailDto.LessonRef::lessonNo).containsExactly(1, 2);
        assertThat(detail.lessons().getFirst().textbookTitle()).isEqualTo("HSK 1");
        assertThat(detail.lessons()).extracting(WordDetailDto.LessonRef::exampleSentence)
                .containsExactly("我们学习中文。", null);
        assertThat(detail.cards()).extracting(WordDetailDto.CardRef::cardType)
                .containsExactly(CardType.RECOGNITION, CardType.WRITING, CardType.TONE);
    }

    @Test
    void updateChangesOnlyGivenFieldsAndClearsBlankOptionalOnes() {
        long xuexi = id("学习");

        WordDto updated = service.update(xuexi, new UpdateWordRequest(" to learn ", "", null));

        assertThat(updated.meaningEn()).isEqualTo("to learn");
        assertThat(updated.meaningDe()).isNull();
        assertThat(updated.partOfSpeech()).isEqualTo("v.");
        assertThat(service.detail(xuexi).meaningEn()).isEqualTo("to learn");
    }

    @Test
    void unlinkKeepsTheWordItsCardsAndOtherLessons() {
        long xuexi = id("学习");
        confirm(fx.lesson2, new ConfirmItem("学习", "xuéxí", "to study", null, null, null, false));

        service.unlink(fx.lesson1, xuexi);

        assertThat(service.detail(xuexi).lessons()).extracting(WordDetailDto.LessonRef::lessonNo).containsExactly(2);
        assertThat(count("review_card WHERE word_id = " + xuexi)).isEqualTo(3);
        assertThat(pinyin(fx.lesson1, null)).containsExactly("le");
    }

    @Test
    void unlinkingAWordThatIsNotInTheLessonIs404() {
        assertThatThrownBy(() -> service.unlink(fx.lesson1, id("北京")))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void deleteRemovesTheWordWithCardsLogsAndLinks() {
        long xuexi = id("学习");
        long card = jdbc.queryForObject("SELECT min(id) FROM review_card WHERE word_id = ?", Long.class, xuexi);
        jdbc.update("""
                INSERT INTO review_log (card_id, rating, state_before, elapsed_days, scheduled_days)
                VALUES (?, 3, 'NEW', 0, 1)""", card);

        service.delete(xuexi);

        assertThat(count("word WHERE id = " + xuexi)).isZero();
        assertThat(count("review_card WHERE word_id = " + xuexi)).isZero();
        assertThat(count("review_log")).isZero();
        assertThat(count("lesson_word WHERE word_id = " + xuexi)).isZero();
        assertThat(hanzi(null, null)).containsExactly("北京", "了", "了");
    }

    @Test
    void unknownWordIs404() {
        assertThatThrownBy(() -> service.detail(-1)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.delete(-1)).isInstanceOf(NotFoundException.class);
    }

    private void confirm(long lessonId, ConfirmItem item) {
        confirmation.confirm(lessonId, new ConfirmRequest(null, List.of(item)));
    }

    private List<String> hanzi(Long lessonId, String q) {
        return service.search(lessonId, q, FIRST_PAGE).getContent().stream().map(WordDto::hanzi).toList();
    }

    private List<String> pinyin(Long lessonId, String q) {
        return service.search(lessonId, q, FIRST_PAGE).getContent().stream().map(WordDto::pinyin).toList();
    }

    private long id(String hanzi) {
        return jdbc.queryForObject("SELECT id FROM word WHERE hanzi = ?", Long.class, hanzi);
    }

    private int count(String fromWhere) {
        return jdbc.queryForObject("SELECT count(*) FROM " + fromWhere, Integer.class);
    }
}
