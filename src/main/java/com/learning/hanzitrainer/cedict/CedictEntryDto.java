package com.learning.hanzitrainer.cedict;

import java.util.List;

public record CedictEntryDto(Long id, String simplified, String traditional, String pinyin, String pinyinNumbered,
                             List<String> definitions) {

    static CedictEntryDto from(CedictEntry e) {
        return new CedictEntryDto(e.getId(), e.getSimplified(), e.getTraditional(), e.getPinyin(),
                e.getPinyinNumbered(), List.copyOf(e.getDefinitions()));
    }
}
