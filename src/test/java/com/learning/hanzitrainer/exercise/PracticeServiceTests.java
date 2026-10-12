package com.learning.hanzitrainer.exercise;

import com.learning.hanzitrainer.TestcontainersConfiguration;
import com.learning.hanzitrainer.cedict.CedictImporter;
import com.learning.hanzitrainer.common.NotFoundException;
import com.learning.hanzitrainer.word.ConfirmItem;
import com.learning.hanzitrainer.word.ConfirmRequest;
import com.learning.hanzitrainer.word.ConfirmationService;
import com.learning.hanzitrainer.word.VocabularyFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Spec 07, "Practice session" and the code-generated types, against PostgreSQL. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PracticeServiceTests {

    @Autowired
    private PracticeService practice;
    @Autowired
    private ExerciseService exercises;
    @Autowired
    private ConfirmationService confirmation;
    @Autowired
    private CedictImporter importer;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JsonMapper json;

    @TempDir
    Path dir;

    private VocabularyFixture fx;

    @BeforeEach
    void setUp() {
        fx = VocabularyFixture.create(jdbc, importer, dir);
    }

    @AfterEach
    void cleanUp() {
        VocabularyFixture.cleanUp(jdbc);
    }

    @Test
    void sessionMixesAllCodeTypesAndUsesEachWordTypePairOnce() {
        confirmThreeWords();

        List<ExerciseDto> session = session(fx.lesson1, null, 15);

        assertThat(session).hasSize(9); // 3 words × 3 types, capped
        assertThat(session).extracting(ExerciseDto::type)
                .containsOnly(ExerciseType.HANZI_TO_MEANING, ExerciseType.TONE, ExerciseType.MEANING_TO_HANZI);
        Set<String> pairs = new HashSet<>();
        session.forEach(e -> pairs.add(e.type() + "/" + e.wordIds()));
        assertThat(pairs).hasSize(9);
    }

    @Test
    void theSameWordIsNeverAskedTwiceInARow() {
        confirmThreeWords();

        for (int run = 0; run < 10; run++) {
            List<ExerciseDto> session = session(fx.lesson1, null, 15);
            for (int i = 1; i < session.size(); i++) {
                assertThat(session.get(i).wordIds()).isNotEqualTo(session.get(i - 1).wordIds());
            }
        }
    }

    @Test
    void sizeLimitsTheSessionAndStoredExercisesAreReused() {
        confirmThreeWords();

        assertThat(session(fx.lesson1, null, 3)).hasSize(3);
        session(fx.lesson1, null, 15);
        int stored = count("exercise");
        session(fx.lesson1, null, 15);

        assertThat(stored).isEqualTo(9);
        assertThat(count("exercise")).isEqualTo(9);
        assertThat(jdbc.queryForList("SELECT DISTINCT generated_by FROM exercise", String.class)).containsExactly("code");
    }

    @Test
    void hanziToMeaningHasFourDistinctOptionsIncludingTheMeaning() {
        confirmThreeWords();

        ExerciseDto exercise = session(fx.lesson1, List.of(ExerciseType.HANZI_TO_MEANING), 15).stream()
                .filter(e -> e.prompt().get("hanzi").asString().equals("朋友")).findFirst().orElseThrow();

        List<String> options = options(exercise);
        assertThat(options).hasSize(4).doesNotHaveDuplicates().contains("friend");
        assertThat(exercise.prompt().has("correct")).as("answer not sent").isFalse();
    }

    @Test
    void distractorsFallBackToCedictWhenTheVocabularyIsTooSmall() {
        confirm(fx.lesson1, "学习", "xuéxí", "to study");

        ExerciseDto exercise = session(fx.lesson1, List.of(ExerciseType.HANZI_TO_MEANING), 15).getFirst();

        // the other two-character entries in the fixture dictionary: 朋友, 不是 (two readings), 北京
        assertThat(options(exercise)).hasSize(4).contains("to study")
                .isSubsetOf("to study", "friend", "no", "fault; blame", "Beijing");
    }

    @Test
    void toneAsksCitationTonesWithoutErhua() {
        confirm(fx.lesson1, "一点儿", "yìdiǎnr", "a little");

        ExerciseDto exercise = session(fx.lesson1, List.of(ExerciseType.TONE), 15).getFirst();

        assertThat(exercise.prompt().get("syllables").values().stream().map(n -> n.asString()))
                .containsExactly("yi", "dian");
        AnswerResult result = exercises.answer(exercise.id(), new AnswerRequest(json.readTree("{\"tones\": [1, 3]}"), 4_000L, null));
        assertThat(result.correct()).isTrue();
        assertThat(result.suggestedRating()).isEqualTo(4);
    }

    @Test
    void answeringGradesAgainstTheStoredAnswer() {
        confirmThreeWords();
        ExerciseDto exercise = session(fx.lesson1, List.of(ExerciseType.HANZI_TO_MEANING), 15).stream()
                .filter(e -> e.prompt().get("hanzi").asString().equals("北京")).findFirst().orElseThrow();
        String wrong = options(exercise).stream().filter(o -> !o.equals("Beijing")).findFirst().orElseThrow();

        AnswerResult right = exercises.answer(exercise.id(), answer("Beijing"));
        AnswerResult bad = exercises.answer(exercise.id(), answer(wrong));

        assertThat(right.correct()).isTrue();
        assertThat(bad.correct()).isFalse();
        assertThat(bad.feedback()).isEqualTo("北京 means “Beijing”.");
        assertThat(bad.expected().get("correct").asString()).isEqualTo("Beijing");
    }

    @Test
    void anEditedMeaningRegeneratesTheExercise() {
        confirmThreeWords();
        session(fx.lesson1, null, 15);
        jdbc.update("UPDATE word SET meaning_en = 'pal' WHERE hanzi = '朋友'");

        ExerciseDto exercise = session(fx.lesson1, List.of(ExerciseType.HANZI_TO_MEANING), 15).stream()
                .filter(e -> e.prompt().get("hanzi").asString().equals("朋友")).findFirst().orElseThrow();

        assertThat(options(exercise)).contains("pal").doesNotContain("friend");
        assertThat(count("exercise")).isEqualTo(9);
    }

    @Test
    void listReturnsStoredExercisesWithTheirWords() {
        confirmThreeWords();
        session(fx.lesson1, null, 15);

        List<ExerciseDto> tones = exercises.list(fx.lesson1, ExerciseType.TONE);

        assertThat(tones).hasSize(3).allSatisfy(e -> assertThat(e.wordIds()).hasSize(1));
        assertThat(exercises.list(fx.lesson1, null)).hasSize(9);
    }

    @Test
    void writingShowsMeaningsAndPinyinAndGradesStrokeMistakes() {
        confirmThreeWords();
        jdbc.update("UPDATE word SET meaning_de = 'Freund' WHERE hanzi = '朋友'");

        ExerciseDto exercise = session(fx.lesson1, List.of(ExerciseType.MEANING_TO_HANZI), 15).stream()
                .filter(e -> e.prompt().get("hanzi").asString().equals("朋友")).findFirst().orElseThrow();

        assertThat(exercise.prompt().get("meaningEn").asString()).isEqualTo("friend");
        assertThat(exercise.prompt().get("meaningDe").asString()).isEqualTo("Freund");
        assertThat(exercise.prompt().get("pinyin").asString()).isEqualTo("péngyou");
        assertThat(exercise.prompt().get("characterCount").asInt()).isEqualTo(2);
        AnswerResult result = exercises.answer(exercise.id(), new AnswerRequest(json.readTree(
                "{\"hanzi\": \"朋友\", \"strokeMistakes\": [0, 5], \"hintsUsed\": 0, \"skipped\": false}"), 9_000L, null));
        assertThat(result.score()).isEqualTo(0.5);
        assertThat(result.expected().get("hanzi").asString()).isEqualTo("朋友");
    }

    @Test
    void anEditedGermanMeaningRegeneratesTheWritingExercise() {
        confirmThreeWords();
        session(fx.lesson1, List.of(ExerciseType.MEANING_TO_HANZI), 15);
        jdbc.update("UPDATE word SET meaning_de = 'Kumpel' WHERE hanzi = '朋友'");

        ExerciseDto exercise = session(fx.lesson1, List.of(ExerciseType.MEANING_TO_HANZI), 15).stream()
                .filter(e -> e.prompt().get("hanzi").asString().equals("朋友")).findFirst().orElseThrow();

        assertThat(exercise.prompt().get("meaningDe").asString()).isEqualTo("Kumpel");
        assertThat(count("exercise")).isEqualTo(3);
    }

    @Test
    void wordsWithLatinLettersAreNotWritten() {
        confirm(fx.lesson1, "AA制", "AA zhì", "to split the bill");

        assertThat(session(fx.lesson1, List.of(ExerciseType.MEANING_TO_HANZI), 15)).isEmpty();
        assertThat(session(fx.lesson1, List.of(ExerciseType.HANZI_TO_MEANING), 15)).hasSize(1);
    }

    @Test
    void lessonWithoutWordsGivesAnEmptySessionAndUnknownLessonIs404() {
        assertThat(session(fx.lesson2, null, 15)).isEmpty();
        assertThatThrownBy(() -> session(-1L, null, 15)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void deletingAnExerciseRemovesIt() {
        confirmThreeWords();
        long id = session(fx.lesson1, null, 1).getFirst().id();

        exercises.delete(id);

        assertThat(count("exercise WHERE id = " + id)).isZero();
        assertThatThrownBy(() -> exercises.delete(id)).isInstanceOf(NotFoundException.class);
    }

    private void confirmThreeWords() {
        confirm(fx.lesson1, "朋友", "péngyou", "friend");
        confirm(fx.lesson1, "学习", "xuéxí", "to study");
        confirm(fx.lesson1, "北京", "Běijīng", "Beijing");
    }

    private void confirm(long lessonId, String hanzi, String pinyin, String meaningEn) {
        confirmation.confirm(lessonId, new ConfirmRequest(null,
                List.of(new ConfirmItem(hanzi, pinyin, meaningEn, null, null, null, false))));
    }

    private List<ExerciseDto> session(Long lessonId, List<ExerciseType> types, int size) {
        return practice.createSession(new PracticeSessionRequest(lessonId, types, size)).exercises();
    }

    private static List<String> options(ExerciseDto exercise) {
        return exercise.prompt().get("options").values().stream().map(n -> n.asString()).toList();
    }

    private AnswerRequest answer(String selected) {
        return new AnswerRequest(json.valueToTree(java.util.Map.of("selected", selected)), 3_000L, null);
    }

    private int count(String fromWhere) {
        return jdbc.queryForObject("SELECT count(*) FROM " + fromWhere, Integer.class);
    }
}
