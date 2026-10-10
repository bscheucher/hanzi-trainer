package com.learning.hanzitrainer.extraction;

import java.util.List;

/**
 * What the vision model reads from one textbook page; also the JSON stored in {@code page_image.raw_extraction}
 * (spec 05).
 *
 * @param pageType      VOCABULARY_LIST | TEXT | DIALOGUE | GRAMMAR | EXERCISES | OTHER
 * @param grammarPoints short English descriptions, may be empty
 * @param warnings      e.g. "bottom of page cut off", "handwriting on page"
 */
public record ExtractionResult(
        String pageType,
        List<ExtractedWord> words,
        List<ExtractedSentence> sentences,
        List<String> grammarPoints,
        List<String> warnings) {

    /**
     * @param hanzi           simplified only
     * @param pinyin          tone marks, as printed in the book if present
     * @param pinyinFromPage  true if the pinyin was printed, false if the model supplied it
     * @param meaningDe       may be null
     * @param partOfSpeech    book's abbreviation if printed (n., v., adj., m., ...), else the model's guess
     * @param exampleSentence a sentence from this page that uses the word, or null
     * @param confidence      0..1, the model's own estimate of reading the characters correctly
     */
    public record ExtractedWord(
            String hanzi,
            String pinyin,
            boolean pinyinFromPage,
            String meaningEn,
            String meaningDe,
            String partOfSpeech,
            String exampleSentence,
            double confidence) {
    }

    public record ExtractedSentence(String hanzi, String pinyin, String translationEn) {
    }
}
