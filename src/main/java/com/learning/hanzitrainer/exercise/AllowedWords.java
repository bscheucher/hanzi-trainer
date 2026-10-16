package com.learning.hanzitrainer.exercise;

import com.learning.hanzitrainer.cedict.HanziText;

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

    AllowedWords(Collection<String> vocabulary) {
        words = new LinkedHashSet<>(vocabulary);
        words.addAll(BASIC);
        characters = words.stream().flatMapToInt(String::codePoints).boxed().collect(Collectors.toSet());
    }

    Set<String> words() {
        return words;
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
