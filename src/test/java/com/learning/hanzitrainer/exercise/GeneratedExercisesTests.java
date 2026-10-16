package com.learning.hanzitrainer.exercise;

import com.learning.hanzitrainer.exercise.GeneratedExercises.Result;
import com.learning.hanzitrainer.word.WordDto;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GeneratedExercisesTests {

    private static final WordDto FRIEND = word(1, "朋友", "péngyou", "friend");
    private static final WordDto SCHOOL = word(2, "学校", "xuéxiào", "school");
    private static final WordDto GO = word(3, "去", "qù", "to go");

    private final AllowedWords allowed = new AllowedWords(List.of("朋友", "学校", "去", "老师", "学生", "妈妈", "明天"));
    private final List<WordDto> targets = List.of(FRIEND, SCHOOL);
    private final Set<String> existing = new HashSet<>();

    @Test
    void fillBlankBecomesShuffledOptionsWithTheFullSentenceAsAnswer() {
        Result result = check(ExerciseType.FILL_BLANK, new GeneratedExercise.FillBlank("我和我的____去学校。",
                "I go to school with my friend.", "朋友", List.of("老师", "学生", "妈妈")), 0);

        ExercisePrompt.FillBlank prompt = (ExercisePrompt.FillBlank) result.built().prompt();
        assertThat(prompt.sentence()).isEqualTo("我和我的___去学校。");
        assertThat(prompt.options()).containsExactlyInAnyOrder("朋友", "老师", "学生", "妈妈");
        assertThat(result.built().answer()).isEqualTo(new ExerciseAnswer.FillBlank("朋友", "我和我的朋友去学校。"));
        assertThat(result.built().words()).containsExactly(FRIEND, SCHOOL);
    }

    @Test
    void fillBlankNeedsOneBlankAndThreeDistinctWrongOptions() {
        assertThat(check(ExerciseType.FILL_BLANK, new GeneratedExercise.FillBlank("我和___去___。", "x", "朋友",
                List.of("老师", "学生", "妈妈")), 0).rejection()).contains("exactly one blank, not 2");
        assertThat(check(ExerciseType.FILL_BLANK, new GeneratedExercise.FillBlank("我和朋友去学校。", "x", "朋友",
                List.of("老师", "学生", "妈妈")), 0).rejection()).contains("not 0");
        assertThat(check(ExerciseType.FILL_BLANK, new GeneratedExercise.FillBlank("我和我的___去学校。", "x", "朋友",
                List.of("老师", "朋友", " 老师 ")), 0).rejection()).contains("3 distinct wrong options");
    }

    @Test
    void optionsAreCheckedForUnknownCharactersToo() {
        Result result = check(ExerciseType.FILL_BLANK, new GeneratedExercise.FillBlank("我和我的___去学校。", "x",
                "朋友", List.of("老师", "学生", "爸爸")), 0);

        assertThat(result.rejection()).isEqualTo("uses characters outside the vocabulary: 爸");
    }

    @Test
    void wordOrderDropsPunctuationTokensAndNeverShowsAnAcceptedOrder() {
        for (int seed = 0; seed < 20; seed++) {
            Result result = GeneratedExercises.check(ExerciseType.WORD_ORDER, new GeneratedExercise.WordOrder(
                    List.of(List.of("我", "明天", "去", "学校", "。"), List.of("明天", "我", "去", "学校")),
                    "I will go to school tomorrow."), targets, allowed, existing, 0, new Random(seed));

            ExercisePrompt.WordOrder prompt = (ExercisePrompt.WordOrder) result.built().prompt();
            ExerciseAnswer.WordOrder answer = (ExerciseAnswer.WordOrder) result.built().answer();
            assertThat(answer.accepted()).containsExactly(List.of("我", "明天", "去", "学校"),
                    List.of("明天", "我", "去", "学校"));
            assertThat(prompt.tokens()).containsExactlyInAnyOrder("我", "明天", "去", "学校");
            assertThat(answer.accepted()).doesNotContain(prompt.tokens());
            assertThat(result.built().sentence()).isEqualTo("我明天去学校");
        }
    }

    @Test
    void wordOrdersMustUseTheSameTokensAndHaveAWrongOrder() {
        assertThat(check(ExerciseType.WORD_ORDER, new GeneratedExercise.WordOrder(
                List.of(List.of("我", "去", "学校"), List.of("我", "明天", "去", "学校")), "x"), 0).rejection())
                .contains("different tokens");
        assertThat(check(ExerciseType.WORD_ORDER, new GeneratedExercise.WordOrder(
                List.of(List.of("朋友", "学校"), List.of("学校", "朋友")), "x"), 0).rejection())
                .contains("no order that is wrong");
        assertThat(check(ExerciseType.WORD_ORDER, new GeneratedExercise.WordOrder(
                List.of(List.of("朋友")), "x"), 0).rejection()).contains("at least two tokens");
    }

    @Test
    void translationAlternatesTheSourceLanguageAndHintsTheTargetWordsItUses() {
        GeneratedExercise.Translation item = new GeneratedExercise.Translation(
                "Ich gehe mit meinem Freund zur Schule.", "I go to school with my friend.", "我和我的朋友去学校。");

        ExercisePrompt.Translation german = (ExercisePrompt.Translation) check(ExerciseType.TRANSLATION, item, 4)
                .built().prompt();
        ExercisePrompt.Translation english = (ExercisePrompt.Translation) check(ExerciseType.TRANSLATION, item, 5)
                .built().prompt();

        assertThat(german).isEqualTo(new ExercisePrompt.Translation("Ich gehe mit meinem Freund zur Schule.", "de",
                List.of("朋友", "学校")));
        assertThat(english.sourceLang()).isEqualTo("en");
        assertThat(english.source()).isEqualTo("I go to school with my friend.");
    }

    @Test
    void rejectsDuplicatesSentencesWithoutTargetWordsAndTheWrongType() {
        existing.add("我和我的朋友去学校。");
        GeneratedExercise.Translation item = new GeneratedExercise.Translation("de", "en", "我和我的朋友去学校。");

        assertThat(check(ExerciseType.TRANSLATION, item, 0).rejection()).startsWith("duplicate sentence");
        assertThat(check(ExerciseType.TRANSLATION, new GeneratedExercise.Translation("de", "en", "老师去。"), 0)
                .rejection()).startsWith("uses no target word");
        assertThat(check(ExerciseType.FILL_BLANK, item, 0).rejection()).isEqualTo("not a FILL_BLANK exercise");
        assertThat(check(ExerciseType.TRANSLATION, new GeneratedExercise.Translation(null, "en", "朋友去。"), 0)
                .rejection()).isEqualTo("incomplete TRANSLATION");
    }

    @Test
    void sentenceOfStoredAnswersMatchesTheBuiltSentence() {
        assertThat(GeneratedExercises.sentenceOf(new ExerciseAnswer.WordOrder(
                List.of(List.of("我", "去", "学校"))))).isEqualTo("我去学校");
        assertThat(GeneratedExercises.sentenceOf(new ExerciseAnswer.FillBlank("朋友", "我的朋友。")))
                .isEqualTo("我的朋友。");
    }

    private Result check(ExerciseType type, GeneratedExercise item, int sequence) {
        return GeneratedExercises.check(type, item, targets, allowed, existing, sequence, new Random(1));
    }

    private static WordDto word(long id, String hanzi, String pinyin, String meaning) {
        return new WordDto(id, hanzi, pinyin, null, meaning, null, null, true);
    }
}
