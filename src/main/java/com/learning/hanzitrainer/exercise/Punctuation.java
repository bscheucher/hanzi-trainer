package com.learning.hanzitrainer.exercise;

/**
 * Punctuation, symbols (～ ￥ % 🙂) and spaces in Chinese text, including full-width ones such as the IME space U+3000:
 * everything that is neither a letter, a digit nor a character.
 */
final class Punctuation {

    private Punctuation() {
    }

    /** Punctuation, a symbol or a space; also the invisible parts of emoji (variation selectors, joiners). */
    static boolean is(int codePoint) {
        return isPunctuationOrSpace(codePoint) || switch (Character.getType(codePoint)) {
            case Character.MATH_SYMBOL, Character.CURRENCY_SYMBOL, Character.MODIFIER_SYMBOL, Character.OTHER_SYMBOL,
                 Character.FORMAT -> true;
            default -> (codePoint >= 0xFE00 && codePoint <= 0xFE0F) || (codePoint >= 0xE0100 && codePoint <= 0xE01EF);
        };
    }

    /** Punctuation or a space, but not a symbol: a WORD_ORDER token of only these is dropped, "～" is kept. */
    static boolean isPunctuationOrSpace(int codePoint) {
        return switch (Character.getType(codePoint)) {
            case Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION, Character.START_PUNCTUATION,
                 Character.END_PUNCTUATION, Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION,
                 Character.OTHER_PUNCTUATION, Character.SPACE_SEPARATOR, Character.LINE_SEPARATOR,
                 Character.PARAGRAPH_SEPARATOR -> true;
            default -> Character.isWhitespace(codePoint);
        };
    }

    /** {@code text} without anything {@link #is} matches. */
    static String remove(String text) {
        StringBuilder kept = new StringBuilder(text.length());
        text.codePoints().filter(cp -> !is(cp)).forEach(kept::appendCodePoint);
        return kept.toString();
    }
}
