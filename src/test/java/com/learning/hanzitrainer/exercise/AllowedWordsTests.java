package com.learning.hanzitrainer.exercise;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AllowedWordsTests {

    private final AllowedWords allowed = new AllowedWords(List.of("朋友", "学校", "去"));

    @Test
    void charactersOfVocabularyAndBasicWordsAreAllowedAndPunctuationIgnored() {
        assertThat(allowed.disallowedCharacters("我和我的朋友去学校。")).isEmpty();
        assertThat(allowed.disallowedCharacters("“你好！”, 我　去")).containsExactly("好"); // U+3000 is a space
    }

    @Test
    void lettersAndDigitsOnlyPassWhenAVocabularyWordHasThem() {
        assertThat(allowed.disallowedCharacters("我有3个朋友, OK")).containsExactly("3", "K", "O");
        assertThat(allowed.disallowedCharacters("我有３个朋友")).containsExactly("３");
        assertThat(new AllowedWords(List.of("卡拉OK")).disallowedCharacters("卡拉OK。")).isEmpty();
    }

    @Test
    void reportsEachUnknownCharacterOnceSorted() {
        assertThat(allowed.disallowedCharacters("我们明天去学校看朋友，明天见。")).containsExactly("天", "明", "看", "见");
    }

    @Test
    void wordsIncludeTheWhitelist() {
        assertThat(allowed.words()).contains("朋友", "的", "两", "百").hasSize(3 + AllowedWords.BASIC.size());
    }
}
