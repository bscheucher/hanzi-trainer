package com.learning.hanzitrainer.word;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Turns the {@code q} of {@code GET /words} into the parameters of {@link WordRepository#search} (spec 03).
 * Hanzi and meanings match by case-insensitive substring. A query that looks like pinyin is also matched against
 * the reading: without tones ({@code xuexi}) it ignores tones; with tone marks ({@code xuéxí}) or tone numbers
 * ({@code xue2xi2}) only that reading matches. Spaces and apostrophes in the query are ignored.
 */
record WordSearch(String text, String pinyinMode, String pinyin) {

    static final WordSearch NONE = new WordSearch(null, "NONE", null);

    /** Latin letters (ü included), tone-marked vowels and tone digits only: anything else is not pinyin. */
    private static final Pattern PINYIN_CHARS = Pattern.compile("[a-zü:āáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜ1-5]+");
    private static final Pattern TONE_MARKS = Pattern.compile("[āáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜ]");
    private static final Pattern TONE_DIGITS = Pattern.compile("[1-5]");

    static WordSearch of(String q) {
        if (q == null || q.isBlank()) {
            return NONE;
        }
        String query = Normalizer.normalize(q.strip(), Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        String text = contains(query);
        String compact = query.replaceAll("[\\s'’]", "");
        if (!PINYIN_CHARS.matcher(compact).matches()) {
            return new WordSearch(text, "NONE", null);
        }
        if (TONE_DIGITS.matcher(compact).find()) {
            return new WordSearch(text, "NUMBERED", contains(compact.replace("u:", "v").replace('ü', 'v')));
        }
        if (TONE_MARKS.matcher(compact).find()) {
            return new WordSearch(text, "MARKED", contains(compact));
        }
        return new WordSearch(text, "TONELESS", contains(compact.replace("u:", "v").replace('ü', 'v')));
    }

    private static String contains(String value) {
        return "%" + value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
