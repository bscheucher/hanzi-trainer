package com.learning.hanzitrainer.cedict;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PinyinConverterTests {

    /** The test table from spec 04, both directions. Syllable count = tokens of the numbered form. */
    @ParameterizedTest(name = "{0} <-> {1}")
    @CsvSource(delimiter = '|', textBlock = """
            ni3 hao3     | nǐhǎo
            xue2 xi2     | xuéxí
            le5          | le
            nv3          | nǚ
            lv4 se4      | lǜsè
            gou3         | gǒu
            liu2         | liú
            gui4         | guì
            xi1 an1      | xī'ān
            peng2 you5   | péngyou
            er4          | èr
            zhuang1      | zhuāng
            xi1 nan2     | xīnán
            yi1 dian3 r5 | yīdiǎnr
            Bei3 jing1   | Běijīng
            A A zhi4     | AA zhì
            """)
    void specTable(String numbered, String marked) {
        assertThat(PinyinConverter.toToneMarks(numbered)).isEqualTo(marked);

        String expectedNumbered = numbered.equals("Bei3 jing1") ? "bei3 jing1" : numbered;
        assertThat(PinyinConverter.toNumbered(marked, numbered.split(" ").length)).isEqualTo(expectedNumbered);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', textBlock = """
            nu:3                | nǚ
            Lu:3                | Lǚ
            lve4                | lüè
            Ao4 men2            | Àomén
            Xi1 an1             | Xī'ān
            nv3 er2             | nǚ'ér
            wan2 r5 ming4       | wánrmìng
            ka3 la1 O K         | kǎlā OK
            T xu4               | T xù
            yi1 lu4 , shun4 feng1 | yīlù , shùnfēng
            xx5                 | xx
            m2                  | m
            """)
    void toToneMarksEdgeCases(String numbered, String marked) {
        assertThat(PinyinConverter.toToneMarks(numbered)).isEqualTo(marked);
    }

    @ParameterizedTest(name = "{0} ({1}) -> {2}")
    @CsvSource(delimiter = '|', textBlock = """
            Àomén        | 2 | ao4 men2
            lüè          | 1 | lve4
            nü           | 1 | nv5
            xī ān        | 2 | xi1 an1
            Xī’ān        | 2 | xi1 an1
            kǎlāOK       | 4 | ka3 la1 O K
            kǎlā OK      | 4 | ka3 la1 O K
            T xù         | 2 | T xu4
            wánrmìng     | 3 | wan2 r5 ming4
            pīnyīn       | 2 | pin1 yin1
            Zhōngguó     | 2 | zhong1 guo2
            shízài       | 2 | shi2 zai4
            tiān'ānmén   | 3 | tian1 an1 men2
            hng          | 1 | hng5
            """)
    void toNumberedEdgeCases(String marked, int count, String numbered) {
        assertThat(PinyinConverter.toNumbered(marked, count)).isEqualTo(numbered);
    }

    @Test
    void syllableCountDisambiguatesSegmentation() {
        assertThat(PinyinConverter.toNumbered("xiān", 1)).isEqualTo("xian1");
        assertThat(PinyinConverter.toNumbered("pīnyīn", 2)).isEqualTo("pin1 yin1");
        // n / ng / m are only accepted as a whole word, so pi+n+yin is not a valid split
        assertThatIllegalArgumentException().isThrownBy(() -> PinyinConverter.toNumbered("pīnyīn", 3));
    }

    @Test
    void vowelInitialSyllableNeedsApostrophe() {
        // Without an apostrophe, "fāngàn" can only be fan+gàn; 方案 is written fāng'àn.
        assertThat(PinyinConverter.toNumbered("fāngàn", 2)).isEqualTo("fan1 gan4");
        assertThat(PinyinConverter.toNumbered("fāng'àn", 2)).isEqualTo("fang1 an4");
    }

    @Test
    void erhuaIsNeverTheFirstSyllable() {
        assertThatIllegalArgumentException().isThrownBy(() -> PinyinConverter.toNumbered("r", 1));
    }

    @Test
    void rejectsWrongSyllableCount() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PinyinConverter.toNumbered("xuéxí", 3))
                .withMessageContaining("xuéxí");
        assertThatIllegalArgumentException().isThrownBy(() -> PinyinConverter.toNumbered("yīdiǎnr", 2));
    }

    @Test
    void rejectsGarbage() {
        assertThatIllegalArgumentException().isThrownBy(() -> PinyinConverter.toNumbered("qqq", 1));
        assertThatIllegalArgumentException().isThrownBy(() -> PinyinConverter.toNumbered("学习", 2));
        assertThatIllegalArgumentException().isThrownBy(() -> PinyinConverter.toNumbered("", 1));
        assertThatIllegalArgumentException().isThrownBy(() -> PinyinConverter.toNumbered("xué2", 1));
    }

    @Test
    void rejectsTwoMarksInOneSyllable() {
        assertThatIllegalArgumentException().isThrownBy(() -> PinyinConverter.toNumbered("xuéé", 1));
    }

    @Test
    void stripTones() {
        assertThat(PinyinConverter.stripTones("xue2 xi2")).isEqualTo("xue xi");
        assertThat(PinyinConverter.stripTones("nv3 er2")).isEqualTo("nv er");
        assertThat(PinyinConverter.stripTones("ka3 la1 O K")).isEqualTo("ka la O K");
    }

    @Test
    void normaliseNumberedForStorage() {
        assertThat(PinyinConverter.normaliseNumbered("Bei3 jing1")).isEqualTo("bei3 jing1");
        assertThat(PinyinConverter.normaliseNumbered("nu:3")).isEqualTo("nv3");
        assertThat(PinyinConverter.normaliseNumbered("ka3 la1 O K")).isEqualTo("ka3 la1 O K");
        assertThat(PinyinConverter.normaliseNumbered("t o n y lao3 shi1")).isEqualTo("t o n y lao3 shi1");
    }
}
