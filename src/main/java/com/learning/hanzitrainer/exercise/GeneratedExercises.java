package com.learning.hanzitrainer.exercise;

import com.learning.hanzitrainer.word.WordDto;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Checks an exercise the LLM wrote and turns it into a stored prompt and answer (spec 07, "Allowed-character
 * validation"). Anything that fails a check is discarded with a reason; the caller counts it as rejected.
 */
final class GeneratedExercises {

    static final String BLANK = "___";
    /** Models write blanks of any length, sometimes with full-width underscores. */
    private static final Pattern ANY_BLANK = Pattern.compile("[_＿]{2,}");
    private static final int DISTRACTORS = 3;
    private static final int SHUFFLE_ATTEMPTS = 20;

    private GeneratedExercises() {
    }

    /**
     * @param sentence the Chinese sentence, used to find duplicates
     * @param words    the target words the exercise practises (stored in {@code exercise_word})
     */
    record Built(ExercisePrompt prompt, ExerciseAnswer answer, String sentence, List<WordDto> words) {
    }

    /** Either {@code built} or {@code rejection} is set. */
    record Result(Built built, String rejection) {

        static Result rejected(String reason) {
            return new Result(null, reason);
        }
    }

    /**
     * @param existingSentences Chinese sentences of this type already stored for the lesson or built in this job
     * @param sequence          running number of the exercise in the lesson; TRANSLATION alternates German
     *                          and English sources by it
     */
    static Result check(ExerciseType type, GeneratedExercise item, List<WordDto> targetWords, AllowedWords allowed,
                        Set<String> existingSentences, int sequence, Random random) {
        Result shaped = switch (item) {
            case GeneratedExercise.FillBlank f when type == ExerciseType.FILL_BLANK -> fillBlank(f, random);
            case GeneratedExercise.WordOrder w when type == ExerciseType.WORD_ORDER -> wordOrder(w, random);
            case GeneratedExercise.Translation t when type == ExerciseType.TRANSLATION -> translation(t, sequence);
            default -> Result.rejected("not a " + type + " exercise");
        };
        if (shaped.built() == null) {
            return shaped;
        }
        Built built = shaped.built();
        Set<String> disallowed = allowed.disallowedCharacters(chineseText(built));
        if (!disallowed.isEmpty()) {
            return Result.rejected("uses characters outside the vocabulary: " + String.join(" ", disallowed));
        }
        if (existingSentences.contains(built.sentence())) {
            return Result.rejected("duplicate sentence " + built.sentence());
        }
        List<WordDto> words = practisedWords(built.sentence(), targetWords, allowed);
        if (words.isEmpty()) {
            return Result.rejected("uses no target word: " + built.sentence());
        }
        if (built.prompt() instanceof ExercisePrompt.Translation p) {
            return ok(new ExercisePrompt.Translation(p.source(), p.sourceLang(),
                    words.stream().map(WordDto::hanzi).toList()), built.answer(), built.sentence(), words);
        }
        return ok(built.prompt(), built.answer(), built.sentence(), words);
    }

    /**
     * The target words that are whole words of the segmented sentence. Targets with the same hanzi but different
     * pinyin (polyphones) are left out: the text does not tell which reading the sentence uses.
     */
    static List<WordDto> practisedWords(String sentence, List<WordDto> targetWords, AllowedWords allowed) {
        Set<String> segments = new HashSet<>(allowed.segment(sentence));
        Map<String, Long> perHanzi = targetWords.stream()
                .collect(Collectors.groupingBy(WordDto::hanzi, Collectors.counting()));
        return targetWords.stream()
                .filter(w -> perHanzi.get(w.hanzi()) == 1 && segments.contains(w.hanzi()))
                .toList();
    }

    /** The Chinese sentence of a stored sentence exercise, as {@link Built#sentence()}. */
    static String sentenceOf(ExerciseAnswer answer) {
        return switch (answer) {
            case ExerciseAnswer.FillBlank a -> a.fullSentence();
            case ExerciseAnswer.WordOrder a -> String.join("", a.accepted().getFirst());
            case ExerciseAnswer.Translation a -> a.reference();
            default -> throw new IllegalArgumentException(answer + " is not a sentence exercise");
        };
    }

    private static Result fillBlank(GeneratedExercise.FillBlank item, Random random) {
        String correct = trim(item.correct());
        String translation = trim(item.translationEn());
        if (item.sentence() == null || correct.isEmpty() || translation.isEmpty()) {
            return Result.rejected("incomplete FILL_BLANK");
        }
        String sentence = ANY_BLANK.matcher(item.sentence().strip()).replaceAll(BLANK);
        int blanks = sentence.split(Pattern.quote(BLANK), -1).length - 1;
        if (blanks != 1) {
            return Result.rejected("FILL_BLANK needs exactly one blank, not " + blanks + ": " + sentence);
        }
        Set<String> distractors = new LinkedHashSet<>(trimmed(item.distractors()));
        distractors.remove(correct);
        if (distractors.size() < DISTRACTORS) {
            return Result.rejected("FILL_BLANK needs " + DISTRACTORS + " distinct wrong options: " + sentence);
        }
        List<String> options = new ArrayList<>(distractors.stream().limit(DISTRACTORS).toList());
        options.add(correct);
        Collections.shuffle(options, random);
        String full = sentence.replace(BLANK, correct);
        return ok(new ExercisePrompt.FillBlank(sentence, translation, options),
                new ExerciseAnswer.FillBlank(correct, full), full, List.of());
    }

    private static Result wordOrder(GeneratedExercise.WordOrder item, Random random) {
        String translation = trim(item.translationEn());
        if (item.accepted() == null || item.accepted().isEmpty() || translation.isEmpty()) {
            return Result.rejected("incomplete WORD_ORDER");
        }
        List<List<String>> accepted = new ArrayList<>(item.accepted().stream()
                .map(GeneratedExercises::tokens)
                .distinct()
                .toList());
        List<String> tokens = accepted.getFirst();
        if (tokens.size() < 2) {
            return Result.rejected("WORD_ORDER needs at least two tokens");
        }
        List<String> sorted = tokens.stream().sorted().toList();
        if (accepted.stream().anyMatch(order -> !order.stream().sorted().toList().equals(sorted))) {
            return Result.rejected("WORD_ORDER orders use different tokens: " + accepted);
        }
        List<String> shuffled = new ArrayList<>(tokens);
        for (int i = 0; i < SHUFFLE_ATTEMPTS && accepted.contains(shuffled); i++) {
            Collections.shuffle(shuffled, random);
        }
        if (accepted.contains(shuffled)) {
            return Result.rejected("WORD_ORDER has no order that is wrong: " + String.join("", tokens));
        }
        return ok(new ExercisePrompt.WordOrder(shuffled, translation), new ExerciseAnswer.WordOrder(accepted),
                String.join("", tokens), List.of());
    }

    private static Result translation(GeneratedExercise.Translation item, int sequence) {
        String reference = trim(item.reference());
        boolean german = sequence % 2 == 0;
        String source = trim(german ? item.sourceDe() : item.sourceEn());
        if (reference.isEmpty() || source.isEmpty()) {
            return Result.rejected("incomplete TRANSLATION");
        }
        return ok(new ExercisePrompt.Translation(source, german ? "de" : "en", List.of()),
                new ExerciseAnswer.Translation(reference), reference, List.of());
    }

    /** All Chinese text the learner sees or writes, for the allowed-character check. */
    private static String chineseText(Built built) {
        return switch (built.prompt()) {
            case ExercisePrompt.FillBlank p -> built.sentence() + String.join("", p.options());
            default -> built.sentence();
        };
    }

    /** Tokens without blanks and punctuation-only entries, e.g. a separate "。" at the end. */
    private static List<String> tokens(List<String> order) {
        return trimmed(order).stream().filter(t -> !Punctuation.remove(t).isEmpty()).toList();
    }

    private static List<String> trimmed(Collection<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().filter(Objects::nonNull).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    private static String trim(String value) {
        return value == null ? "" : value.strip();
    }

    private static Result ok(ExercisePrompt prompt, ExerciseAnswer answer, String sentence, List<WordDto> words) {
        return new Result(new Built(prompt, answer, sentence, words), null);
    }
}
