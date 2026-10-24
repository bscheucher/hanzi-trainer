package com.learning.hanzitrainer.review;

import com.learning.hanzitrainer.TestcontainersConfiguration;
import com.learning.hanzitrainer.cedict.CedictImporter;
import com.learning.hanzitrainer.common.BadRequestException;
import com.learning.hanzitrainer.common.ConflictException;
import com.learning.hanzitrainer.common.HanziProperties;
import com.learning.hanzitrainer.exercise.AnswerRequest;
import com.learning.hanzitrainer.exercise.AnswerResult;
import com.learning.hanzitrainer.exercise.CodeExerciseGenerator;
import com.learning.hanzitrainer.exercise.ExerciseGrader;
import com.learning.hanzitrainer.exercise.ExerciseRepository;
import com.learning.hanzitrainer.exercise.ExerciseType;
import com.learning.hanzitrainer.exercise.ExerciseWordRepository;
import com.learning.hanzitrainer.word.ConfirmItem;
import com.learning.hanzitrainer.word.ConfirmRequest;
import com.learning.hanzitrainer.word.ConfirmationService;
import com.learning.hanzitrainer.word.LessonWordRepository;
import com.learning.hanzitrainer.word.VocabularyFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Spec 09, "Review flow" and "Dashboard", against PostgreSQL with a clock the tests move. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ReviewServiceTests {

    /** 09:00 in Vienna (UTC+1 in January). */
    private static final Instant START = Instant.parse("2026-01-05T08:00:00Z");

    @Autowired
    private ReviewCardRepository cards;
    @Autowired
    private ReviewLogRepository logs;
    @Autowired
    private LessonWordRepository lessonWords;
    @Autowired
    private ExerciseRepository exercises;
    @Autowired
    private ExerciseWordRepository exerciseWords;
    @Autowired
    private CodeExerciseGenerator generator;
    @Autowired
    private ExerciseGrader grader;
    @Autowired
    private HanziProperties properties;
    @Autowired
    private ConfirmationService confirmation;
    @Autowired
    private CedictImporter importer;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JsonMapper json;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @TempDir
    Path dir;

    private VocabularyFixture fx;
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        fx = VocabularyFixture.create(jdbc, importer, dir);
        clock = new MutableClock(START);
    }

    @AfterEach
    void cleanUp() {
        VocabularyFixture.cleanUp(jdbc);
    }

    @Test
    void newWordsComeOneCardPerWordInTheOrderTheyWereAdded() {
        confirmThree();

        List<ReviewItemDto> due = service(15).due(30);

        assertThat(due).extracting(ReviewItemDto::wordId).containsExactly(word("朋友"), word("学习"), word("北京"));
        ReviewItemDto first = due.getFirst();
        assertThat(first.exercise().id()).isNull(); // built on the fly
        assertThat(first.exercise().wordIds()).containsExactly(first.wordId());
        assertThat(first.exercise().type()).isEqualTo(switch (first.cardType()) {
            case RECOGNITION -> ExerciseType.HANZI_TO_MEANING;
            case TONE -> ExerciseType.TONE;
            case WRITING -> ExerciseType.MEANING_TO_HANZI;
        });
        assertThat(first.previewIntervals()).containsEntry("1", "PT1M").containsEntry("2", "PT5M30S")
                .containsEntry("3", "PT10M");
        assertThat(first.previewIntervals().get("4")).matches("P\\d+D");
    }

    @Test
    void ratingSchedulesTheCardLogsItAndPausesTheWordsOtherCards() {
        confirmThree();
        ReviewService reviews = service(15);
        ReviewItemDto friend = reviews.due(30).getFirst();

        CardDto rated = reviews.rate(friend.cardId(), 3);

        assertThat(rated.state()).isEqualTo(CardState.LEARNING);
        assertThat(rated.due()).isEqualTo(START.plus(Duration.ofMinutes(10)));
        assertThat(rated.reps()).isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT rating, state_before, elapsed_days, scheduled_days FROM review_log"))
                .containsEntry("rating", 3).containsEntry("state_before", "NEW")
                .containsEntry("elapsed_days", 0).containsEntry("scheduled_days", 0);
        assertThat(reviews.due(30)).extracting(ReviewItemDto::wordId).doesNotContain(friend.wordId());

        clock.advance(Duration.ofMinutes(11));
        List<ReviewItemDto> later = reviews.due(30);
        assertThat(later.getFirst().cardId()).isEqualTo(friend.cardId()); // LEARNING before NEW
        assertThat(later).extracting(ReviewItemDto::wordId).doesNotHaveDuplicates();
    }

    @Test
    void theRatedCardFollowsItsOwnLearningStepWhileItsSiblingsWait() {
        confirm("朋友", "péngyou", "friend");
        ReviewService reviews = service(15);
        long again = reviews.due(30).getFirst().cardId();
        reviews.rate(again, 1); // Again: due in 1 minute

        clock.advance(Duration.ofMinutes(2));

        assertThat(reviews.due(30)).extracting(ReviewItemDto::cardId).containsExactly(again);
        clock.advance(Duration.ofMinutes(9)); // siblings free again after 10 minutes
        assertThat(reviews.due(30)).extracting(ReviewItemDto::cardId).hasSize(1); // still one card per word
    }

    @Test
    void theIntervalAPreviewShowsIsTheOneTheRatingSchedules() {
        confirmThree();
        ReviewService reviews = service(15);
        for (ReviewItemDto item : reviews.due(30)) {
            Duration easy = Duration.parse(item.previewIntervals().get("4"));

            CardDto rated = reviews.rate(item.cardId(), 4);

            assertThat(rated.due()).isEqualTo(clock.instant().plus(easy));
        }
    }

    @Test
    void aSecondRatingWithinFiveSecondsIs409() {
        confirmThree();
        ReviewService reviews = service(15);
        long card = reviews.due(30).getFirst().cardId();
        reviews.rate(card, 1);

        clock.advance(Duration.ofSeconds(4));
        assertThatThrownBy(() -> reviews.rate(card, 1)).isInstanceOf(ConflictException.class);
        clock.advance(Duration.ofSeconds(2));
        assertThat(reviews.rate(card, 3).reps()).isEqualTo(2);
    }

    @Test
    void newCardsAreLimitedPerLocalDay() {
        confirmThree();
        ReviewService reviews = service(2);
        List<ReviewItemDto> due = reviews.due(30);
        assertThat(due).hasSize(2);
        due.forEach(item -> reviews.rate(item.cardId(), 4)); // both leave for days

        clock.advance(Duration.ofMinutes(11));
        assertThat(reviews.due(30)).isEmpty();
        assertThat(reviews.stats().newCardsLeftToday()).isZero();

        clock.advance(Duration.ofHours(15)); // past midnight in Vienna
        assertThat(reviews.due(30)).hasSize(2); // a new day: two more new cards
    }

    @Test
    void answersAreGradedByCardTypeAgainstTheWord() {
        confirm("学习", "xuéxí", "to learn");
        ReviewService reviews = service(15);
        long wordId = word("学习");

        AnswerResult meaning = reviews.answer(card(wordId, CardType.RECOGNITION),
                new AnswerRequest(json.readTree("{\"selected\": \"to learn\"}"), 2_000L, null));
        AnswerResult tones = reviews.answer(card(wordId, CardType.TONE),
                new AnswerRequest(json.readTree("{\"tones\": [2, 3]}"), 2_000L, null));

        assertThat(meaning.correct()).isTrue();
        assertThat(meaning.suggestedRating()).isEqualTo(4);
        assertThat(tones.score()).isEqualTo(0.5);
        assertThat(tones.suggestedRating()).isEqualTo(2);
        assertThat(cards.findById(card(wordId, CardType.TONE)).orElseThrow().getState()).isEqualTo(CardState.NEW);
    }

    @Test
    void aStoredExerciseMustBeAFillBlankWithTheCardsWord() {
        confirm("学习", "xuéxí", "to learn");
        long exercise = jdbc.queryForObject("""
                INSERT INTO exercise (lesson_id, type, prompt, answer, sentence, generated_by)
                VALUES (?, 'FILL_BLANK', '{"sentence": "我___中文。", "translationEn": "x", "options": ["学习"]}',
                        '{"correct": "学习", "fullSentence": "我学习中文。"}', '我学习中文。', 'm') RETURNING id""",
                Long.class, fx.lesson1);
        long recognition = card(word("学习"), CardType.RECOGNITION);
        ReviewService reviews = service(15);

        assertThatThrownBy(() -> reviews.answer(recognition,
                new AnswerRequest(json.readTree("{\"selected\": \"学习\"}"), 1L, exercise)))
                .isInstanceOf(BadRequestException.class); // not linked to the word yet
        jdbc.update("INSERT INTO exercise_word (exercise_id, word_id) VALUES (?, ?)", exercise, word("学习"));

        assertThat(reviews.answer(recognition,
                new AnswerRequest(json.readTree("{\"selected\": \"学习\"}"), 1L, exercise)).correct()).isTrue();
        assertThatThrownBy(() -> reviews.answer(card(word("学习"), CardType.TONE),
                new AnswerRequest(json.readTree("{\"selected\": \"学习\"}"), 1L, exercise)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void cardsThatCannotBeAskedAreLeftOut() {
        confirm("学习", "xuéxí", "to learn");
        jdbc.update("UPDATE word SET meaning_en = NULL"); // no meaning: only the TONE card can be asked

        ReviewService reviews = service(15);

        assertThat(reviews.due(30)).singleElement().extracting(ReviewItemDto::cardType).isEqualTo(CardType.TONE);
        ReviewStatsDto stats = reviews.stats();
        assertThat(stats.dueNow()).isEqualTo(1);
        assertThat(stats.byState()).containsEntry(CardState.NEW, 3L); // counted per state all the same
    }

    @Test
    void statsCountDueCardsNewCardsAndTodaysReviews() {
        confirmThree();
        ReviewService reviews = service(4);

        ReviewStatsDto before = reviews.stats();
        assertThat(before.newCardsLeftToday()).isEqualTo(4);
        assertThat(before.dueNow()).isEqualTo(4); // 9 new cards, 4 allowed today
        assertThat(before.byType()).isEqualTo(Map.of(CardType.RECOGNITION, 3L, CardType.WRITING, 3L,
                CardType.TONE, 3L));

        reviews.rate(reviews.due(30).getFirst().cardId(), 3); // LEARNING, due in 10 minutes
        ReviewStatsDto after = reviews.stats();

        assertThat(after.reviewedToday()).isEqualTo(1);
        assertThat(after.newCardsLeftToday()).isEqualTo(3);
        assertThat(after.dueNow()).isEqualTo(3); // the learning card is not due yet
        assertThat(after.dueToday()).isEqualTo(4);
        assertThat(after.byState()).containsEntry(CardState.LEARNING, 1L).containsEntry(CardState.NEW, 8L);
    }

    @Test
    void forecastCountsScheduledCardsPerLocalDay() {
        confirmThree();
        ReviewService reviews = service(15);
        CardDto rated = reviews.rate(reviews.due(30).getFirst().cardId(), 4);

        List<ForecastDayDto> forecast = reviews.forecast(30);

        LocalDate today = LocalDate.of(2026, 1, 5);
        LocalDate dueDay = rated.due().atZone(ZoneId.of("Europe/Vienna")).toLocalDate();
        assertThat(forecast).hasSize(30).first().isEqualTo(new ForecastDayDto(today, 0));
        assertThat(forecast).filteredOn(d -> d.count() > 0).singleElement()
                .isEqualTo(new ForecastDayDto(dueDay, 1));
        assertThatThrownBy(() -> reviews.forecast(0)).isInstanceOf(BadRequestException.class);
    }

    private ReviewService service(int newPerDay) {
        HanziProperties limited = new HanziProperties(properties.cedict(), properties.storage(), properties.llm(),
                properties.extraction(), properties.exercises(),
                new HanziProperties.Review(newPerDay, ZoneId.of("Europe/Vienna")));
        ReviewService target = new ReviewService(cards, logs, lessonWords, exercises, exerciseWords, generator, grader,
                limited, clock, new Random(1));
        // Built by hand for the clock and the limit: give it the transactions the Spring bean has.
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor((TransactionManager) transactionManager,
                new AnnotationTransactionAttributeSource()));
        return (ReviewService) proxy.getProxy();
    }

    private void confirmThree() {
        confirm("朋友", "péngyou", "friend");
        confirm("学习", "xuéxí", "to learn");
        confirm("北京", "Běijīng", "Beijing");
        // cards are due when created: move them to the start of the test
        jdbc.update("UPDATE review_card SET due = ?", java.sql.Timestamp.from(START));
    }

    private void confirm(String hanzi, String pinyin, String meaningEn) {
        confirmation.confirm(fx.lesson1, new ConfirmRequest(null,
                List.of(new ConfirmItem(hanzi, pinyin, meaningEn, null, null, null, true))));
        jdbc.update("UPDATE review_card SET due = ?", java.sql.Timestamp.from(START));
    }

    private long word(String hanzi) {
        return jdbc.queryForObject("SELECT id FROM word WHERE hanzi = ?", Long.class, hanzi);
    }

    private long card(long wordId, CardType type) {
        return jdbc.queryForObject("SELECT id FROM review_card WHERE word_id = ? AND card_type = ?", Long.class,
                wordId, type.name());
    }
}
