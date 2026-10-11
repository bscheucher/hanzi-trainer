package com.learning.hanzitrainer.exercise;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Picks wrong options for {@code HANZI_TO_MEANING} (spec 07). Candidates come in tiers of decreasing preference;
 * a candidate is skipped if it equals the correct meaning, shares a meaning segment with it (split on {@code ;}
 * and {@code ,}, case-insensitive: 学 "to learn" vs 学习 "to study; to learn"), or repeats an option already taken.
 */
final class Distractors {

    private Distractors() {
    }

    static List<String> pick(String correct, int count, List<List<String>> tiers) {
        Set<String> correctSegments = segments(correct);
        Set<String> taken = new HashSet<>(Set.of(normalise(correct)));
        List<String> picked = new ArrayList<>();
        for (List<String> tier : tiers) {
            for (String candidate : tier) {
                if (picked.size() == count) {
                    return picked;
                }
                if (candidate == null || candidate.isBlank()) {
                    continue;
                }
                String key = normalise(candidate);
                if (taken.contains(key) || segments(candidate).stream().anyMatch(correctSegments::contains)) {
                    continue;
                }
                taken.add(key);
                picked.add(candidate.strip());
            }
        }
        return picked;
    }

    static Set<String> segments(String meaning) {
        return Arrays.stream(meaning.split("[;,]"))
                .map(Distractors::normalise)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String normalise(String text) {
        return text.strip().toLowerCase(Locale.ROOT);
    }
}
