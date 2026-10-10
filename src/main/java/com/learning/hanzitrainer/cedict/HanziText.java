package com.learning.hanzitrainer.cedict;

/** Rules for the hanzi of a vocabulary item (spec 05 validation, spec 06 confirmation). */
public final class HanziText {

    /** Middle dots in transliterated names (马克·吐温); allowed, but not counted as syllables. */
    private static final String NAME_SEPARATORS = "·・";

    private HanziText() {
    }

    /** Null if the hanzi is acceptable, otherwise a short reason ("no Chinese characters", "contains '1'"). */
    public static String problem(String hanzi) {
        if (hanzi == null || hanzi.isBlank()) {
            return "empty";
        }
        boolean hasCjk = false;
        for (int i = 0; i < hanzi.length(); ) {
            int cp = hanzi.codePointAt(i);
            i += Character.charCount(cp);
            if (isCjk(cp)) {
                hasCjk = true;
            } else if (!isAsciiLetter(cp) && NAME_SEPARATORS.indexOf(cp) < 0) {
                return "contains '" + Character.toString(cp) + "'";
            }
        }
        return hasCjk ? null : "no Chinese characters";
    }

    /** Syllables the pinyin must have: one per CJK character (儿 included) plus one per Latin letter. */
    public static int syllableCount(String hanzi) {
        return (int) hanzi.codePoints().filter(cp -> isCjk(cp) || isAsciiLetter(cp)).count();
    }

    public static boolean isCjk(int cp) {
        return (cp >= 0x4E00 && cp <= 0x9FFF) || (cp >= 0x3400 && cp <= 0x4DBF) || cp == 0x3007; // 〇 líng
    }

    private static boolean isAsciiLetter(int cp) {
        return cp < 128 && Character.isLetter(cp);
    }
}
