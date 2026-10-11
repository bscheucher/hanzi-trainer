package com.learning.hanzitrainer.exercise;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ToneSyllablesTests {

    @Test
    void asksEverySyllableWithItsCitationTone() {
        ToneSyllables s = ToneSyllables.of("peng2 you5");

        assertThat(s.syllables()).containsExactly("peng", "you");
        assertThat(s.tones()).containsExactly(2, 5);
    }

    @Test
    void skipsErhuaAndLatinLetters() {
        assertThat(ToneSyllables.of("yi1 dian3 r5").syllables()).containsExactly("yi", "dian");
        assertThat(ToneSyllables.of("A A zhi4").syllables()).containsExactly("zhi");
        assertThat(ToneSyllables.of("er2 zi5").tones()).containsExactly(2, 5); // 儿子: er2 is a real syllable
    }

    @Test
    void showsVAsUmlaut() {
        assertThat(ToneSyllables.of("lv4 se4").syllables()).isEqualTo(List.of("lü", "se"));
    }

    @Test
    void wordsWithoutTonedSyllablesAreEmpty() {
        assertThat(ToneSyllables.of("K T V").isEmpty()).isTrue();
    }
}
