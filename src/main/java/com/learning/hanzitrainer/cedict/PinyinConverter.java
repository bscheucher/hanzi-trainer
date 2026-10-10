package com.learning.hanzitrainer.cedict;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts between numbered pinyin ({@code xue2 xi2}) and tone-marked pinyin ({@code xuéxí}).
 * Rules and test table: {@code src/docs/specs/04-cedict-and-pinyin.md}. TS twin: {@code frontend/src/lib/pinyin.ts}.
 */
public final class PinyinConverter {

    private static final String VOWELS = "aeiouv";
    private static final String[] MARKED_LOWER = {"āáǎà", "ēéěè", "īíǐì", "ōóǒò", "ūúǔù", "ǖǘǚǜ"};
    private static final String[] MARKED_UPPER = {"ĀÁǍÀ", "ĒÉĚÈ", "ĪÍǏÌ", "ŌÓǑÒ", "ŪÚǓÙ", "ǕǗǙǛ"};

    /** A numbered syllable token: letters (incl. {@code u:} / {@code ü}) followed by a tone digit. */
    private static final Pattern NUMBERED_SYLLABLE = Pattern.compile("([A-Za-zÜü:]+)([1-5])");
    private static final Pattern TONE_DIGIT = Pattern.compile("(?<=[A-Za-zÜü:])[1-5]");

    /** Syllables without a vowel; only accepted as a whole word, never inside a segmentation. */
    private static final Set<String> STANDALONE_ONLY = Set.of("m", "n", "ng", "hm", "hng");
    private static final String ERHUA = "r";
    private static final int MAX_SYLLABLE_LENGTH = 6;

    private static final Set<String> SYLLABLES = loadSyllables();
    /** Marked or plain vowel → {base letter, tone (0 = none)}. */
    private static final Map<Character, int[]> VOWEL_TONES = buildVowelTones();

    private PinyinConverter() {
    }

    // ------------------------------------------------------------------ numbered → marks

