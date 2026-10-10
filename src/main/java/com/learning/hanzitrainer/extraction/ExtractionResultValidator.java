package com.learning.hanzitrainer.extraction;

import com.learning.hanzitrainer.cedict.PinyinConverter;
import com.learning.hanzitrainer.extraction.ExtractionResult.ExtractedSentence;
import com.learning.hanzitrainer.extraction.ExtractionResult.ExtractedWord;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Cleans model output before it is stored (spec 05, "Validation after extraction"): drops items whose hanzi is not
 * a CJK word, de-duplicates on (hanzi, numbered pinyin), and normalises nulls and blanks.
 */
public final class ExtractionResultValidator {

    private ExtractionResultValidator() {
    }

    public static ExtractionResult validate(ExtractionResult raw) {
        List<String> warnings = new ArrayList<>(clean(raw.warnings()));
        List<ExtractedWord> words = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ExtractedWord word : nullToEmpty(raw.words())) {
            if (word == null) {
                continue;
            }
            String hanzi = trimToNull(word.hanzi());
            String problem = hanziProblem(hanzi);
            if (problem != null) {
                warnings.add("Dropped item '" + Objects.toString(hanzi, "") + "': " + problem);
                continue;
            }
            String pinyin = Objects.toString(trimToNull(word.pinyin()), "");
            if (!seen.add(hanzi + "|" + dedupeKey(hanzi, pinyin))) {
                continue;
            }
            words.add(new ExtractedWord(hanzi, pinyin, word.pinyinFromPage(), trimToNull(word.meaningEn()),
                    trimToNull(word.meaningDe()), trimToNull(word.partOfSpeech()),
                    trimToNull(word.exampleSentence()), Math.clamp(word.confidence(), 0.0, 1.0)));
        }
        List<ExtractedSentence> sentences = nullToEmpty(raw.sentences()).stream()
                .filter(s -> s != null && trimToNull(s.hanzi()) != null)
                .map(s -> new ExtractedSentence(s.hanzi().strip(), trimToNull(s.pinyin()),
                        trimToNull(s.translationEn())))
                .toList();
        return new ExtractionResult(Objects.toString(trimToNull(raw.pageType()), "OTHER"), List.copyOf(words),
                sentences, clean(raw.grammarPoints()), List.copyOf(warnings));
    }

    /** Null if the hanzi is acceptable, otherwise why it is dropped. */
    static String hanziProblem(String hanzi) {
        if (hanzi == null) {
            return "empty";
        }
        boolean hasCjk = false;
        for (int i = 0; i < hanzi.length(); ) {
            int cp = hanzi.codePointAt(i);
            i += Character.charCount(cp);
            if (isCjk(cp)) {
                hasCjk = true;
            } else if (!(cp < 128 && Character.isLetter(cp))) {
                return "contains '" + Character.toString(cp) + "'";
            }
        }
        return hasCjk ? null : "no Chinese characters";
    }

    static int syllableCount(String hanzi) {
        return (int) hanzi.codePoints().filter(cp -> isCjk(cp) || (cp < 128 && Character.isLetter(cp))).count();
    }

    /** Numbered pinyin when it parses, otherwise the lowercased text, so unparsable items are still kept. */
    private static String dedupeKey(String hanzi, String pinyin) {
        try {
            return PinyinConverter.toNumbered(pinyin, syllableCount(hanzi));
        } catch (IllegalArgumentException e) {
            return pinyin.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        }
    }

    private static boolean isCjk(int cp) {
        return (cp >= 0x4E00 && cp <= 0x9FFF) || (cp >= 0x3400 && cp <= 0x4DBF);
    }

    private static List<String> clean(List<String> values) {
        return nullToEmpty(values).stream().map(ExtractionResultValidator::trimToNull).filter(Objects::nonNull)
                .toList();
    }

    private static <T> List<T> nullToEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}
