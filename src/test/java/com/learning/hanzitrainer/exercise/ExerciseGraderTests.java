package com.learning.hanzitrainer.exercise;

import com.learning.hanzitrainer.common.BadRequestException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExerciseGraderTests {

    private final JsonMapper json = JsonMapper.builder().build();
    private final ExerciseCodec codec = new ExerciseCodec(json);
    private final ExerciseGrader grader = new ExerciseGrader(codec);

    private final Exercise hanziToMeaning = new Exercise(null, ExerciseType.HANZI_TO_MEANING,
            codec.toJson(new ExercisePrompt.HanziToMeaning("朋友", List.of("teacher", "friend", "mother", "Beijing"))),
            codec.toJson(new ExerciseAnswer.HanziToMeaning("friend")), "code");
    private final Exercise tone = new Exercise(null, ExerciseType.TONE,
            codec.toJson(new ExercisePrompt.Tone("朋友", List.of("peng", "you"))),
            codec.toJson(new ExerciseAnswer.Tone(List.of(2, 5))), "code");

    @Test
    void correctMeaningScoresOneAndIsEasyWhenFast() {
        AnswerResult result = grader.grade(hanziToMeaning, response("{\"selected\": \"friend\"}"), 2_000);

        assertThat(result.correct()).isTrue();
        assertThat(result.score()).isEqualTo(1);
        assertThat(result.feedback()).isNull();
        assertThat(result.suggestedRating()).isEqualTo(4);
        assertThat(result.expected().get("correct").asString()).isEqualTo("friend");
    }

    @Test
    void wrongMeaningExplainsTheWord() {
        AnswerResult result = grader.grade(hanziToMeaning, response("{\"selected\": \"teacher\"}"), 9_000);

        assertThat(result.correct()).isFalse();
        assertThat(result.score()).isZero();
        assertThat(result.feedback()).isEqualTo("朋友 means “friend”.");
        assertThat(result.suggestedRating()).isEqualTo(1);
    }

    @Test
    void tonesScoreTheShareOfCorrectSyllables() {
        AnswerResult result = grader.grade(tone, response("{\"tones\": [2, 3]}"), 3_000);

        assertThat(result.correct()).isFalse();
        assertThat(result.score()).isEqualTo(0.5);
        assertThat(result.feedback()).isEqualTo("you: neutral tone, not tone 3.");
        assertThat(result.suggestedRating()).isEqualTo(2);
    }

    @Test
    void allTonesRightIsCorrect() {
        AnswerResult result = grader.grade(tone, response("{\"tones\": [2, 5]}"), 6_000);

        assertThat(result.correct()).isTrue();
        assertThat(result.suggestedRating()).isEqualTo(3);
    }

    @Test
    void responsesOfTheWrongShapeAreRejected() {
        assertThatThrownBy(() -> grader.grade(hanziToMeaning, response("{\"tones\": [1]}"), 1))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> grader.grade(tone, response("{\"tones\": [2]}"), 1))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("2 number(s)");
        assertThatThrownBy(() -> grader.grade(tone, response("{\"tones\": [2, 6]}"), 1))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> grader.grade(tone, response("{\"tones\": [\"2\", 5]}"), 1))
                .isInstanceOf(BadRequestException.class);
    }

    private JsonNode response(String text) {
        return json.readTree(text);
    }
}
