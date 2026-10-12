package com.learning.hanzitrainer.exercise;

import com.learning.hanzitrainer.cedict.CedictEntry;
import com.learning.hanzitrainer.cedict.CedictEntryRepository;
import com.learning.hanzitrainer.textbook.Lesson;
import com.learning.hanzitrainer.word.Word;
import com.learning.hanzitrainer.word.WordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Builds the exercise types that need no LLM (spec 07): {@code HANZI_TO_MEANING}, {@code TONE} and
 * {@code MEANING_TO_HANZI}.
 */
@Component
public class CodeExerciseGenerator {

    static final Set<ExerciseType> TYPES = Set.of(ExerciseType.HANZI_TO_MEANING, ExerciseType.TONE,
            ExerciseType.MEANING_TO_HANZI);
    static final String GENERATED_BY = "code";

    private static final int OPTIONS = 4;
    private static final int RANDOM_CANDIDATES = 50;
    /** CC-CEDICT glosses that make poor answer options. */
    private static final Pattern UNUSABLE_GLOSS = Pattern.compile(
            "(?i)^(surname |(old |erhua )?variant of|see |cl:|abbr\\. |used in |pr\\. |\\().*|.*\\[.*");

    private final WordRepository words;
    private final CedictEntryRepository cedict;
    private final ExerciseCodec codec;
    private final Random random;

    @Autowired
    public CodeExerciseGenerator(WordRepository words, CedictEntryRepository cedict, ExerciseCodec codec) {
        this(words, cedict, codec, new Random());
    }

    CodeExerciseGenerator(WordRepository words, CedictEntryRepository cedict, ExerciseCodec codec, Random random) {
        this.words = words;
        this.cedict = cedict;
        this.codec = codec;
        this.random = random;
    }

    /**
     * Whether {@code word} can be asked as {@code type} at all: TONE needs at least one toned syllable, writing needs
     * a word of Chinese characters only (Hanzi Writer has no data for Latin letters such as in AA制).
     */
    public boolean canGenerate(ExerciseType type, Word word) {
        return switch (type) {
            case HANZI_TO_MEANING -> hasMeaning(word);
            case TONE -> !ToneSyllables.of(word.getPinyinNumbered()).isEmpty();
            case MEANING_TO_HANZI -> hasMeaning(word) && !word.getHanzi().isEmpty() && word.getHanzi().codePoints()
                    .allMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN);
            default -> false;
        };
    }

    /** A new, unsaved exercise for {@code word}; empty if the word cannot be asked as {@code type}. */
    public Optional<Exercise> generate(ExerciseType type, Word word, Lesson lesson, List<Word> lessonWords) {
        if (!canGenerate(type, word)) {
            return Optional.empty();
        }
        return Optional.of(switch (type) {
            case HANZI_TO_MEANING -> hanziToMeaning(word, lesson, lessonWords);
            case TONE -> tone(word, lesson);
            case MEANING_TO_HANZI -> meaningToHanzi(word, lesson);
            default -> throw new IllegalArgumentException(type + " is not code-generated");
        });
    }

    /**
     * True if a stored exercise no longer matches its word, e.g. after the meaning was edited (M4);
     * such exercises are regenerated instead of served.
     */
    public boolean isStale(Exercise exercise, Word word) {
        return switch (codec.answer(exercise)) {
            case ExerciseAnswer.HanziToMeaning a -> !Objects.equals(a.correct(), word.getMeaningEn());
            case ExerciseAnswer.Tone a -> !a.tones().equals(ToneSyllables.of(word.getPinyinNumbered()).tones());
            case ExerciseAnswer.MeaningToHanzi a -> !codec.prompt(exercise).equals(meaningToHanziPrompt(word));
        };
    }

    private Exercise hanziToMeaning(Word word, Lesson lesson, List<Word> lessonWords) {
        String correct = word.getMeaningEn();
        List<Word> others = new ArrayList<>(lessonWords.stream().filter(w -> !w.getId().equals(word.getId())).toList());
        Collections.shuffle(others, random);
        List<Word> elsewhere = words.findRandomOthers(word.getId(), RANDOM_CANDIDATES).stream()
                .filter(w -> others.stream().noneMatch(o -> o.getId().equals(w.getId())))
                .toList();
        Predicate<Word> samePos = w -> word.getPartOfSpeech() != null
                && word.getPartOfSpeech().equalsIgnoreCase(Objects.requireNonNullElse(w.getPartOfSpeech(), ""));

        List<List<String>> tiers = List.of(
                meanings(others, samePos),
                meanings(others, w -> true),
                meanings(elsewhere, samePos),
                meanings(elsewhere, w -> true));
        List<String> distractors = Distractors.pick(correct, OPTIONS - 1, tiers);
        if (distractors.size() < OPTIONS - 1) {
            List<String> glosses = cedict.findRandomOfLength(word.getHanzi().codePointCount(0, word.getHanzi().length()),
                            word.getHanzi(), RANDOM_CANDIDATES).stream()
                    .map(CodeExerciseGenerator::usableGloss)
                    .flatMap(Optional::stream)
                    .toList();
            List<List<String>> withCedict = new ArrayList<>(tiers);
            withCedict.add(glosses);
            distractors = Distractors.pick(correct, OPTIONS - 1, withCedict);
        }

        List<String> options = new ArrayList<>(distractors);
        options.add(correct);
        Collections.shuffle(options, random);
        return new Exercise(lesson, ExerciseType.HANZI_TO_MEANING,
                codec.toJson(new ExercisePrompt.HanziToMeaning(word.getHanzi(), options)),
                codec.toJson(new ExerciseAnswer.HanziToMeaning(correct)), GENERATED_BY);
    }

    private Exercise tone(Word word, Lesson lesson) {
        ToneSyllables syllables = ToneSyllables.of(word.getPinyinNumbered());
        return new Exercise(lesson, ExerciseType.TONE,
                codec.toJson(new ExercisePrompt.Tone(word.getHanzi(), syllables.syllables())),
                codec.toJson(new ExerciseAnswer.Tone(syllables.tones())), GENERATED_BY);
    }

    private Exercise meaningToHanzi(Word word, Lesson lesson) {
        return new Exercise(lesson, ExerciseType.MEANING_TO_HANZI, codec.toJson(meaningToHanziPrompt(word)),
                codec.toJson(new ExerciseAnswer.MeaningToHanzi(word.getHanzi())), GENERATED_BY);
    }

    /** The prompt shows the meanings and pinyin, so it is stale as soon as one of them is edited. */
    private static ExercisePrompt.MeaningToHanzi meaningToHanziPrompt(Word word) {
        return new ExercisePrompt.MeaningToHanzi(word.getHanzi(), word.getMeaningEn(), word.getMeaningDe(),
                word.getPinyin(), word.getHanzi().codePointCount(0, word.getHanzi().length()));
    }

    private static boolean hasMeaning(Word word) {
        return word.getMeaningEn() != null && !word.getMeaningEn().isBlank();
    }

    private static List<String> meanings(List<Word> candidates, Predicate<Word> filter) {
        return candidates.stream().filter(filter).map(Word::getMeaningEn).toList();
    }

    static Optional<String> usableGloss(CedictEntry entry) {
        return entry.getDefinitions().stream()
                .map(String::strip)
                .filter(d -> !d.isEmpty() && !UNUSABLE_GLOSS.matcher(d).matches())
                .findFirst();
    }
}
