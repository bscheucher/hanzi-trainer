package com.learning.hanzitrainer.exercise;

import com.learning.hanzitrainer.TestcontainersConfiguration;
import com.learning.hanzitrainer.cedict.CedictImporter;
import com.learning.hanzitrainer.common.BadRequestException;
import com.learning.hanzitrainer.common.NotFoundException;
import com.learning.hanzitrainer.common.ServiceUnavailableException;
import com.learning.hanzitrainer.common.UnreadableReplyException;
import com.learning.hanzitrainer.textbook.LessonRepository;
import com.learning.hanzitrainer.word.ConfirmItem;
import com.learning.hanzitrainer.word.ConfirmRequest;
import com.learning.hanzitrainer.word.ConfirmationService;
import com.learning.hanzitrainer.word.LessonWordRepository;
import com.learning.hanzitrainer.word.VocabularyFixture;
import com.learning.hanzitrainer.word.WordDto;
import com.learning.hanzitrainer.word.WordRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.node.JsonNodeFactory;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/** Spec 07, "LLM generation" and "Allowed-character validation", with a fake generator against PostgreSQL. */
@SpringBootTest
@Import({TestcontainersConfiguration.class, FakeExerciseConfiguration.class})
class GenerationJobServiceTests {

    @Autowired
    private GenerationJobService jobs;
    @Autowired
    private ExerciseService exercises;
    @Autowired
    private PracticeService practice;
    @Autowired
    private FakeExerciseGenerator generator;
    @Autowired
    private ConfirmationService confirmation;
    @Autowired
    private CedictImporter importer;
    @Autowired
    private JdbcTemplate jdbc;

    @TempDir
    Path dir;

    private VocabularyFixture fx;

    @BeforeEach
    void setUp() {
        fx = VocabularyFixture.create(jdbc, importer, dir);
        confirm(fx.lesson1, "朋友", "péngyou", "friend");
        confirm(fx.lesson1, "学校", "xuéxiào", "school");
        confirm(fx.lesson1, "去", "qù", "to go");
        confirm(fx.lesson2, "老师", "lǎoshī", "teacher");
    }

    @AfterEach
    void cleanUp() {
        generator.reset();
        VocabularyFixture.cleanUp(jdbc);
    }

    @Test
    void storesAcceptedExercisesWithTheWordsTheyUse() {
        generator.willAnswer(call -> List.of(
                fillBlank("我和我的___去学校。", "朋友"),
                fillBlank("朋友___学校。", "去")));

        GenerationJobDto job = run(List.of(ExerciseType.FILL_BLANK), 2);

        assertThat(job.status()).isEqualTo(GenerationJobDto.Status.DONE);
        assertThat(job.created()).isEqualTo(2);
        assertThat(job.rejected()).isZero();
        List<ExerciseDto> stored = exercises.list(fx.lesson1, ExerciseType.FILL_BLANK);
        assertThat(stored).hasSize(2);
        assertThat(stored.getFirst().prompt().get("sentence").asString()).isEqualTo("我和我的___去学校。");
        assertThat(stored.getFirst().wordIds()).hasSize(2); // the two target words; both sentences use all three
        assertThat(jdbc.queryForList("SELECT DISTINCT generated_by FROM exercise", String.class))
                .containsExactly("fake-model");
    }

    @Test
    void theModelOnlyGetsWordsFromThisAndEarlierLessons() {
        run(List.of(ExerciseType.TRANSLATION), 1);

        FakeExerciseGenerator.Call call = generator.calls().getFirst();
        assertThat(call.type()).isEqualTo(ExerciseType.TRANSLATION);
        assertThat(call.allowedWords()).contains("朋友", "学校", "去", "的", "两").doesNotContain("老师");
        assertThat(call.targetWords()).hasSize(1).first().extracting(WordDto::hanzi).isIn("朋友", "学校", "去");
    }

    @Test
    void rejectedExercisesAreRegeneratedAtMostTwiceMore() {
        generator.willAnswer(call -> List.of(fillBlank("老师和___去学校。", "朋友")));

        GenerationJobDto job = run(List.of(ExerciseType.FILL_BLANK), 1);

        assertThat(job.status()).isEqualTo(GenerationJobDto.Status.DONE);
        assertThat(job.created()).isZero();
        assertThat(job.rejected()).isEqualTo(3);
        assertThat(generator.calls()).hasSize(1 + GenerationJobService.RETRIES);
    }

