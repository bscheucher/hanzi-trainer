package com.learning.hanzitrainer.cedict;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CedictParserTests {

    @Test
    void parsesAndNormalises() {
        var e = CedictParser.parse("學習 学习 [xue2 xi2] /to learn/to study/").orElseThrow();

        assertThat(e.traditional()).isEqualTo("學習");
        assertThat(e.simplified()).isEqualTo("学习");
        assertThat(e.pinyinNumbered()).isEqualTo("xue2 xi2");
        assertThat(e.pinyin()).isEqualTo("xuéxí");
        assertThat(e.definitions()).containsExactly("to learn", "to study");
    }

    @Test
    void umlautBecomesV() {
        var e = CedictParser.parse("女 女 [nu:3] /female/woman/daughter/").orElseThrow();

        assertThat(e.pinyinNumbered()).isEqualTo("nv3");
        assertThat(e.pinyin()).isEqualTo("nǚ");
    }

    @Test
    void properNounKeepsCapitalOnlyInDisplayForm() {
        var e = CedictParser.parse("北京 北京 [Bei3 jing1] /Beijing/").orElseThrow();

        assertThat(e.pinyinNumbered()).isEqualTo("bei3 jing1");
        assertThat(e.pinyin()).isEqualTo("Běijīng");
    }

    @Test
    void latinLettersPassThrough() {
        var e = CedictParser.parse("卡拉OK 卡拉OK [ka3 la1 O K] /karaoke (loanword)/").orElseThrow();

        assertThat(e.pinyinNumbered()).isEqualTo("ka3 la1 O K");
        assertThat(e.pinyin()).isEqualTo("kǎlā OK");
    }

    @Test
    void definitionsMayContainBracketsAndSemicolons() {
        var e = CedictParser.parse("一個 一个 [yi1 ge5] /a; an; one/the whole (afternoon, summer vacation etc)/")
                .orElseThrow();

        assertThat(e.definitions()).containsExactly("a; an; one", "the whole (afternoon, summer vacation etc)");
    }

    @Test
    void skipsCommentsBlankAndMalformedLines() {
        assertThat(CedictParser.parse("# CC-CEDICT")).isEmpty();
        assertThat(CedictParser.parse("")).isEmpty();
        assertThat(CedictParser.parse("学习 [xue2 xi2] /to learn/")).isEmpty();
    }
}
