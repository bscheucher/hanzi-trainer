package com.learning.hanzitrainer.word;

import com.learning.hanzitrainer.cedict.CedictMatch;
import com.learning.hanzitrainer.cedict.CedictReadingDto;

import java.util.List;

/**
 * An extracted word enriched for the review screen (spec 06).
 *
 * @param index          position in {@code raw_extraction.words}
 * @param pinyin         canonical display pinyin; on MATCH the CC-CEDICT reading
 * @param pinyinNumbered null when the pinyin could not be parsed (see {@code pinyinError})
 * @param existingWordId word with the same (hanzi, pinyinNumbered) already in the vocabulary
 */
public record CandidateDto(
        int index,
        String hanzi,
        String pinyin,
        String pinyinNumbered,
        String meaningEn,
        String meaningDe,
        String partOfSpeech,
        String exampleSentence,
        double confidence,
        boolean pinyinFromPage,
        CedictMatch.Status cedictStatus,
        List<CedictReadingDto> cedictReadings,
        String pinyinError,
        Long existingWordId,
        boolean alreadyInLesson) {
}
