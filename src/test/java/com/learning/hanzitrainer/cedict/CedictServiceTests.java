package com.learning.hanzitrainer.cedict;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CedictServiceTests {

    private final CedictEntryRepository repository = mock(CedictEntryRepository.class);
    private final CedictService service = new CedictService(repository);

    @Test
    void lookupStripsWhitespace() {
        CedictEntry xuexi = entry(1, "学习", "xue2 xi2");
        when(repository.findBySimplifiedOrderByIdAsc("学习")).thenReturn(List.of(xuexi));

        assertThat(service.lookup(" 学习 ")).containsExactly(xuexi);
    }

    @Test
    void matchReturnsEntry() {
        CedictEntry xuexi = entry(1, "学习", "xue2 xi2");
        given("学习", xuexi);

        CedictMatch match = service.match("学习", "xue2 xi2");

        assertThat(match.status()).isEqualTo(CedictMatch.Status.MATCH);
        assertThat(match.entry()).isSameAs(xuexi);
    }

    @Test
    void matchIgnoresCaseAndSpacing() {
        given("北京", entry(1, "北京", "bei3 jing1"));

        assertThat(service.match("北京", " Bei3  jing1 ").status()).isEqualTo(CedictMatch.Status.MATCH);
    }

    @Test
    void polyphoneWithOtherReadingIsMismatch() {
        given("了", entry(1, "了", "le5"), entry(2, "了", "liao3"));

        assertThat(service.match("了", "liao3").entry().getId()).isEqualTo(2);
        assertThat(service.match("了", "liao4").status()).isEqualTo(CedictMatch.Status.PINYIN_MISMATCH);
    }

    @Test
    void unknownHanziIsNotFound() {
        given("龘");

        assertThat(service.match("龘", "da2").status()).isEqualTo(CedictMatch.Status.NOT_FOUND);
    }

    @Test
    void duplicatesLinkTheLowestId() {
        given("干", entry(3, "干", "gan1"), entry(7, "干", "gan1"), entry(9, "干", "gan4"));

        assertThat(service.match("干", "gan1").entry().getId()).isEqualTo(3);
    }

    @Test
    void toneSandhiOfYiAndBuMatches() {
        given("不是", entry(1, "不是", "bu4 shi4"));
        given("一个", entry(2, "一个", "yi1 ge5"));

        assertThat(service.match("不是", "bu2 shi4").status()).isEqualTo(CedictMatch.Status.MATCH);
        assertThat(service.match("一个", "yi2 ge5").status()).isEqualTo(CedictMatch.Status.MATCH);
        assertThat(service.match("一个", "yi4 ge5").status()).isEqualTo(CedictMatch.Status.MATCH);
        assertThat(service.match("一个", "yi3 ge5").status()).isEqualTo(CedictMatch.Status.PINYIN_MISMATCH);
    }

    @Test
    void sandhiOnlyAppliesToYiAndBu() {
        given("是不", entry(1, "是不", "shi4 bu4"));

        // shi2 is not a sandhi form of 是
        assertThat(service.match("是不", "shi2 bu2").status()).isEqualTo(CedictMatch.Status.PINYIN_MISMATCH);
        assertThat(service.match("是不", "shi4 bu2").status()).isEqualTo(CedictMatch.Status.MATCH);
    }

    @Test
    void latinLettersCompareLetterByLetter() {
        given("AA制", entry(1, "AA制", "AA zhi4"));
        given("Tony老师", entry(2, "Tony老师", "t o n y lao3 shi1"));

        assertThat(service.match("AA制", "A A zhi4").status()).isEqualTo(CedictMatch.Status.MATCH);
        assertThat(service.match("Tony老师", "T O N Y lao3 shi1").status()).isEqualTo(CedictMatch.Status.MATCH);
    }

    @Test
    void checkCanonicalisesMatchesToDictionaryReading() {
        given("不是", entry(1, "不是", "bu4 shi4"));

        var check = service.check(" 不是 ", "bú shì");

        assertThat(check.status()).isEqualTo(CedictMatch.Status.MATCH);
        assertThat(check.pinyinNumbered()).isEqualTo("bu4 shi4");
        assertThat(check.pinyin()).isEqualTo("bùshì");
        assertThat(check.error()).isNull();
    }

    @Test
    void checkKeepsProperNounCapital() {
        CedictEntry beijing = new CedictEntry("北京", "北京", "bei3 jing1", "Běijīng", List.of("Beijing"));
        ReflectionTestUtils.setField(beijing, "id", 1L);
        given("北京", beijing);

        assertThat(service.check("北京", "běijīng").pinyin()).isEqualTo("Běijīng");
        given("北海");
        assertThat(service.check("北海", "Běihǎi").pinyin()).isEqualTo("Běihǎi");
    }

    @Test
    void checkPrefersTheEntryWhoseCaseMatchesTheInput() {
        CedictEntry surname = new CedictEntry("王", "王", "Wang2", "Wáng", List.of("surname Wang"));
        ReflectionTestUtils.setField(surname, "id", 1L);
        CedictEntry king = entry(2, "王", "wang2");
        given("王", surname, king);

        var common = service.check("王", "wáng");
        assertThat(common.pinyin()).isEqualTo("wáng");
        assertThat(common.entry()).isSameAs(king);

        var name = service.check("王", "Wáng");
        assertThat(name.pinyin()).isEqualTo("Wáng");
        assertThat(name.entry()).isSameAs(surname);
    }

    @Test
    void checkRejectsInvalidHanziLikeConfirmation() {
        given("你好!");

        var check = service.check("你好!", "nǐhǎo");

        assertThat(check.pinyinNumbered()).isNull();
        assertThat(check.error()).isEqualTo("Hanzi '你好!': contains '!'");
    }

    @Test
    void checkReportsUnparsablePinyinWithoutThrowing() {
        given("学习", entry(1, "学习", "xue2 xi2"));

        var check = service.check("学习", "xue");

        assertThat(check.pinyinNumbered()).isNull();
        assertThat(check.status()).isEqualTo(CedictMatch.Status.PINYIN_MISMATCH);
        assertThat(check.error()).contains("2 syllable");
        assertThat(check.entries()).hasSize(1);
    }

    private void given(String hanzi, CedictEntry... entries) {
        when(repository.findBySimplifiedOrderByIdAsc(hanzi)).thenReturn(List.of(entries));
    }

    private static CedictEntry entry(long id, String simplified, String pinyinNumbered) {
        CedictEntry e = new CedictEntry(simplified, simplified, pinyinNumbered,
                PinyinConverter.toToneMarks(pinyinNumbered), List.of("definition"));
        ReflectionTestUtils.setField(e, "id", id);
        return e;
    }
}