    /**
     * {@code "xue2 xi2"} → {@code "xuéxí"}. Syllables of one word are joined; an apostrophe goes before a
     * non-initial syllable starting with a, e or o. Tokens without a tone digit (Latin letters, punctuation)
     * pass through, separated by spaces; adjacent single Latin letters are joined ({@code "O K"} → {@code "OK"}).
     */
    public static String toToneMarks(String numbered) {
        if (numbered == null || numbered.isBlank()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        Token previous = null;
        for (String raw : numbered.trim().split("\\s+")) {
            Matcher m = NUMBERED_SYLLABLE.matcher(raw);
            Token token = m.matches()
                    ? Token.syllable(normaliseLetters(m.group(1)), Integer.parseInt(m.group(2)))
                    : Token.passThrough(raw);
            if (previous != null) {
                if (previous.syllable && token.syllable) {
                    if (startsWithAeo(token.text)) {
                        out.append('\'');
                    }
                } else if (!(previous.isSingleLetter() && token.isSingleLetter())) {
                    out.append(' ');
                }
            }
            out.append(token.syllable ? markSyllable(token.text, token.tone) : token.text);
            previous = token;
        }
        return out.toString();
    }

    private static String markSyllable(String letters, int tone) {
        int markAt = tone >= 1 && tone <= 4 ? markPosition(letters.toLowerCase()) : -1;
        StringBuilder out = new StringBuilder(letters.length());
        for (int i = 0; i < letters.length(); i++) {
            char c = letters.charAt(i);
            char lower = Character.toLowerCase(c);
            boolean upper = Character.isUpperCase(c);
            if (i == markAt) {
                String marks = (upper ? MARKED_UPPER : MARKED_LOWER)[VOWELS.indexOf(lower)];
                out.append(marks.charAt(tone - 1));
            } else if (lower == 'v') {
                out.append(upper ? 'Ü' : 'ü');
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** a or e takes the mark; else o in "ou"; else the last vowel. -1 if there is no vowel. */
    private static int markPosition(String lower) {
        int a = lower.indexOf('a');
        if (a >= 0) {
            return a;
        }
        int e = lower.indexOf('e');
        if (e >= 0) {
            return e;
        }
        int ou = lower.indexOf("ou");
        if (ou >= 0) {
            return ou;
        }
        for (int i = lower.length() - 1; i >= 0; i--) {
            if (VOWELS.indexOf(lower.charAt(i)) >= 0) {
                return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ marks → numbered

    /**
     * {@code "xuéxí", 2} → {@code "xue2 xi2"}. Pinyin syllables are lowercased; Latin letters (uppercase ASCII
     * letters not followed by a lowercase letter, e.g. {@code OK}, {@code T}) are emitted one per letter and keep
     * their case.
     *
     * @param syllableCount number of CJK characters plus Latin letters in the word's hanzi
     * @throws IllegalArgumentException if no segmentation yields exactly {@code syllableCount} syllables
     */
    public static String toNumbered(String marked, int syllableCount) {
        Objects.requireNonNull(marked, "marked");
        List<Item> items = tokenise(Normalizer.normalize(marked.trim(), Normalizer.Form.NFC));
        long latin = items.stream().filter(Item::isLatin).count();
        int chunks = (int) items.stream().filter(i -> !i.isLatin()).count();
        int target = syllableCount - (int) latin;
        if (items.isEmpty() || target < chunks) {
            throw unparsable(marked, syllableCount);
        }
        List<String> syllables = new ArrayList<>();
        if (!segment(items, 0, 0, target, syllables)) {
            throw unparsable(marked, syllableCount);
        }
        // Re-interleave Latin letters with the segmented syllables in input order.
        List<String> out = new ArrayList<>();
        int next = 0;
        for (Item item : items) {
            if (item.isLatin()) {
                out.add(item.latin);
            } else {
                for (int k = 0; k < item.syllableCount; k++) {
                    out.add(syllables.get(next++));
                }
            }
        }
        return String.join(" ", out);
    }

    private static IllegalArgumentException unparsable(String marked, int syllableCount) {
        return new IllegalArgumentException(
                "Unparsable pinyin '" + marked + "' for " + syllableCount + " syllable(s)");
    }

    /** Splits into Latin letters and chunks of pinyin letters; whitespace, apostrophes and punctuation separate. */
    private static List<Item> tokenise(String s) {
        List<Item> items = new ArrayList<>();
        StringBuilder base = new StringBuilder();
        List<Integer> tones = new ArrayList<>();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            char next = i + 1 < s.length() ? s.charAt(i + 1) : ' ';
            if (c >= 'A' && c <= 'Z' && !Character.isLowerCase(next)) {
                flushChunk(items, base, tones);
                items.add(Item.latin(String.valueOf(c)));
            } else if (Character.isWhitespace(c) || "'’‘-,·".indexOf(c) >= 0) {
                flushChunk(items, base, tones);
            } else if (c == 'ü' || c == 'Ü') {
                base.append('v');
                tones.add(0);
            } else if (VOWEL_TONES.containsKey(c)) {
                int[] baseAndTone = VOWEL_TONES.get(c);
                base.append((char) baseAndTone[0]);
                tones.add(baseAndTone[1]);
            } else if (Character.isLetter(c) && c < 128) {
                base.append(Character.toLowerCase(c));
                tones.add(0);
            } else {
                throw new IllegalArgumentException("Unexpected character '" + c + "' in pinyin '" + s + "'");
            }
        }
        flushChunk(items, base, tones);
        return items;
    }

    private static void flushChunk(List<Item> items, StringBuilder base, List<Integer> tones) {
        if (!base.isEmpty()) {
            items.add(Item.chunk(base.toString(), tones.stream().mapToInt(Integer::intValue).toArray()));
            base.setLength(0);
            tones.clear();
        }
    }

    /**
     * Depth-first, longest syllable first. Fills {@code out} and each chunk's {@code syllableCount};
     * returns false if no segmentation of the remaining input yields exactly {@code remaining} syllables.
     */
    private static boolean segment(List<Item> items, int itemIndex, int pos, int remaining, List<String> out) {
        while (itemIndex < items.size() && items.get(itemIndex).isLatin()) {
            itemIndex++;
        }
        if (itemIndex == items.size()) {
            return remaining == 0;
        }
        Item chunk = items.get(itemIndex);
        if (pos == chunk.base.length()) {
            return segment(items, itemIndex + 1, 0, remaining, out);
        }
        if (pos == 0) {
            chunk.syllableCount = 0;
        }
        if (remaining <= 0) {
            return false;
        }
        int maxEnd = Math.min(chunk.base.length(), pos + MAX_SYLLABLE_LENGTH);
        for (int end = maxEnd; end > pos; end--) {
            String candidate = chunk.base.substring(pos, end);
            int tone = toneOf(chunk.tones, pos, end);
            if (tone < 0 || !acceptable(candidate, chunk, pos, end, tone, out.isEmpty())) {
                continue;
            }
            out.add(candidate + (tone == 0 ? 5 : tone));
            chunk.syllableCount++;
            if (segment(items, itemIndex, end, remaining - 1, out)) {
                return true;
            }
            out.removeLast();
            chunk.syllableCount--;
        }
        return false;
    }

    private static boolean acceptable(String candidate, Item chunk, int pos, int end, int tone, boolean first) {
        if (pos > 0 && startsWithAeo(candidate)) {
            return false; // would need an apostrophe: xīnán = xi1 nan2, never xin1 an2
        }
        if (candidate.equals(ERHUA)) {
            return !first && tone == 0;
        }
        if (STANDALONE_ONLY.contains(candidate)) {
            return pos == 0 && end == chunk.base.length();
        }
        return SYLLABLES.contains(candidate);
    }

    /** The single tone marked within [from, to), 0 if none, -1 if more than one. */
    private static int toneOf(int[] tones, int from, int to) {
        int tone = 0;
        for (int i = from; i < to; i++) {
            if (tones[i] != 0) {
                if (tone != 0) {
                    return -1;
                }
                tone = tones[i];
            }
        }
        return tone;
    }

    // ------------------------------------------------------------------ search helper

    /** {@code "xue2 xi2"} → {@code "xue xi"}. */
    public static String stripTones(String numbered) {
        return numbered == null ? "" : TONE_DIGIT.matcher(numbered.trim()).replaceAll("");
    }

    /** CEDICT numbered pinyin → stored form: pinyin syllables lowercased, {@code u:} → {@code v}. */
    public static String normaliseNumbered(String cedictPinyin) {
        List<String> out = new ArrayList<>();
        for (String raw : cedictPinyin.trim().split("\\s+")) {
            Matcher m = NUMBERED_SYLLABLE.matcher(raw);
            out.add(m.matches() ? normaliseLetters(m.group(1)).toLowerCase() + m.group(2) : raw);
        }
        return String.join(" ", out);
    }

    // ------------------------------------------------------------------ helpers

    private static String normaliseLetters(String letters) {
        return letters.replace("u:", "v").replace("U:", "V").replace('ü', 'v').replace('Ü', 'V');
    }

    private static boolean startsWithAeo(String syllable) {
        return !syllable.isEmpty() && "aeo".indexOf(Character.toLowerCase(syllable.charAt(0))) >= 0;
    }

    private static Set<String> loadSyllables() {
        InputStream in = PinyinConverter.class.getResourceAsStream("/pinyin/syllables.txt");
        if (in == null) {
            throw new IllegalStateException("Missing resource /pinyin/syllables.txt");
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            Set<String> syllables = new HashSet<>();
            reader.lines().map(String::trim).filter(l -> !l.isEmpty() && !l.startsWith("#")).forEach(syllables::add);
            return Set.copyOf(syllables);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Map<Character, int[]> buildVowelTones() {
        Map<Character, int[]> map = new HashMap<>();
        for (int v = 0; v < VOWELS.length(); v++) {
            char base = VOWELS.charAt(v);
            for (int tone = 1; tone <= 4; tone++) {
                map.put(MARKED_LOWER[v].charAt(tone - 1), new int[]{base, tone});
                map.put(MARKED_UPPER[v].charAt(tone - 1), new int[]{base, tone});
            }
        }
        return Map.copyOf(map);
    }

    private record Token(boolean syllable, String text, int tone) {

        static Token syllable(String letters, int tone) {
            return new Token(true, letters, tone);
        }

        static Token passThrough(String text) {
            return new Token(false, text, 0);
        }

        boolean isSingleLetter() {
            return !syllable && text.length() == 1 && Character.isLetter(text.charAt(0)) && text.charAt(0) < 128;
        }
    }

    /** Either one Latin letter or a chunk of pinyin letters with per-letter tones (0 = unmarked). */
    private static final class Item {
        final String latin;
        final String base;
        final int[] tones;
        int syllableCount;

        private Item(String latin, String base, int[] tones) {
            this.latin = latin;
            this.base = base;
            this.tones = tones;
        }

        static Item latin(String letter) {
            return new Item(letter, null, null);
        }

        static Item chunk(String base, int[] tones) {
            return new Item(null, base, tones);
        }

        boolean isLatin() {
            return latin != null;
        }
    }
}
