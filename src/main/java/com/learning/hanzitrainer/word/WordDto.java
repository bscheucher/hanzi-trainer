package com.learning.hanzitrainer.word;

public record WordDto(Long id, String hanzi, String pinyin, String pinyinNumbered, String meaningEn,
                      String meaningDe, String partOfSpeech, boolean verified) {

    public static WordDto from(Word w) {
        return new WordDto(w.getId(), w.getHanzi(), w.getPinyin(), w.getPinyinNumbered(), w.getMeaningEn(),
                w.getMeaningDe(), w.getPartOfSpeech(), w.isVerified());
    }
}
