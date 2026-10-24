package com.learning.hanzitrainer.exercise;

import com.learning.hanzitrainer.exercise.PracticeService.Slot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class PracticeOrderTests {

    @Test
    void separatesTheSameWordWhereAGreedyOrderWouldNot() {
        // "take the first different word" turns A A B B C C into A B A B C C
        assertThat(PracticeService.noSameWordInARow(slots(1L, 1L, 2L, 2L, 3L, 3L)))
                .extracting(PracticeOrderTests::word).containsExactly(1L, 2L, 3L, 1L, 2L, 3L);
    }

    @Test
    void putsTheMostFrequentWordFirstSoItCanBeSpreadOut() {
        assertThat(PracticeService.noSameWordInARow(slots(2L, 1L, 1L, 3L, 1L)))
                .extracting(PracticeOrderTests::word).containsExactly(1L, 2L, 1L, 3L, 1L);
    }

    @Test
    void repeatsAWordOnlyWhenNothingElseIsLeft() {
        assertThat(PracticeService.noSameWordInARow(slots(1L, 2L, 1L, 1L)))
                .extracting(PracticeOrderTests::word).containsExactly(1L, 2L, 1L, 1L);
    }

    @Test
    void aSentenceExerciseSeparatesEveryWordItPractises() {
        Slot sentence = new Slot(ExerciseType.FILL_BLANK, Set.of(1L, 2L), null, null);
        Slot one = new Slot(ExerciseType.TONE, Set.of(1L), null, null);
        Slot two = new Slot(ExerciseType.HANZI_TO_MEANING, Set.of(2L), null, null);
        Slot three = new Slot(ExerciseType.TONE, Set.of(3L), null, null);

        assertThat(PracticeService.noSameWordInARow(List.of(sentence, two, three, one)))
                .containsExactly(sentence, three, two, one);
    }

    @Test
    void interleavesTypesUntilTheSizeOrTheSlotsRunOut() {
        List<Slot> h = slots(ExerciseType.HANZI_TO_MEANING, 1L, 2L, 3L);
        List<Slot> t = slots(ExerciseType.TONE, 1L);

        assertThat(PracticeService.interleave(List.of(h, t), 10)).extracting(Slot::type).containsExactly(
                ExerciseType.HANZI_TO_MEANING, ExerciseType.TONE, ExerciseType.HANZI_TO_MEANING,
                ExerciseType.HANZI_TO_MEANING);
        assertThat(PracticeService.interleave(List.of(h, t), 2)).hasSize(2);
    }

    private static Long word(Slot slot) {
        return slot.wordIds().iterator().next();
    }

    private static List<Slot> slots(Long... wordIds) {
        return slots(ExerciseType.HANZI_TO_MEANING, wordIds);
    }

    private static List<Slot> slots(ExerciseType type, Long... wordIds) {
        return Stream.of(wordIds).map(id -> new Slot(type, Set.of(id), null, null)).toList();
    }
}
