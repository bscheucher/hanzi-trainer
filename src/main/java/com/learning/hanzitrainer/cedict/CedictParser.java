package com.learning.hanzitrainer.cedict;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses one line of the CC-CEDICT file into a normalised entry (spec 04, "File format"). */
public final class CedictParser {

    private static final Pattern LINE = Pattern.compile("^(\\S+) (\\S+) \\[([^\\]]*)\\] /(.*)/$");

    private CedictParser() {
    }

    /** @return empty for comments, blank lines and lines that do not match the CEDICT format */
    public static Optional<ParsedEntry> parse(String line) {
        if (line == null || line.isBlank() || line.startsWith("#")) {
            return Optional.empty();
        }
        Matcher m = LINE.matcher(line.strip());
        if (!m.matches()) {
            return Optional.empty();
        }
        String cedictPinyin = m.group(3);
        List<String> definitions = Arrays.stream(m.group(4).split("/"))
                .map(String::strip)
                .filter(d -> !d.isEmpty())
                .toList();
        return Optional.of(new ParsedEntry(
                m.group(1),
                m.group(2),
                PinyinConverter.normaliseNumbered(cedictPinyin),
                PinyinConverter.toToneMarks(cedictPinyin),
                definitions));
    }

    public static boolean isComment(String line) {
        return line == null || line.isBlank() || line.startsWith("#");
    }

    /**
     * @param pinyinNumbered stored form: lowercase pinyin syllables, {@code u:} → {@code v}
     * @param pinyin         display form with tone marks, keeping CEDICT's capitalisation ({@code Běijīng})
     */
    public record ParsedEntry(String traditional, String simplified, String pinyinNumbered, String pinyin,
                              List<String> definitions) {
    }
}
