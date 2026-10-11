package com.learning.hanzitrainer.word;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WordSearchTests {

    @Test
    void blankQueryHasNoFilter() {
        assertThat(WordSearch.of(null)).isEqualTo(WordSearch.NONE);
        assertThat(WordSearch.of("  ")).isEqualTo(WordSearch.NONE);
    }

    @Test
    void hanziAndPunctuatedTextAreNotPinyin() {
        assertThat(WordSearch.of("学习")).isEqualTo(new WordSearch("%学习%", "NONE", null));
        assertThat(WordSearch.of("Chinese (language)")).isEqualTo(new WordSearch("%chinese (language)%", "NONE", null));
    }

    @Test
    void plainWordsAreAlsoTriedAsPinyin() {
        // a meaning made of letters cannot be told apart from pinyin; it is matched both ways
        assertThat(WordSearch.of("to study")).isEqualTo(new WordSearch("%to study%", "TONELESS", "%tostudy%"));
    }

    @Test
    void lettersOnlyIsTonelessPinyinWithoutSpaces() {
        assertThat(WordSearch.of(" Xue Xi ")).isEqualTo(new WordSearch("%xue xi%", "TONELESS", "%xuexi%"));
        assertThat(WordSearch.of("xi'an")).isEqualTo(new WordSearch("%xi'an%", "TONELESS", "%xian%"));
    }

    @Test
    void toneDigitsSearchNumberedPinyin() {
        assertThat(WordSearch.of("xue2 xi2")).isEqualTo(new WordSearch("%xue2 xi2%", "NUMBERED", "%xue2xi2%"));
    }

    @Test
    void toneMarksSearchDisplayPinyin() {
        assertThat(WordSearch.of("Běijīng")).isEqualTo(new WordSearch("%běijīng%", "MARKED", "%běijīng%"));
    }

    @Test
    void decomposedToneMarksAreNormalised() {
        String decomposed = "xuéxí"; // e + combining acute
        assertThat(WordSearch.of(decomposed).pinyin()).isEqualTo("%xuéxí%");
    }

    @Test
    void umlautUIsVInNumberedAndTonelessPinyin() {
        assertThat(WordSearch.of("lü4").pinyin()).isEqualTo("%lv4%");
        assertThat(WordSearch.of("nu:3").pinyin()).isEqualTo("%nv3%");
        assertThat(WordSearch.of("lü").pinyin()).isEqualTo("%lv%");
        assertThat(WordSearch.of("lǜ")).isEqualTo(new WordSearch("%lǜ%", "MARKED", "%lǜ%"));
    }

    @Test
    void likeWildcardsAreEscaped() {
        assertThat(WordSearch.of("50%_a\\b").text()).isEqualTo("%50\\%\\_a\\\\b%");
    }
}
