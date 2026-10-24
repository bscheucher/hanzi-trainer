package com.learning.hanzitrainer.review;

import com.learning.hanzitrainer.common.BadRequestException;
import com.learning.hanzitrainer.common.ConflictException;
import com.learning.hanzitrainer.common.HanziProperties;
import com.learning.hanzitrainer.common.NotFoundException;
import com.learning.hanzitrainer.exercise.AnswerRequest;
import com.learning.hanzitrainer.exercise.AnswerResult;
import com.learning.hanzitrainer.exercise.CodeExerciseGenerator;
import com.learning.hanzitrainer.exercise.Exercise;
import com.learning.hanzitrainer.exercise.ExerciseDto;
import com.learning.hanzitrainer.exercise.ExerciseGrader;
import com.learning.hanzitrainer.exercise.ExerciseRepository;
import com.learning.hanzitrainer.exercise.ExerciseService;
import com.learning.hanzitrainer.exercise.ExerciseType;
import com.learning.hanzitrainer.exercise.ExerciseWordRepository;
import com.learning.hanzitrainer.textbook.Lesson;
import com.learning.hanzitrainer.word.LessonWord;
import com.learning.hanzitrainer.word.LessonWordRepository;
import com.learning.hanzitrainer.word.Word;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * Spaced-repetition reviews across all lessons (spec 09): the due queue with an exercise per card, grading, rating
 * with FSRS, and the dashboard.
 *
 * <p>A card that cannot be asked is left out of the queue and of the due counts, as practice sessions do: TONE for a
 * word without Chinese syllables, RECOGNITION without an English meaning (unless a stored FILL_BLANK practises the
 * word), WRITING without a meaning or with characters other than hanzi. It stays NEW until the word is fixed.
 *
 * <p>The interval fuzz is seeded from the card id and its review count, so the interval a preview shows is the one
 * the rating schedules.
 */
@Service
@Transactional
public class ReviewService {

    static final int MAX_LIMIT = 100;
    static final int MAX_FORECAST_DAYS = 365;
    /** A word's other cards wait this long after one of its cards was rated. */
    static final Duration SIBLING_PAUSE = Duration.ofMinutes(10);
    /** A second rating of the same card within this time is a 409. */
    static final Duration RATE_GUARD = Duration.ofSeconds(5);

    private final ReviewCardRepository cards;
    private final ReviewLogRepository logs;
    private final LessonWordRepository lessonWords;
    private final ExerciseRepository exercises;
    private final ExerciseWordRepository exerciseWords;
    private final CodeExerciseGenerator generator;
    private final ExerciseGrader grader;
    private final HanziProperties.Review settings;
    private final FsrsScheduler scheduler;
    private final Clock clock;
    private final Random random;

    @Autowired
    public ReviewService(ReviewCardRepository cards, ReviewLogRepository logs, LessonWordRepository lessonWords,
                         ExerciseRepository exercises, ExerciseWordRepository exerciseWords,
                         CodeExerciseGenerator generator, ExerciseGrader grader, HanziProperties properties) {
        this(cards, logs, lessonWords, exercises, exerciseWords, generator, grader, properties, Clock.systemUTC(),
                new Random());
    }

    ReviewService(ReviewCardRepository cards, ReviewLogRepository logs, LessonWordRepository lessonWords,
                  ExerciseRepository exercises, ExerciseWordRepository exerciseWords, CodeExerciseGenerator generator,
                  ExerciseGrader grader, HanziProperties properties, Clock clock, Random random) {
        this.cards = cards;
        this.logs = logs;
        this.lessonWords = lessonWords;
        this.exercises = exercises;
        this.exerciseWords = exerciseWords;
        this.generator = generator;
        this.grader = grader;
        this.settings = properties.review();
        this.scheduler = new FsrsScheduler(true, random);
        this.clock = clock;
        this.random = random;
    }

    /**
     * Cards due now: LEARNING / RELEARNING first, then REVIEW (each by due), then NEW (by word creation, lesson), at
     * most one per word, none whose sibling (another card of the word) was rated in the last {@link #SIBLING_PAUSE},
     * NEW ones up to today's limit. The rated card itself follows its own due (learning steps of 1 and 10 min).
     */
    @Transactional(readOnly = true)
    public List<ReviewItemDto> due(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new BadRequestException("limit must be 1.." + MAX_LIMIT);
        }
        Instant now = clock.instant();
        Set<Long> fillBlankWords = exerciseWords.findWordIdsWithType(ExerciseType.FILL_BLANK);
        Map<Long, Set<Long>> ratedByWord = new HashMap<>();
        logs.findRatedSince(now.minus(SIBLING_PAUSE))
                .forEach(r -> ratedByWord.computeIfAbsent(r.wordId(), w -> new HashSet<>()).add(r.cardId()));
        long newLeft = newAllowance(startOfDay(now));
        Map<Long, Integer> lessonNos = firstLessonNos();

