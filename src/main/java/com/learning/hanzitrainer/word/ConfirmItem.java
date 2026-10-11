package com.learning.hanzitrainer.word;

/** One reviewed word to confirm (spec 03). Validation is per item, see {@link ConfirmationService}. */
public record ConfirmItem(String hanzi, String pinyin, String meaningEn, String meaningDe, String partOfSpeech,
                          String exampleSentence, boolean acceptMismatch) {

    public ConfirmItem withExample(String sentence) {
        return new ConfirmItem(hanzi, pinyin, meaningEn, meaningDe, partOfSpeech, sentence, acceptMismatch);
    }
}
