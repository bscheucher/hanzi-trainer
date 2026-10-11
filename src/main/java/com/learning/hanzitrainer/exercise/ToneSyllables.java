package com.learning.hanzitrainer.exercise;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The syllables a {@code TONE} exercise asks (spec 07): every numbered syllable of {@code pinyin_numbered} except
 * erhua {@code r5}; Latin letters ({@code AA制}) have no tone and are skipped. Display form writes {@code v} as ü.
 */
record ToneSyllables(List<String> syllables, List<Integer> tones) {

    private static final Pattern SYLLABLE = Pattern.compile("([a-zv]+)([1-5])");

    static ToneSyllables of(String pinyinNumbered) {
        List<String> syllables = new ArrayList<>();
        List<Integer> tones = new ArrayList<>();
        for (String token : pinyinNumbered.strip().split("\\s+")) {
            Matcher m = SYLLABLE.matcher(token);
            if (!m.matches() || token.equals("r5")) {
                continue;
            }
            syllables.add(m.group(1).replace('v', 'ü'));
            tones.add(Integer.parseInt(m.group(2)));
        }
        return new ToneSyllables(List.copyOf(syllables), List.copyOf(tones));
    }

    boolean isEmpty() {
        return syllables.isEmpty();
    }
}
