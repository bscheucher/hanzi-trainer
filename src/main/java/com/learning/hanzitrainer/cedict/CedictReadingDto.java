package com.learning.hanzitrainer.cedict;

import java.util.List;

/** One CC-CEDICT reading offered as a one-click replacement on the review screen. */
public record CedictReadingDto(String pinyin, String pinyinNumbered, List<String> definitions) {

    public static List<CedictReadingDto> of(List<CedictEntry> entries) {
        return entries.stream()
                .map(e -> new CedictReadingDto(e.getPinyin(), e.getPinyinNumbered(), List.copyOf(e.getDefinitions())))
                .toList();
    }
}
