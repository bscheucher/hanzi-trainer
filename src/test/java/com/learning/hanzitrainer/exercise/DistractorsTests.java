package com.learning.hanzitrainer.exercise;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DistractorsTests {

    @Test
    void takesEarlierTiersFirst() {
        List<String> picked = Distractors.pick("friend", 3,
                List.of(List.of("teacher"), List.of("student", "mother", "father")));

        assertThat(picked).containsExactly("teacher", "student", "mother");
    }

    @Test
    void skipsTheCorrectMeaningAndSharedSegments() {
        List<String> picked = Distractors.pick("to study; to learn", 3,
                List.of(List.of("To Study", "to learn", "learning, to learn", "friend", "Beijing", "is not")));

        assertThat(picked).containsExactly("friend", "Beijing", "is not");
    }

    @Test
    void optionTextsAreDistinct() {
        List<String> picked = Distractors.pick("friend", 3,
                List.of(List.of("teacher", "Teacher ", "student"), List.of("teacher", "mother")));

        assertThat(picked).containsExactly("teacher", "student", "mother");
    }

    @Test
    void returnsFewerWhenCandidatesRunOut() {
        assertThat(Distractors.pick("friend", 3, List.of(List.of("teacher"), List.of()))).containsExactly("teacher");
    }
}
