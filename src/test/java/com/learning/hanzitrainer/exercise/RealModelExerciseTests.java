package com.learning.hanzitrainer.exercise;

import com.learning.hanzitrainer.TestcontainersConfiguration;
import com.learning.hanzitrainer.word.WordDto;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Generates sentence exercises and grades a translation with the configured provider and exercise model (spec 07).
 * Costs money with Anthropic. Run with {@code HANZI_LLM_TEST=true ./gradlew llmTest}; never part of the build.
 */
@Tag("llm")
@EnabledIfEnvironmentVariable(named = "HANZI_LLM_TEST", matches = "true")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RealModelExerciseTests {

    private static final List<String> VOCABULARY = List.of("朋友", "老师", "学生", "学校", "去", "学习", "中文",
            "明天", "今天", "妈妈", "爸爸", "喜欢", "吃", "米饭", "看", "书");
    private static final List<WordDto> TARGETS = List.of(
            new WordDto(1L, "朋友", "péngyou", "peng2 you5", "friend", "Freund", "n.", true),
            new WordDto(2L, "学校", "xuéxiào", "xue2 xiao4", "school", "Schule", "n.", true),
            new WordDto(3L, "喜欢", "xǐhuan", "xi3 huan5", "to like", "mögen", "v.", true));

    @Autowired
    private ExerciseGenerator generator;

    @Autowired
    private TranslationGrader grader;

    @Test
    void mostGeneratedExercisesPassTheChecks() {
        assertThat(generator.unavailableReason()).as("LLM configured").isEmpty();
        AllowedWords allowed = new AllowedWords(VOCABULARY);

        for (ExerciseType type : GenerationJobService.LLM_TYPES) {
            List<GeneratedExercise> items = generator.generate(type, TARGETS, allowed.words(), 3);
            List<GeneratedExercises.Built> accepted = new ArrayList<>();
            System.out.printf("%s via %s:%n", type, generator.modelName());
            for (int i = 0; i < items.size(); i++) {
                GeneratedExercises.Result result = GeneratedExercises.check(type, items.get(i), TARGETS, allowed,
                        new HashSet<>(), i, new Random());
                System.out.println("  " + (result.built() != null ? result.built().prompt() : "REJECTED "
                        + result.rejection()));
                if (result.built() != null) {
                    accepted.add(result.built());
                }
            }
            assertThat(accepted).as(type + " exercises passing the checks").hasSizeGreaterThanOrEqualTo(2);
        }
    }

    @Test
    void gradesATranslationWithFeedback() {
        assertThat(grader.unavailableReason()).as("LLM configured").isEmpty();

        TranslationGrader.TranslationGrade right = grader.grade("Ich lerne mit meinem Freund Chinesisch.",
                "我和我的朋友学习中文。", "我跟朋友学中文。");
        TranslationGrader.TranslationGrade wrong = grader.grade("Ich lerne mit meinem Freund Chinesisch.",
                "我和我的朋友学习中文。", "我朋友去学校。");

        System.out.println("right: " + right);
        System.out.println("wrong: " + wrong);
        assertThat(right.correct()).isTrue();
        assertThat(right.score()).isGreaterThanOrEqualTo(0.8);
        assertThat(wrong.correct()).isFalse();
        assertThat(wrong.score()).isLessThan(0.5);
        assertThat(wrong.feedback()).isNotBlank();
    }
}
