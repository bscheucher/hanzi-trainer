package com.learning.hanzitrainer.exercise;

import java.util.regex.Pattern;

/** Punctuation and spaces in Chinese text, including full-width ones such as the IME space U+3000. */
final class Punctuation {

    private static final Pattern PUNCTUATION_OR_SPACE = Pattern.compile("[\\p{P}\\p{Z}\\s]");

    private Punctuation() {
    }

    static boolean is(int codePoint) {
        return PUNCTUATION_OR_SPACE.matcher(Character.toString(codePoint)).matches();
    }

    static String remove(String text) {
        return PUNCTUATION_OR_SPACE.matcher(text).replaceAll("");
    }
}
