package com.learning.hanzitrainer.word;

import com.learning.hanzitrainer.TestcontainersConfiguration;
import com.learning.hanzitrainer.cedict.CedictImporter;
import com.learning.hanzitrainer.common.InvalidItemsException;
import com.learning.hanzitrainer.common.InvalidItemsException.ItemError;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Spec 06, "Confirmation" and "Tests", against PostgreSQL. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ConfirmationServiceTests {

    @Autowired
    private ConfirmationService service;
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
    void newWordGetsThreeCardsAndALessonLink() {
        long page = fx.page(jdbc, fx.lesson1);

        ConfirmResult result = service.confirm(fx.lesson1, new ConfirmRequest(page,
                List.of(item("学习", "xuéxí", "to study", false).withExample("我们学习中文。"))));

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.linked()).isEqualTo(1);
        assertThat(result.words()).singleElement().satisfies(w -> {
            assertThat(w.pinyin()).isEqualTo("xuéxí");
            assertThat(w.pinyinNumbered()).isEqualTo("xue2 xi2");
            assertThat(w.verified()).isTrue();
        });
        assertThat(cardTypes()).containsExactlyInAnyOrder("RECOGNITION", "WRITING", "TONE");
        assertThat(jdbc.queryForMap("SELECT source_page_id, example_sentence FROM lesson_word"))
                .containsEntry("source_page_id", page).containsEntry("example_sentence", "我们学习中文。");
        assertThat(jdbc.queryForObject("SELECT cedict_entry_id IS NOT NULL FROM word", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT confirmed_at IS NOT NULL FROM page_image WHERE id = ?",
                Boolean.class, page)).isTrue();
    }

    @Test
    void sameWordFromTwoPagesOfOneLessonIsOneWordAndOneLink() {
        service.confirm(fx.lesson1, request(fx.page(jdbc, fx.lesson1), item("朋友", "péngyou", "friend", false)));
        ConfirmResult second = service.confirm(fx.lesson1,
                request(fx.page(jdbc, fx.lesson1), item("朋友", "péngyou", "friend", false)));

        assertThat(second.created()).isZero();
        assertThat(second.reused()).isEqualTo(1);
        assertThat(second.linked()).isZero();
        assertThat(count("word")).isEqualTo(1);
        assertThat(count("lesson_word")).isEqualTo(1);
    }

    @Test
    void sameWordInTwoLessonsIsOneWordTwoLinksStillThreeCards() {
        service.confirm(fx.lesson1, request(null, item("朋友", "péngyou", "friend", false)));
        ConfirmResult second = service.confirm(fx.lesson2, request(null, item("朋友", "péngyou", "friend", false)));

        assertThat(second.linked()).isEqualTo(1);
        assertThat(count("word")).isEqualTo(1);
        assertThat(count("lesson_word")).isEqualTo(2);
        assertThat(count("review_card")).isEqualTo(3);
    }

    @Test
    void confirmingTwiceCreatesNoDuplicates() {
        ConfirmRequest request = request(fx.page(jdbc, fx.lesson1), item("学习", "xuéxí", "to study", false),
                item("朋友", "péngyou", "friend", false));

        service.confirm(fx.lesson1, request);
        ConfirmResult again = service.confirm(fx.lesson1, request);

        assertThat(again.created()).isZero();
        assertThat(again.reused()).isEqualTo(2);
        assertThat(count("word")).isEqualTo(2);
        assertThat(count("lesson_word")).isEqualTo(2);
        assertThat(count("review_card")).isEqualTo(6);
    }

    @Test
    void polyphonesAreSeparateWords() {
        service.confirm(fx.lesson1, request(null, item("了", "le", "particle", false),
                item("了", "liǎo", "to finish", false)));

        assertThat(jdbc.queryForList("SELECT pinyin_numbered FROM word ORDER BY pinyin_numbered", String.class))
                .containsExactly("le5", "liao3");
    }

    @Test
    void toneSandhiFromThePageIsStoredWithDictionaryTones() {
        ConfirmResult result = service.confirm(fx.lesson1, request(null, item("不是", "bú shì", "is not", false)));

        assertThat(result.words()).singleElement().satisfies(w -> {
            assertThat(w.pinyinNumbered()).isEqualTo("bu4 shi4");
            assertThat(w.pinyin()).isEqualTo("bùshì");
            assertThat(w.verified()).isTrue();
        });
    }

    @Test
    void mismatchNeedsExplicitAcceptance() {
        assertThatThrownBy(() -> service.confirm(fx.lesson1, request(null,
                item("学习", "xuéxí", "to study", false), item("了", "lè", "joyful?", false))))
                .isInstanceOf(InvalidItemsException.class)
                .extracting(e -> ((InvalidItemsException) e).errors())
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.list(ItemError.class))
                .singleElement().satisfies(e -> {
                    assertThat(e.index()).isEqualTo(1);
                    assertThat(e.message()).contains("differs from CC-CEDICT").contains("liǎo");
                });
        assertThat(count("word")).as("nothing stored when any item is invalid").isZero();

        ConfirmResult accepted = service.confirm(fx.lesson1, request(null, item("了", "lè", "joyful?", true)));
        assertThat(accepted.words().getFirst().verified()).isTrue();
        assertThat(jdbc.queryForObject("SELECT cedict_entry_id FROM word", Long.class)).isNull();
    }

    @Test
    void wordsNotInTheDictionaryAreUnverified() {
        ConfirmResult result = service.confirm(fx.lesson1, request(null, item("龘", "dá", "dragons flying", false)));

        assertThat(result.words().getFirst().verified()).isFalse();
    }

    @Test
    void invalidItemsAreReportedByIndex() {
        assertThatThrownBy(() -> service.confirm(fx.lesson1, request(null,
                item("", "a", "x", false),
                item("hello", "hello", "x", false),
                item("学习", "xuéxí xíxí", "to study", false),
                item("朋友", "péngyou", " ", false),
                null,
                item("朋友", "péngyou", "friend", false))))
                .isInstanceOf(InvalidItemsException.class)
                .satisfies(e -> assertThat(((InvalidItemsException) e).errors())
                        .extracting(ItemError::index).containsExactly(0, 1, 2, 3, 4));
        assertThat(count("word")).isZero();
    }

    @Test
    void reuseFillsOnlyEmptyFields() {
        service.confirm(fx.lesson1, request(null, new ConfirmItem("朋友", "péngyou", "friend", null, null, null, false)));
        service.confirm(fx.lesson2, request(null,
                new ConfirmItem("朋友", "péngyou", "buddy", "Freund", "n.", null, false)));

        Map<String, Object> word = jdbc.queryForMap("SELECT meaning_en, meaning_de, part_of_speech FROM word");
        assertThat(word).containsEntry("meaning_en", "friend").containsEntry("meaning_de", "Freund")
                .containsEntry("part_of_speech", "n.");
    }

    @Test
    void properNounKeepsItsCapitalAndErhuaCountsAsSyllable() {
        ConfirmResult result = service.confirm(fx.lesson1, request(null, item("北京", "běijīng", "Beijing", false),
                item("一点儿", "yìdiǎnr", "a little", false)));

        assertThat(result.words()).extracting(WordDto::pinyin).containsExactly("Běijīng", "yīdiǎnr");
        assertThat(result.words()).extracting(WordDto::pinyinNumbered).containsExactly("bei3 jing1", "yi1 dian3 r5");
    }

    @Test
    void lowercaseInputLinksTheCommonEntryNotTheSurname() {
        ConfirmResult result = service.confirm(fx.lesson1, request(null, item("王", "wáng", "king", false)));

        assertThat(result.words().getFirst().pinyin()).isEqualTo("wáng");
        assertThat(jdbc.queryForObject("SELECT e.pinyin_numbered FROM word w JOIN cedict_entry e "
                + "ON e.id = w.cedict_entry_id", String.class)).isEqualTo("wang2");
    }

    @Test
    void pageOfAnotherLessonIsRejected() {
        long foreignPage = fx.page(jdbc, fx.lesson2);

        assertThatThrownBy(() -> service.confirm(fx.lesson1, request(foreignPage, item("朋友", "péngyou", "friend", false))))
                .isInstanceOf(InvalidItemsException.class)
                .hasMessageContaining("another lesson");
    }

    private ConfirmRequest request(Long pageId, ConfirmItem... items) {
        return new ConfirmRequest(pageId, Arrays.asList(items));
    }

    private static ConfirmItem item(String hanzi, String pinyin, String meaningEn, boolean acceptMismatch) {
        return new ConfirmItem(hanzi, pinyin, meaningEn, null, null, null, acceptMismatch);
    }

    private List<String> cardTypes() {
        return jdbc.queryForList("SELECT card_type FROM review_card", String.class);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }
}
