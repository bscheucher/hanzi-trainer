package com.learning.hanzitrainer.extraction;

import com.learning.hanzitrainer.extraction.ExtractionResult.ExtractedSentence;
import com.learning.hanzitrainer.extraction.ExtractionResult.ExtractedWord;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractionResultValidatorTests {

    @Test
    void keepsValidWordsAndTrimsFields() {
        ExtractionResult result = validate(word(" 朋友 ", " péngyou ", " friend ", "  ", 0.9));

        assertThat(result.words()).singleElement().satisfies(w -> {
            assertThat(w.hanzi()).isEqualTo("朋友");
            assertThat(w.pinyin()).isEqualTo("péngyou");
            assertThat(w.meaningEn()).isEqualTo("friend");
            assertThat(w.meaningDe()).isNull();
        });
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void dropsNonChineseItemsWithWarning() {
        ExtractionResult result = validate(
                word("", "a", "x", null, 1),
                word("hello", "hello", "x", null, 1),
                word("第1课", "dì yī kè", "lesson 1", null, 1),
                word("朋友。", "péngyou", "friend", null, 1));

        assertThat(result.words()).isEmpty();
        assertThat(result.warnings()).hasSize(4)
                .anySatisfy(w -> assertThat(w).contains("hello").contains("no Chinese characters"))
                .anySatisfy(w -> assertThat(w).contains("第1课").contains("'1'"));
    }

    @Test
    void keepsMixedLatinWords() {
        ExtractionResult result = validate(word("T恤", "T xù", "T-shirt", null, 0.9),
                word("卡拉OK", "kǎlā OK", "karaoke", null, 0.9));

        assertThat(result.words()).extracting(ExtractedWord::hanzi).containsExactly("T恤", "卡拉OK");
    }

    @Test
    void deduplicatesOnHanziAndNumberedPinyin() {
        ExtractionResult result = validate(
                word("朋友", "péngyou", "friend", null, 0.9),
                word("朋友", "péng you", "friend (again)", null, 0.8),
                word("了", "le", "particle", null, 0.9),
                word("了", "liǎo", "to finish", null, 0.9));

        assertThat(result.words()).extracting(ExtractedWord::meaningEn)
                .containsExactly("friend", "particle", "to finish");
    }

    @Test
    void unparsablePinyinIsKeptForReview() {
        ExtractionResult result = validate(word("朋友", "pengyuo??", "friend", null, 0.4));

        assertThat(result.words()).hasSize(1);
    }

    @Test
    void clampsConfidenceAndFillsMissingLists() {
        ExtractionResult raw = new ExtractionResult(null,
                List.of(word("学习", "xuéxí", "to study", null, 1.7)), null, null, null);

        ExtractionResult result = ExtractionResultValidator.validate(raw);

        assertThat(result.pageType()).isEqualTo("OTHER");
        assertThat(result.words().getFirst().confidence()).isEqualTo(1.0);
        assertThat(result.sentences()).isEmpty();
        assertThat(result.grammarPoints()).isEmpty();
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void dropsEmptySentencesAndNullEntries() {
        ExtractionResult raw = new ExtractionResult("TEXT", Arrays.asList(null, word("好", "hǎo", "good", null, 1)),
                Arrays.asList(new ExtractedSentence(" ", null, null), new ExtractedSentence("你好！", "Nǐ hǎo!", "Hi!")),
                List.of(" ", "是 sentences"), List.of("cut off "));

        ExtractionResult result = ExtractionResultValidator.validate(raw);

        assertThat(result.words()).hasSize(1);
        assertThat(result.sentences()).extracting(ExtractedSentence::hanzi).containsExactly("你好！");
        assertThat(result.grammarPoints()).containsExactly("是 sentences");
        assertThat(result.warnings()).containsExactly("cut off");
    }

    private static ExtractionResult validate(ExtractedWord... words) {
        return ExtractionResultValidator.validate(
                new ExtractionResult("VOCABULARY_LIST", List.of(words), List.of(), List.of(), List.of()));
    }

    private static ExtractedWord word(String hanzi, String pinyin, String meaningEn, String meaningDe,
                                      double confidence) {
        return new ExtractedWord(hanzi, pinyin, true, meaningEn, meaningDe, "n.", null, confidence);
    }
}
