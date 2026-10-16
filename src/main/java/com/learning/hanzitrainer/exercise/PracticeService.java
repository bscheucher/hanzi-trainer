package com.learning.hanzitrainer.exercise;

import com.learning.hanzitrainer.common.HanziProperties;
import com.learning.hanzitrainer.common.NotFoundException;
import com.learning.hanzitrainer.textbook.Lesson;
import com.learning.hanzitrainer.textbook.LessonRepository;
import com.learning.hanzitrainer.word.LessonWordRepository;
import com.learning.hanzitrainer.word.Word;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * Practice sessions (spec 07, "Practice session"). Code-generated exercises are created on the fly when a word has
 * none of the requested type (or only a stale one); other types come from stored exercises only. Each word/type pair
 * is used at most once, so a short lesson gives a shorter session. Types are interleaved and the same word is never
 * asked twice in a row when that can be avoided.
 */
@Service
@Transactional
public class PracticeService {

    /** Default mix: the types the player supports so far. */
    static final List<ExerciseType> DEFAULT_TYPES = List.of(ExerciseType.HANZI_TO_MEANING, ExerciseType.TONE,
            ExerciseType.MEANING_TO_HANZI);

    private final LessonRepository lessons;
    private final LessonWordRepository lessonWords;
    private final ExerciseRepository exercises;
    private final ExerciseWordRepository exerciseWords;
    private final CodeExerciseGenerator generator;
    private final HanziProperties properties;
    private final Random random;

    @Autowired
    public PracticeService(LessonRepository lessons, LessonWordRepository lessonWords, ExerciseRepository exercises,
                           ExerciseWordRepository exerciseWords, CodeExerciseGenerator generator,
                           HanziProperties properties) {
        this(lessons, lessonWords, exercises, exerciseWords, generator, properties, new Random());
    }

    PracticeService(LessonRepository lessons, LessonWordRepository lessonWords, ExerciseRepository exercises,
                    ExerciseWordRepository exerciseWords, CodeExerciseGenerator generator, HanziProperties properties,
                    Random random) {
        this.lessons = lessons;
        this.lessonWords = lessonWords;
        this.exercises = exercises;
        this.exerciseWords = exerciseWords;
        this.generator = generator;
        this.properties = properties;
        this.random = random;
    }

    public PracticeSessionDto createSession(PracticeSessionRequest request) {
        Lesson lesson = lessons.findById(request.lessonId())
                .orElseThrow(() -> new NotFoundException("Lesson", request.lessonId()));
        List<ExerciseType> types = request.types() == null || request.types().isEmpty() ? DEFAULT_TYPES
                : List.copyOf(new LinkedHashSet<>(request.types().stream().filter(Objects::nonNull).toList()));
        int size = request.size() != null ? request.size() : properties.exercises().defaultSessionSize();
        List<Word> words = lessonWords.findWordsOfLesson(lesson.getId());

        List<List<Slot>> perType = new ArrayList<>();
        for (ExerciseType type : types) {
            List<Slot> slots = slots(lesson, type, words);
            Collections.shuffle(slots, random);
            perType.add(slots);
        }
        Collections.shuffle(perType, random);

        List<Slot> chosen = noSameWordInARow(interleave(perType, size));
        List<Exercise> session = chosen.stream().map(slot -> slot.exercise() != null ? slot.exercise()
                : storeNew(slot, lesson, words)).toList();
        return new PracticeSessionDto(ExerciseService.toDtos(session, exerciseWords));
    }

    /**
     * One slot per word/type pair: an existing valid exercise, or a word to generate one for. Stale exercises, and
     * all stored exercises of a word that can no longer be asked as {@code type}, are deleted.
     */
    private List<Slot> slots(Lesson lesson, ExerciseType type, List<Word> words) {
        Map<Long, List<ExerciseWord>> storedByWord = new HashMap<>();
        for (ExerciseWord ew : exerciseWords.findByLessonAndType(lesson.getId(), type)) {
            storedByWord.computeIfAbsent(ew.getWord().getId(), id -> new ArrayList<>()).add(ew);
        }
        List<Slot> slots = new ArrayList<>();
        if (!CodeExerciseGenerator.TYPES.contains(type)) {
            Map<Long, Slot> byExercise = new LinkedHashMap<>();
            storedByWord.forEach((wordId, list) -> list.forEach(ew -> byExercise.putIfAbsent(
                    ew.getExercise().getId(), new Slot(type, wordId, null, ew.getExercise()))));
            slots.addAll(byExercise.values());
            return slots;
        }
        for (Word word : words) {
            boolean askable = generator.canGenerate(type, word);
            Exercise valid = null;
            for (ExerciseWord stored : storedByWord.getOrDefault(word.getId(), List.of())) {
                if (!askable || generator.isStale(stored.getExercise(), word)) {
                    exerciseWords.delete(stored);
                    exercises.delete(stored.getExercise());
                } else if (valid == null) {
                    valid = stored.getExercise();
                }
            }
            if (askable) {
                slots.add(new Slot(type, word.getId(), word, valid));
            }
        }
        return slots;
    }

    private Exercise storeNew(Slot slot, Lesson lesson, List<Word> words) {
        Exercise exercise = generator.generate(slot.type(), slot.word(), lesson, words).orElseThrow();
        exercises.save(exercise);
        exerciseWords.save(new ExerciseWord(exercise, slot.word()));
        return exercise;
    }

    /** Takes one slot of each type in turn until {@code size} slots or all slots are taken. */
    static List<Slot> interleave(List<List<Slot>> perType, int size) {
        List<Slot> result = new ArrayList<>();
        for (int round = 0; result.size() < size; round++) {
            boolean any = false;
            for (List<Slot> slots : perType) {
                if (round < slots.size() && result.size() < size) {
                    result.add(slots.get(round));
                    any = true;
                }
            }
            if (!any) {
                break;
            }
        }
        return result;
    }

    /**
     * Reorders so that consecutive exercises practise different words whenever possible: each step takes the next
     * slot of the word with the most slots left, other than the previous word (ties keep the given order).
     */
    static List<Slot> noSameWordInARow(List<Slot> slots) {
        Map<Long, ArrayDeque<Slot>> byWord = new LinkedHashMap<>();
        for (Slot slot : slots) {
            byWord.computeIfAbsent(slot.wordId(), id -> new ArrayDeque<>()).add(slot);
        }
        List<Slot> ordered = new ArrayList<>();
        Long previous = null;
        while (ordered.size() < slots.size()) {
            Long pick = null;
            for (Map.Entry<Long, ArrayDeque<Slot>> e : byWord.entrySet()) {
                if (e.getValue().isEmpty() || e.getKey().equals(previous)) {
                    continue;
                }
                if (pick == null || e.getValue().size() > byWord.get(pick).size()) {
                    pick = e.getKey();
                }
            }
            if (pick == null) {
                pick = previous; // only the previous word is left
            }
            ordered.add(byWord.get(pick).poll());
            previous = pick;
        }
        return ordered;
    }

    /** @param word set when an exercise still has to be generated; {@code exercise} set when one is stored */
    record Slot(ExerciseType type, Long wordId, Word word, Exercise exercise) {
    }
}