        List<ReviewCard> candidates = cards.findAllWithWord().stream()
                .filter(c -> !c.getDue().isAfter(now))
                .filter(c -> askable(c, fillBlankWords))
                .filter(c -> !siblingRated(c, ratedByWord))
                .sorted(queueOrder(lessonNos))
                .toList();
        List<ReviewItemDto> items = new ArrayList<>();
        Set<Long> words = new HashSet<>();
        for (ReviewCard card : candidates) {
            if (items.size() == limit) {
                break;
            }
            boolean isNew = card.getState() == CardState.NEW;
            if (words.contains(card.getWord().getId()) || (isNew && newLeft == 0)) {
                continue;
            }
            if (isNew) {
                newLeft--;
            }
            words.add(card.getWord().getId());
            items.add(item(card, now));
        }
        return items;
    }

    /** Grades without saving anything; {@link #rate} applies the rating (spec 09, step 3). */
    @Transactional(readOnly = true)
    public AnswerResult answer(long cardId, AnswerRequest request) {
        ReviewCard card = cards.findById(cardId).orElseThrow(() -> new NotFoundException("Review card", cardId));
        Exercise exercise;
        if (request.exerciseId() != null) {
            exercise = exercises.findById(request.exerciseId())
                    .orElseThrow(() -> new NotFoundException("Exercise", request.exerciseId()));
            if (card.getCardType() != CardType.RECOGNITION || exercise.getType() != ExerciseType.FILL_BLANK
                    || !exerciseWords.existsByExerciseIdAndWordId(exercise.getId(), card.getWord().getId())) {
                throw new BadRequestException("Exercise " + exercise.getId() + " is not a FILL_BLANK with the word of "
                        + "card " + cardId);
            }
        } else {
            exercise = generator.forGrading(exerciseType(card.getCardType()), card.getWord())
                    .orElseThrow(() -> new BadRequestException("Card " + cardId + " cannot be asked"));
        }
        return grader.grade(exercise, request.response(), request.durationMs());
    }

    /** Applies FSRS, updates the card and writes {@code review_log}; a second rating within 5 s is a 409. */
    public CardDto rate(long cardId, int ratingValue) {
        Rating rating = Rating.of(ratingValue);
        Instant now = clock.instant();
        ReviewCard card = cards.findByIdForUpdate(cardId)
                .orElseThrow(() -> new NotFoundException("Review card", cardId));
        logs.findFirstByCardIdOrderByReviewedAtDesc(cardId)
                .filter(last -> last.getReviewedAt().isAfter(now.minus(RATE_GUARD)))
                .ifPresent(last -> {
                    throw new ConflictException("Card " + cardId + " was rated less than "
                            + RATE_GUARD.toSeconds() + " s ago");
                });

        CardState before = card.getState();
        ScheduledCard scheduled = scheduler.review(snapshot(card), rating, now, fuzz(card));
        CardSnapshot after = scheduled.card();
        card.setState(after.state());
        card.setStep(after.step());
        card.setStability(after.stability());
        card.setDifficulty(after.difficulty());
        card.setDue(after.due());
        card.setLastReview(after.lastReview());
        card.setReps(after.reps());
        card.setLapses(after.lapses());
        logs.save(new ReviewLog(card, (short) rating.value(), before, Math.toIntExact(scheduled.elapsedDays()),
                Math.toIntExact(scheduled.scheduledDays()), now));
        return CardDto.from(card);
    }

    @Transactional(readOnly = true)
    public ReviewStatsDto stats() {
        Instant now = clock.instant();
        ZoneId zone = settings.timeZone();
        LocalDate today = now.atZone(zone).toLocalDate();
        Instant startOfDay = today.atStartOfDay(zone).toInstant();
        Instant endOfDay = today.plusDays(1).atStartOfDay(zone).toInstant();
        List<ReviewCard> all = cards.findAllWithWord();
        Set<Long> fillBlankWords = exerciseWords.findWordIdsWithType(ExerciseType.FILL_BLANK);
        List<ReviewCard> askable = all.stream().filter(c -> askable(c, fillBlankWords)).toList();

        long newLeft = Math.min(newAllowance(startOfDay),
                askable.stream().filter(c -> c.getState() == CardState.NEW).count());
        long dueNow = dueBefore(askable, now, true, newLeft);
        long dueToday = dueBefore(askable, endOfDay, false, newLeft);

        Map<CardState, Long> byState = new EnumMap<>(CardState.class);
        Map<CardType, Long> byType = new EnumMap<>(CardType.class);
        for (CardState state : CardState.values()) {
            byState.put(state, 0L);
        }
        for (CardType type : CardType.values()) {
            byType.put(type, 0L);
        }
        all.forEach(c -> {
            byState.merge(c.getState(), 1L, Long::sum);
            byType.merge(c.getCardType(), 1L, Long::sum);
        });
        return new ReviewStatsDto(dueNow, dueToday, logs.countByReviewedAtGreaterThanEqual(startOfDay), newLeft,
                byState, byType);
    }

    /** Scheduled (not NEW) cards per local day from today; overdue cards count on today. */
    @Transactional(readOnly = true)
    public List<ForecastDayDto> forecast(int days) {
        if (days < 1 || days > MAX_FORECAST_DAYS) {
            throw new BadRequestException("days must be 1.." + MAX_FORECAST_DAYS);
        }
        ZoneId zone = settings.timeZone();
        LocalDate today = clock.instant().atZone(zone).toLocalDate();
        Set<Long> fillBlankWords = exerciseWords.findWordIdsWithType(ExerciseType.FILL_BLANK);
        Map<LocalDate, Long> counts = new LinkedHashMap<>();
        for (int d = 0; d < days; d++) {
            counts.put(today.plusDays(d), 0L);
        }
        cards.findAllWithWord().stream()
                .filter(c -> c.getState() != CardState.NEW && askable(c, fillBlankWords))
                .forEach(c -> {
                    LocalDate date = c.getDue().atZone(zone).toLocalDate();
                    counts.computeIfPresent(date.isBefore(today) ? today : date, (k, n) -> n + 1);
                });
        return counts.entrySet().stream().map(e -> new ForecastDayDto(e.getKey(), e.getValue())).toList();
    }

    /** New cards that may still be introduced today: the daily limit minus NEW cards rated since local midnight. */
    private long newAllowance(Instant startOfDay) {
        long introduced = logs.countRatedSince(CardState.NEW, startOfDay);
        return Math.max(0, settings.newPerDay() - introduced);
    }

    private Instant startOfDay(Instant now) {
        ZoneId zone = settings.timeZone();
        return now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant();
    }

    /** Scheduled cards due at or before {@code until} (strictly before when {@code inclusive} is false), plus NEW. */
    private static long dueBefore(List<ReviewCard> askable, Instant until, boolean inclusive, long newLeft) {
        long scheduled = 0;
        long newDue = 0;
        for (ReviewCard card : askable) {
            if (inclusive ? !card.getDue().isAfter(until) : card.getDue().isBefore(until)) {
                if (card.getState() == CardState.NEW) {
                    newDue++;
                } else {
                    scheduled++;
                }
            }
        }
        return scheduled + Math.min(newLeft, newDue);
    }

    private Comparator<ReviewCard> queueOrder(Map<Long, Integer> lessonNos) {
        Comparator<ReviewCard> byGroup = Comparator.comparingInt(c -> switch (c.getState()) {
            case LEARNING, RELEARNING -> 0;
            case REVIEW -> 1;
            case NEW -> 2;
        });
        return byGroup.thenComparing((a, b) -> a.getState() == CardState.NEW
                ? Comparator.<ReviewCard, Instant>comparing(c -> c.getWord().getCreatedAt())
                        .thenComparingInt(c -> lessonNos.getOrDefault(c.getWord().getId(), Integer.MAX_VALUE))
                        .thenComparing(ReviewCard::getId)
                        .compare(a, b)
                : Comparator.comparing(ReviewCard::getDue).thenComparing(ReviewCard::getId).compare(a, b));
    }

    /** Each word's first lesson number (textbook, then lesson), in one query. */
    private Map<Long, Integer> firstLessonNos() {
        Map<Long, Integer> lessonNos = new HashMap<>();
        for (Object[] row : lessonWords.findLessonNosOfWords()) {
            lessonNos.putIfAbsent((Long) row[0], (Integer) row[1]);
        }
        return lessonNos;
    }

    /** Whether another card of the card's word was rated within the sibling pause. */
    private static boolean siblingRated(ReviewCard card, Map<Long, Set<Long>> ratedByWord) {
        Set<Long> rated = ratedByWord.get(card.getWord().getId());
        return rated != null && rated.stream().anyMatch(id -> !id.equals(card.getId()));
    }

    private ReviewItemDto item(ReviewCard card, Instant now) {
        Map<String, String> previews = new LinkedHashMap<>();
        CardSnapshot snapshot = snapshot(card);
        for (Rating rating : Rating.values()) {
            Instant due = scheduler.review(snapshot, rating, now, fuzz(card)).card().due();
            previews.put(String.valueOf(rating.value()), isoDuration(Duration.between(now, due)));
        }
        return new ReviewItemDto(card.getId(), card.getCardType(), card.getWord().getId(), exercise(card), previews);
    }

    /** RECOGNITION: a stored FILL_BLANK with the word half of the time, if there is one; otherwise built on the fly. */
    private ExerciseDto exercise(ReviewCard card) {
        Word word = card.getWord();
        if (card.getCardType() == CardType.RECOGNITION) {
            List<Exercise> fillBlanks = exerciseWords.findExercisesOfWord(word.getId(), ExerciseType.FILL_BLANK);
            boolean canAskMeaning = generator.canGenerate(ExerciseType.HANZI_TO_MEANING, word);
            if (!fillBlanks.isEmpty() && (!canAskMeaning || random.nextBoolean())) {
                Exercise stored = fillBlanks.get(random.nextInt(fillBlanks.size()));
                return ExerciseService.toDtos(List.of(stored), exerciseWords).getFirst();
            }
        }
        Exercise built = codeExercise(card).orElseThrow();
        return new ExerciseDto(null, built.getType(), built.getPrompt(), List.of(word.getId()));
    }

    /** The exercise to show; only HANZI_TO_MEANING needs the lesson, for distractors from its words. */
    private Optional<Exercise> codeExercise(ReviewCard card) {
        Word word = card.getWord();
        ExerciseType type = exerciseType(card.getCardType());
        if (type != ExerciseType.HANZI_TO_MEANING) {
            return generator.generate(type, word, null, List.of());
        }
        Optional<Lesson> lesson = firstLesson(word.getId());
        List<Word> lessonWordList = lesson.map(l -> lessonWords.findWordsOfLesson(l.getId())).orElse(List.of());
        return generator.generate(type, word, lesson.orElse(null), lessonWordList);
    }

    /** The word's first lesson (textbook, lesson number), or empty for a word no lesson lists any more. */
    private Optional<Lesson> firstLesson(long wordId) {
        return lessonWords.findLinksOfWord(wordId).stream().findFirst().map(LessonWord::getLesson);
    }

    private boolean askable(ReviewCard card, Set<Long> fillBlankWords) {
        Word word = card.getWord();
        boolean codeExercise = generator.canGenerate(exerciseType(card.getCardType()), word);
        return codeExercise || (card.getCardType() == CardType.RECOGNITION && fillBlankWords.contains(word.getId()));
    }

    private static ExerciseType exerciseType(CardType type) {
        return switch (type) {
            case RECOGNITION -> ExerciseType.HANZI_TO_MEANING;
            case TONE -> ExerciseType.TONE;
            case WRITING -> ExerciseType.MEANING_TO_HANZI;
        };
    }

    private static CardSnapshot snapshot(ReviewCard card) {
        return new CardSnapshot(card.getState(), card.getStep(), card.getStability(), card.getDifficulty(),
                card.getDue(), card.getLastReview(), card.getReps(), card.getLapses());
    }

    /** The same fuzz for a card's preview and its rating, until the next review. */
    private static Random fuzz(ReviewCard card) {
        return new Random(card.getId() * 1_000_003L + card.getReps());
    }

    /** {@code P3D} for whole days, else {@code PT10M} / {@code PT5M30S} (spec 03, previewIntervals). */
    static String isoDuration(Duration duration) {
        if (duration.toDays() > 0 && duration.toSeconds() % 86_400 == 0 && duration.getNano() == 0) {
            return "P" + duration.toDays() + "D";
        }
        return duration.toString();
    }
}
