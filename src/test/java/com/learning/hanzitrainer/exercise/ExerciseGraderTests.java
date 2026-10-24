package com.learning.hanzitrainer.exercise;

import com.learning.hanzitrainer.common.BadRequestException;
import com.learning.hanzitrainer.common.ServiceUnavailableException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExerciseGraderTests {

    private final JsonMapper json = JsonMapper.builder().build();
    private final ExerciseCodec codec = new ExerciseCodec(json);
    private final List<String> gradedAnswers = new ArrayList<>();
    private final ExerciseGrader grader = new ExerciseGrader(codec, (source, reference, answer) -> {
        gradedAnswers.add(answer);
        return new TranslationGrader.TranslationGrade(false, 0.6, "跟 works too, but 中文 needs 学.", "我跟朋友学中文。");
    });

    private final Exercise hanziToMeaning = new Exercise(null, ExerciseType.HANZI_TO_MEANING,
            codec.toJson(new ExercisePrompt.HanziToMeaning("朋友", List.of("teacher", "friend", "mother", "Beijing"))),
            codec.toJson(new ExerciseAnswer.HanziToMeaning("friend")), "code");
    private final Exercise tone = new Exercise(null, ExerciseType.TONE,
            codec.toJson(new ExercisePrompt.Tone("朋友", List.of("peng", "you"))),
            codec.toJson(new ExerciseAnswer.Tone(List.of(2, 5))), "code");
    private final Exercise writing = new Exercise(null, ExerciseType.MEANING_TO_HANZI,
            codec.toJson(new ExercisePrompt.MeaningToHanzi("朋友", "friend", "Freund", "péngyou", 2)),
            codec.toJson(new ExerciseAnswer.MeaningToHanzi("朋友")), "code");
    private final Exercise fillBlank = new Exercise(null, ExerciseType.FILL_BLANK,
            codec.toJson(new ExercisePrompt.FillBlank("我和我的___去学校。", "I go to school with my friend.",
                    List.of("老师", "朋友", "学生", "妈妈"))),
            codec.toJson(new ExerciseAnswer.FillBlank("朋友", "我和我的朋友去学校。")), "fake-model");
    private final Exercise wordOrder = new Exercise(null, ExerciseType.WORD_ORDER,
            codec.toJson(new ExercisePrompt.WordOrder(List.of("学校", "我", "去", "明天"), "I will go to school tomorrow.")),
            codec.toJson(new ExerciseAnswer.WordOrder(List.of(List.of("我", "明天", "去", "学校"),
                    List.of("明天", "我", "去", "学校")))), "fake-model");
    private final Exercise translation = new Exercise(null, ExerciseType.TRANSLATION,
            codec.toJson(new ExercisePrompt.Translation("Ich lerne mit meinem Freund Chinesisch.", "de",
                    List.of("朋友", "学习"))),
            codec.toJson(new ExerciseAnswer.Translation("我和我的朋友学习中文。")), "fake-model");

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
    void aSkipIgnoresTheOtherCounters() {
        AnswerResult result = grader.grade(writing, response(
                "{\"strokeMistakes\": [0], \"hintsUsed\": -1, \"skipped\": true}"), 2_000);

        assertThat(result.correct()).isFalse();
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
        assertThatThrownBy(() -> grader.grade(writing,
                response("{\"hanzi\": [\"朋\", \"友\"], \"strokeMistakes\": [0, 0], \"skipped\": false}"), 1))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("string");
    }

    @Test
    void fillBlankChecksTheSelectedOption() {
        AnswerResult right = grader.grade(fillBlank, response("{\"selected\": \"朋友\"}"), 3_000);
        AnswerResult wrong = grader.grade(fillBlank, response("{\"selected\": \"老师\"}"), 3_000);

        assertThat(right.correct()).isTrue();
        assertThat(right.suggestedRating()).isEqualTo(4);
        assertThat(wrong.correct()).isFalse();
        assertThat(wrong.feedback()).isEqualTo("The sentence is 我和我的朋友去学校。");
        assertThat(wrong.expected().get("correct").asString()).isEqualTo("朋友");
    }

    @Test
    void anyAcceptedWordOrderIsCorrect() {
        AnswerResult first = grader.grade(wordOrder, response("{\"order\": [\"我\", \"明天\", \"去\", \"学校\"]}"), 9_000);
        AnswerResult second = grader.grade(wordOrder, response("{\"order\": [\"明天\", \"我\", \"去\", \"学校\"]}"), 9_000);
        AnswerResult wrong = grader.grade(wordOrder, response("{\"order\": [\"我\", \"去\", \"明天\", \"学校\"]}"), 9_000);

        assertThat(first.correct()).isTrue();
        assertThat(first.suggestedRating()).isEqualTo(3);
        assertThat(second.correct()).isTrue();
        assertThat(wrong.correct()).isFalse();
        assertThat(wrong.score()).isZero();
        assertThat(wrong.feedback()).isEqualTo("A correct order: 我 明天 去 学校");
    }

    @Test
    void wordOrderMustUseEachTokenOnce() {
        assertThatThrownBy(() -> grader.grade(wordOrder, response("{\"order\": [\"我\", \"去\", \"学校\"]}"), 1))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("exactly once");
        assertThatThrownBy(() -> grader.grade(wordOrder,
                response("{\"order\": [\"我\", \"我\", \"去\", \"学校\"]}"), 1))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> grader.grade(wordOrder, response("{\"order\": \"我明天去学校\"}"), 1))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void translationsAreGradedByTheLlmWithTheCorrectionInExpected() {
        AnswerResult result = grader.grade(translation, response("{\"text\": \" 我跟朋友学中文 \"}"), 20_000);

        assertThat(gradedAnswers).containsExactly("我跟朋友学中文");
        assertThat(result.correct()).isFalse();
        assertThat(result.score()).isEqualTo(0.6);
        assertThat(result.feedback()).isEqualTo("跟 works too, but 中文 needs 学.");
        assertThat(result.suggestedRating()).isEqualTo(2);
        assertThat(result.expected().get("reference").asString()).isEqualTo("我和我的朋友学习中文。");
        assertThat(result.expected().get("corrected").asString()).isEqualTo("我跟朋友学中文。");
    }

    @Test
    void theReferenceItselfIsCorrectWithoutAskingTheLlm() {
        AnswerResult result = grader.grade(translation, response("{\"text\": \"我和我的朋友学习中文\"}"), 20_000);

        assertThat(gradedAnswers).isEmpty();
        assertThat(result.correct()).isTrue();
        assertThat(result.score()).isEqualTo(1);
    }

    @Test
    void fullWidthSpacesFromAnImeAreIgnoredToo() {
        AnswerResult result = grader.grade(translation, response("{\"text\": \"　我和我的朋友　学习中文　\"}"),
                20_000);

        assertThat(gradedAnswers).isEmpty();
        assertThat(result.correct()).isTrue();
        assertThat(result.expected().get("corrected").asString()).isEqualTo("我和我的朋友　学习中文");
    }

    @Test
    void translationNeedsTextAndAConfiguredLlm() {
        assertThatThrownBy(() -> grader.grade(translation, response("{\"text\": \"  \"}"), 1))
                .isInstanceOf(BadRequestException.class);
        ExerciseGrader unconfigured = new ExerciseGrader(codec, new UnavailableTranslationGrader("no key"));
        assertThatThrownBy(() -> unconfigured.grade(translation, response("{\"text\": \"我学中文\"}"), 1))
                .isInstanceOf(ServiceUnavailableException.class).hasMessage("no key");
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
