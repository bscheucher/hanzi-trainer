package com.learning.hanzitrainer.exercise;

import com.learning.hanzitrainer.cedict.HanziText;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * The words generated sentences may use (spec 07, "Allowed words"), and the check that they really do: LLMs ignore
 * vocabulary constraints, so every Chinese character of a sentence must come from one of these words.
 */
final class AllowedWords {

    /** Basic function words and numerals, allowed in every lesson. */
    static final List<String> BASIC = List.of(
            "的", "了", "是", "不", "很", "吗", "呢", "也", "都", "和", "在", "我", "你", "他", "她", "们", "这", "那",
            "个", "有",
            "一", "二", "三", "四", "五", "六", "七", "八", "九", "十", "两", "百");

    private final Set<String> words;
    private final Set<Integer> characters;
    private final int longestWord;

    AllowedWords(Collection<String> vocabulary) {
        words = new LinkedHashSet<>(vocabulary);
        words.addAll(BASIC);
        characters = words.stream().flatMapToInt(String::codePoints).boxed().collect(Collectors.toSet());
        longestWord = words.stream().mapToInt(w -> w.codePointCount(0, w.length())).max().orElse(1);
    }

    Set<String> words() {
        return words;
    }

    /**
     * Splits {@code text} into allowed words, longest match first from the left; any other character is a segment
     * of its own. So 学 is not a word of 我去学校, but 学校 is.
     */
    List<String> segment(String text) {
        int[] cps = text.codePoints().toArray();
        List<String> segments = new ArrayList<>();
        int i = 0;
        while (i < cps.length) {
            int length = 1;
            for (int n = Math.min(longestWord, cps.length - i); n > 1; n--) {
                if (words.contains(new String(cps, i, n))) {
                    length = n;
                    break;
                }
            }
            segments.add(new String(cps, i, length));
            i += length;
        }
        return segments;
    }

    /** The Chinese characters of {@code text} that no allowed word contains, sorted; punctuation is ignored. */
    Set<String> disallowedCharacters(String text) {
        return text.codePoints()
                .filter(HanziText::isCjk)
                .filter(cp -> !characters.contains(cp))
                .mapToObj(Character::toString)
                .collect(Collectors.toCollection(TreeSet::new));
    }
}
