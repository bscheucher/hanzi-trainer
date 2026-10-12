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
    private final Exercise writing = new Exercise(null, ExerciseType.MEANING_TO_HANZI,
            codec.toJson(new ExercisePrompt.MeaningToHanzi("朋友", "friend", "Freund", "péngyou", 2)),
            codec.toJson(new ExerciseAnswer.MeaningToHanzi("朋友")), "code");

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
    void handwritingFailsACharacterWithMoreThanThreeMistakes() {
        AnswerResult result = grader.grade(writing, response(
                "{\"hanzi\": \"朋友\", \"strokeMistakes\": [3, 4], \"hintsUsed\": 0, \"skipped\": false}"), 6_000);

        assertThat(result.correct()).isFalse();
        assertThat(result.score()).isEqualTo(0.5);
        assertThat(result.feedback()).startsWith("友 needed the outline");
        assertThat(result.suggestedRating()).isEqualTo(2);
        assertThat(result.expected().get("hanzi").asString()).isEqualTo("朋友");
    }

    @Test
    void handwritingWithoutMistakesIsCorrectAndHintsMakeItHard() {
        AnswerResult clean = grader.grade(writing, response(
                "{\"hanzi\": \"朋友\", \"strokeMistakes\": [0, 0], \"hintsUsed\": 0, \"skipped\": false}"), 7_000);
        AnswerResult hinted = grader.grade(writing, response(
                "{\"hanzi\": \"朋友\", \"strokeMistakes\": [1, 0], \"hintsUsed\": 1, \"skipped\": false}"), 7_000);

        assertThat(clean.correct()).isTrue();
        assertThat(clean.feedback()).isNull();
        assertThat(clean.suggestedRating()).isEqualTo(4);
        assertThat(hinted.correct()).isTrue();
        assertThat(hinted.suggestedRating()).isEqualTo(2);
    }

    @Test
    void skippedHandwritingIsWrong() {
        AnswerResult result = grader.grade(writing, response(
                "{\"hanzi\": \"朋友\", \"strokeMistakes\": [], \"hintsUsed\": 0, \"skipped\": true}"), 2_000);

        assertThat(result.correct()).isFalse();
        assertThat(result.score()).isZero();
        assertThat(result.feedback()).isEqualTo("Skipped: 朋友 (péngyou).");
        assertThat(result.suggestedRating()).isEqualTo(1);
    }

    @Test
    void handwritingResponsesOfTheWrongShapeAreRejected() {
        assertThatThrownBy(() -> grader.grade(writing, response("{\"strokeMistakes\": [0, 0]}"), 1))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("skipped");
        assertThatThrownBy(() -> grader.grade(writing, response("{\"strokeMistakes\": [0], \"skipped\": false}"), 1))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("2 number(s)");
        assertThatThrownBy(() -> grader.grade(writing, response("{\"strokeMistakes\": [0, -1], \"skipped\": false}"), 1))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> grader.grade(writing,
                response("{\"hanzi\": \"老师\", \"strokeMistakes\": [0, 0], \"skipped\": false}"), 1))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("老师");
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
