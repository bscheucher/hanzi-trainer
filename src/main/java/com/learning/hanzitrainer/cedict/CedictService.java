package com.learning.hanzitrainer.cedict;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Dictionary lookups and pinyin comparison against CC-CEDICT (spec 04, "Lookup"). */
@Service
@Transactional(readOnly = true)
public class CedictService {

    /** Tone sandhi printed in textbooks: these readings count as equal for the given character. */
    private static final Map<Integer, Set<String>> SANDHI = Map.of(
            (int) '一', Set.of("yi1", "yi2", "yi4"),
            (int) '不', Set.of("bu2", "bu4"));

    private final CedictEntryRepository repository;

    public CedictService(CedictEntryRepository repository) {
        this.repository = repository;
    }

    /** All entries with this simplified form, lowest id first. */
    public List<CedictEntry> lookup(String hanzi) {
        return repository.findBySimplifiedOrderByIdAsc(hanzi.strip());
    }

    /**
     * Compares syllable by syllable, case-insensitively, ignoring spaces; Latin letters are compared one by one
     * ({@code AA zhi4} equals {@code A A zhi4}). 一 and 不 match any of their sandhi readings.
     */
    public CedictMatch match(String hanzi, String pinyinNumbered) {
        List<CedictEntry> entries = lookup(hanzi);
        if (entries.isEmpty()) {
            return CedictMatch.notFound();
        }
        List<Integer> units = hanziUnits(hanzi.strip());
        List<String> candidate = pinyinUnits(pinyinNumbered);
        return entries.stream()
                .filter(e -> sameReading(units, candidate, pinyinUnits(e.getPinyinNumbered())))
                .findFirst()
                .map(CedictMatch::match)
                .orElseGet(CedictMatch::mismatch);
    }

    private static boolean sameReading(List<Integer> hanziUnits, List<String> a, List<String> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i).equals(b.get(i))) {
                continue;
            }
            Set<String> sandhi = i < hanziUnits.size() ? SANDHI.get(hanziUnits.get(i)) : null;
            if (sandhi == null || !sandhi.contains(a.get(i)) || !sandhi.contains(b.get(i))) {
                return false;
            }
        }
        return true;
    }

    /** One unit per syllable token; Latin tokens split into single letters. Lowercase, {@code u:} → {@code v}. */
    static List<String> pinyinUnits(String numbered) {
        List<String> units = new ArrayList<>();
        for (String token : numbered.strip().toLowerCase(Locale.ROOT).replace("u:", "v").split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }
            if (Character.isDigit(token.charAt(token.length() - 1))) {
                units.add(token);
            } else {
                token.chars().filter(Character::isLetter).forEach(c -> units.add(String.valueOf((char) c)));
            }
        }
        return units;
    }

    /** One unit per CJK character or Latin letter, aligned with {@link #pinyinUnits}. */
    private static List<Integer> hanziUnits(String hanzi) {
        return hanzi.codePoints()
                .filter(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN
                        || (cp < 128 && Character.isLetter(cp)))
                .boxed()
                .toList();
    }
}