    @Test
    void aRetryAsksOnlyForTheMissingCountAndPrefersWordsWithoutExercises() {
        AtomicInteger round = new AtomicInteger();
        generator.willAnswer(call -> round.getAndIncrement() == 0
                ? List.of(fillBlank("我和我的___去学校。", "朋友"), fillBlank("我和我的___去学校。", "朋友"))
                : List.of(fillBlank("朋友___学校。", "去")));

        GenerationJobDto job = run(List.of(ExerciseType.FILL_BLANK), 2);

        assertThat(job.created()).isEqualTo(2);
        assertThat(job.rejected()).isEqualTo(1); // the duplicate
        assertThat(generator.calls()).extracting(FakeExerciseGenerator.Call::count).containsExactly(2, 1);
        // The first exercise practises the two first targets; the retry targets the third word, which has none.
        List<String> first = generator.calls().get(0).targetWords().stream().map(WordDto::hanzi).toList();
        assertThat(generator.calls().get(1).targetWords()).extracting(WordDto::hanzi)
                .hasSize(1).doesNotContainAnyElementsOf(first);
    }

    @Test
    void sentencesAlreadyStoredForTheLessonAreDuplicates() {
        generator.willAnswer(call -> List.of(fillBlank("我和我的___去学校。", "朋友")));
        run(List.of(ExerciseType.FILL_BLANK), 1);

        GenerationJobDto again = run(List.of(ExerciseType.FILL_BLANK), 1);

        assertThat(again.created()).isZero();
        assertThat(again.rejected()).isEqualTo(3);
    }

    @Test
    void aFailingModelFailsTheJobAndKeepsWhatWasStored() {
        generator.willAnswer(call -> {
            if (call.type() == ExerciseType.WORD_ORDER) {
                throw new IllegalStateException("model overloaded");
            }
            return List.of(fillBlank("我和我的___去学校。", "朋友"));
        });

        GenerationJobDto job = run(List.of(ExerciseType.FILL_BLANK, ExerciseType.WORD_ORDER), 1);

        assertThat(job.status()).isEqualTo(GenerationJobDto.Status.FAILED);
        assertThat(job.error()).isEqualTo("model overloaded");
        assertThat(job.created()).isEqualTo(1);
    }

    @Test
    void anUnreadableReplyCountsAsARejectionAndIsRetried() {
        AtomicInteger round = new AtomicInteger();
        generator.willAnswer(call -> {
            if (round.getAndIncrement() == 0) {
                throw new UnreadableReplyException("cut off at the token limit");
            }
            return List.of(fillBlank("我和我的___去学校。", "朋友"));
        });

        GenerationJobDto job = run(List.of(ExerciseType.FILL_BLANK), 1);

        assertThat(job.status()).isEqualTo(GenerationJobDto.Status.DONE);
        assertThat(job.created()).isEqualTo(1);
        assertThat(job.rejected()).isEqualTo(1);
        assertThat(generator.calls()).hasSize(2);
    }

    @Test
    void anErrorFailsTheJobInsteadOfLeavingItRunning() {
        generator.willAnswer(call -> {
            throw new StackOverflowError();
        });

        GenerationJobDto job = run(List.of(ExerciseType.FILL_BLANK), 1);

        assertThat(job.status()).isEqualTo(GenerationJobDto.Status.FAILED);
        assertThat(job.error()).isEqualTo("StackOverflowError");
    }

    @Test
    void aTranslationIsGradedWithoutHoldingATransaction(@Autowired FakeTranslationGrader grader) {
        generator.willAnswer(call -> List.of(new GeneratedExercise.Translation("Ich gehe zur Schule.",
                "I go to school.", "我去学校。")));
        run(List.of(ExerciseType.TRANSLATION), 1);
        long id = exercises.list(fx.lesson1, ExerciseType.TRANSLATION).getFirst().id();

        AnswerResult result = exercises.answer(id, new AnswerRequest(
                JsonNodeFactory.instance.objectNode().put("text", "我去学"), 5_000L, null));

        assertThat(result.score()).isEqualTo(0.5);
        assertThat(grader.calledInTransaction()).isFalse();
    }

    @Test
    void concurrentJobsForALessonDoNotStoreTheSameSentenceTwice() {
        generator.willAnswer(call -> {
            sleep(200); // without the per-lesson lock both jobs would read the stored sentences before either stores
            return List.of(fillBlank("我和我的___去学校。", "朋友"));
        });

        long first = jobs.start(fx.lesson1, request(ExerciseType.FILL_BLANK)).id();
        long second = jobs.start(fx.lesson1, request(ExerciseType.FILL_BLANK)).id();
        await().atMost(Duration.ofSeconds(10)).until(() -> jobs.get(first).status() != GenerationJobDto.Status.RUNNING
                && jobs.get(second).status() != GenerationJobDto.Status.RUNNING);

        assertThat(exercises.list(fx.lesson1, ExerciseType.FILL_BLANK)).hasSize(1);
        assertThat(jobs.get(first).created() + jobs.get(second).created()).isEqualTo(1);
    }

