package com.learning.hanzitrainer.cedict;

import java.util.List;

/**
 * Result of {@code GET /api/dictionary/check}: the same normalisation and CC-CEDICT comparison that confirmation
 * applies, so the review screen's badge never disagrees with the confirm result.
 */
public record ReadingCheckDto(String pinyin, String pinyinNumbered, CedictMatch.Status cedictStatus,
                              List<CedictReadingDto> cedictReadings, String error) {

    public static ReadingCheckDto from(CedictService.ReadingCheck check) {
        return new ReadingCheckDto(check.pinyin(), check.pinyinNumbered(), check.status(),
                CedictReadingDto.of(check.entries()), check.error());
    }
}
