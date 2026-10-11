package com.learning.hanzitrainer.exercise;

import com.learning.hanzitrainer.exercise.PracticeService.Slot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class PracticeOrderTests {

    @Test
    void separatesTheSameWordWhereAGreedyOrderWouldNot() {
        // "take the first different word" turns A A B B C C into A B A B C C
        assertThat(PracticeService.noSameWordInARow(slots(1L, 1L, 2L, 2L, 3L, 3L)))
                .extracting(Slot::wordId).containsExactly(1L, 2L, 3L, 1L, 2L, 3L);
    }

    @Test
    void putsTheMostFrequentWordFirstSoItCanBeSpreadOut() {
        assertThat(PracticeService.noSameWordInARow(slots(2L, 1L, 1L, 3L, 1L)))
                .extracting(Slot::wordId).containsExactly(1L, 2L, 1L, 3L, 1L);
    }

    @Test
    void repeatsAWordOnlyWhenNothingElseIsLeft() {
        assertThat(PracticeService.noSameWordInARow(slots(1L, 2L, 1L, 1L)))
                .extracting(Slot::wordId).containsExactly(1L, 2L, 1L, 1L);
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

    private static List<Slot> slots(Long... wordIds) {
        return slots(ExerciseType.HANZI_TO_MEANING, wordIds);
    }

    private static List<Slot> slots(ExerciseType type, Long... wordIds) {
        return Stream.of(wordIds).map(id -> new Slot(type, id, null, null)).toList();
    }
}