    @Test
    void finishedJobsAreForgottenAfterAnHour(@Autowired LessonRepository lessons,
                                             @Autowired LessonWordRepository lessonWords, @Autowired WordRepository words,
                                             @Autowired ExerciseRepository exerciseRepository,
                                             @Autowired ExerciseWordRepository exerciseWords,
                                             @Autowired ExerciseCodec codec, @Autowired TransactionTemplate tx) {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-24T10:00:00Z"));
        Clock clock = new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };
        GenerationJobService service = new GenerationJobService(lessons, lessonWords, words, exerciseRepository,
                exerciseWords, codec, generator, tx, Executors.newVirtualThreadPerTaskExecutor(), new Random(1),
                clock);
        long old = service.start(fx.lesson1, request(ExerciseType.FILL_BLANK)).id();
        await().atMost(Duration.ofSeconds(10))
                .until(() -> service.get(old).status() != GenerationJobDto.Status.RUNNING);

        now.set(now.get().plus(GenerationJobService.KEEP_FINISHED).plusSeconds(1));
        long recent = service.start(fx.lesson1, request(ExerciseType.FILL_BLANK)).id();

        assertThatThrownBy(() -> service.get(old)).isInstanceOf(NotFoundException.class);
        assertThat(service.get(recent)).isNotNull();
        service.shutdown();
    }

    @Test
    void aSessionTakesOneStoredSentenceExercisePerWord() {
        generator.willAnswer(call -> List.of(fillBlank("我和我的___去学校。", "朋友"), fillBlank("___去学校。", "朋友"),
                fillBlank("我和朋友___。", "学校")));
        run(List.of(ExerciseType.FILL_BLANK), 3);

        for (int i = 0; i < 10; i++) {
            List<ExerciseDto> session = practice.createSession(
                    new PracticeSessionRequest(fx.lesson1, List.of(ExerciseType.FILL_BLANK), 15)).exercises();
            assertThat(session).hasSize(1); // every exercise practises 朋友
        }
    }

    @Test
    void startIsValidated() {
        long empty = jdbc.queryForObject("INSERT INTO lesson (textbook_id, lesson_no) VALUES (?, 3) RETURNING id",
                Long.class, fx.textbookId);

        assertThatThrownBy(() -> jobs.start(-1, request(ExerciseType.FILL_BLANK))).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> jobs.start(fx.lesson1, request(ExerciseType.TONE)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("TONE");
        assertThatThrownBy(() -> jobs.start(empty, request(ExerciseType.FILL_BLANK)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("no words");
        assertThatThrownBy(() -> jobs.get(999_999)).isInstanceOf(NotFoundException.class);
        assertThat(generator.calls()).isEmpty();
    }

    @Test
    void anUnconfiguredLlmIs503(@Autowired LessonRepository lessons, @Autowired LessonWordRepository lessonWords,
                                @Autowired WordRepository words, @Autowired ExerciseRepository exerciseRepository,
                                @Autowired ExerciseWordRepository exerciseWords, @Autowired ExerciseCodec codec,
                                @Autowired TransactionTemplate tx) {
        GenerationJobService unconfigured = new GenerationJobService(lessons, lessonWords, words, exerciseRepository,
                exerciseWords, codec, new UnavailableExerciseGenerator("needs ANTHROPIC_API_KEY"), tx);

        assertThatThrownBy(() -> unconfigured.start(fx.lesson1, request(ExerciseType.FILL_BLANK)))
                .isInstanceOf(ServiceUnavailableException.class).hasMessage("needs ANTHROPIC_API_KEY");
    }

    private GenerationJobDto run(List<ExerciseType> types, int count) {
        long id = jobs.start(fx.lesson1, new GenerateExercisesRequest(types, count)).id();
        await().atMost(Duration.ofSeconds(10))
                .until(() -> jobs.get(id).status() != GenerationJobDto.Status.RUNNING);
        return jobs.get(id);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static GenerateExercisesRequest request(ExerciseType type) {
        return new GenerateExercisesRequest(List.of(type), 1);
    }

    /** Distractors come from the basic words, which every lesson allows. */
    private static GeneratedExercise fillBlank(String sentence, String correct) {
        return new GeneratedExercise.FillBlank(sentence, "translation", correct, List.of("我", "你", "他"));
    }

    private void confirm(long lessonId, String hanzi, String pinyin, String meaningEn) {
        confirmation.confirm(lessonId, new ConfirmRequest(null,
                List.of(new ConfirmItem(hanzi, pinyin, meaningEn, null, null, null, false))));
    }
}
