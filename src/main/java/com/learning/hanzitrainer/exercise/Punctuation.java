package com.learning.hanzitrainer.exercise;

/**
 * Punctuation, symbols (～ ￥ 🙂) and spaces in Chinese text, including full-width ones such as the IME space U+3000:
 * everything that is neither a letter, a digit nor a character.
 */
final class Punctuation {

    private Punctuation() {
    }

    /**
     * Punctuation, a symbol or a space; also the invisible parts of emoji (variation selectors, joiners). Symbols
     * that look like hanzi (radicals, strokes, enclosed and squared ideographs such as ⼈ ㊙) are not.
     */
    static boolean is(int codePoint) {
        if (isPunctuationOrSpace(codePoint)) {
            return true;
        }
        if (looksLikeHanzi(codePoint)) {
            return false;
        }
        return isInvisible(codePoint) || switch (Character.getType(codePoint)) {
            case Character.MATH_SYMBOL, Character.CURRENCY_SYMBOL, Character.MODIFIER_SYMBOL,
                 Character.OTHER_SYMBOL -> true;
            default -> false;
        };
    }

    /** Punctuation or a space, but not a symbol: "～" is kept as a WORD_ORDER token, "。" is not. */
    static boolean isPunctuationOrSpace(int codePoint) {
        return switch (Character.getType(codePoint)) {
            case Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION, Character.START_PUNCTUATION,
                 Character.END_PUNCTUATION, Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION,
                 Character.OTHER_PUNCTUATION, Character.SPACE_SEPARATOR, Character.LINE_SEPARATOR,
                 Character.PARAGRAPH_SEPARATOR -> true;
            default -> Character.isWhitespace(codePoint);
        };
    }

    /** Nothing to see: punctuation, a space, or an invisible part of an emoji (U+FE0F, U+200D, U+200B). */
    static boolean isInvisibleOrPunctuation(int codePoint) {
        return isPunctuationOrSpace(codePoint) || isInvisible(codePoint);
    }

    /** {@code text} without punctuation and spaces; symbols such as ℃ are kept, they change the meaning. */
    static String removePunctuationAndSpaces(String text) {
        StringBuilder kept = new StringBuilder(text.length());
        text.codePoints().filter(cp -> !isPunctuationOrSpace(cp)).forEach(kept::appendCodePoint);
        return kept.toString();
    }

    private static boolean isInvisible(int cp) {
        return Character.getType(cp) == Character.FORMAT
                || (cp >= 0xFE00 && cp <= 0xFE0F) || (cp >= 0xE0100 && cp <= 0xE01EF); // variation selectors
    }

    private static boolean looksLikeHanzi(int cp) {
        return (cp >= 0x2E80 && cp <= 0x2FFF)      // CJK radicals supplement, Kangxi radicals, description chars
                || (cp >= 0x3190 && cp <= 0x319F)  // Kanbun (㆒ ㆓ look like 一 二)
                || (cp >= 0x31C0 && cp <= 0x31EF)  // CJK strokes
                || (cp >= 0x3200 && cp <= 0x33FF)  // enclosed CJK letters, CJK compatibility (squared)
                || (cp >= 0x1F200 && cp <= 0x1F2FF); // enclosed ideographic supplement
    }
}
