package com.learning.hanzitrainer.exercise;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AllowedWordsTests {

    private final AllowedWords allowed = new AllowedWords(List.of("朋友", "学校", "去"));

    @Test
    void charactersOfVocabularyAndBasicWordsAreAllowedAndPunctuationIgnored() {
        assertThat(allowed.disallowedCharacters("我和我的朋友去学校。")).isEmpty();
        assertThat(allowed.disallowedCharacters("“你好！”, AA 3")).containsExactly("好");
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
