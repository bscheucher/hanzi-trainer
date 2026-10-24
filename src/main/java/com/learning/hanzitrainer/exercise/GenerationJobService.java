package com.learning.hanzitrainer.exercise;

import com.learning.hanzitrainer.common.BadRequestException;
import com.learning.hanzitrainer.common.NotFoundException;
import com.learning.hanzitrainer.common.ServiceUnavailableException;
import com.learning.hanzitrainer.common.UnreadableReplyException;
import com.learning.hanzitrainer.textbook.Lesson;
import com.learning.hanzitrainer.textbook.LessonRepository;
import com.learning.hanzitrainer.word.LessonWordRepository;
import com.learning.hanzitrainer.word.WordDto;
import com.learning.hanzitrainer.word.WordRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Generates sentence exercises for a lesson in the background (spec 07, "LLM generation"; spec 03). Jobs are held
 * in memory only. Per type, exercises that fail a check are discarded and the missing count is asked for again, at
 * most {@link #RETRIES} more times, in calls of at most {@link #MAX_PER_CALL} and at most {@link #maxCalls} per type;
 * each accepted exercise is stored right away. Jobs for the same lesson run one
 * after another, so concurrent jobs do not store the same sentence twice. An unreadable reply (e.g. JSON cut
 * off at the token limit) counts as one rejection and is retried the same way.
 */
@Service
public class GenerationJobService {

    static final Set<ExerciseType> LLM_TYPES = Set.of(ExerciseType.FILL_BLANK, ExerciseType.WORD_ORDER,
            ExerciseType.TRANSLATION);
    static final int RETRIES = 2;
    /** Exercises asked for in one model call; a reply with thinking and more of them can outlast the timeout. */
    static final int MAX_PER_CALL = 5;
    /** How long a finished job can still be polled. */
    static final Duration KEEP_FINISHED = Duration.ofHours(1);

    private static final Logger log = LoggerFactory.getLogger(GenerationJobService.class);
    private static final int MAX_ERROR_LENGTH = 500;
    private static final String INTERRUPTED = "Interrupted by shutdown";

    private final LessonRepository lessons;
    private final LessonWordRepository lessonWords;
    private final WordRepository words;
    private final ExerciseRepository exercises;
    private final ExerciseWordRepository exerciseWords;
    private final ExerciseCodec codec;
    private final ExerciseGenerator generator;
    private final TransactionTemplate tx;
    private final ExecutorService executor;
    private final Random random;
    private final Clock clock;
    private final Map<Long, Job> jobs = new ConcurrentHashMap<>();
    private final Map<Long, ReentrantLock> lessonLocks = new ConcurrentHashMap<>();
    private volatile boolean stopping;
    private final AtomicLong ids = new AtomicLong();

    @Autowired
    public GenerationJobService(LessonRepository lessons, LessonWordRepository lessonWords, WordRepository words,
                                ExerciseRepository exercises, ExerciseWordRepository exerciseWords,
                                ExerciseCodec codec, ExerciseGenerator generator, TransactionTemplate tx) {
        this(lessons, lessonWords, words, exercises, exerciseWords, codec, generator, tx,
                Executors.newVirtualThreadPerTaskExecutor(), new Random(), Clock.systemUTC());
    }

    GenerationJobService(LessonRepository lessons, LessonWordRepository lessonWords, WordRepository words,
                         ExerciseRepository exercises, ExerciseWordRepository exerciseWords, ExerciseCodec codec,
                         ExerciseGenerator generator, TransactionTemplate tx, ExecutorService executor, Random random,
                         Clock clock) {
        this.lessons = lessons;
        this.lessonWords = lessonWords;
        this.words = words;
        this.exercises = exercises;
        this.exerciseWords = exerciseWords;
        this.codec = codec;
        this.generator = generator;
        this.tx = tx;
        this.executor = executor;
        this.random = random;
        this.clock = clock;
    }

    /** Interrupts running jobs on shutdown, so no model calls are paid for after the app stopped. */
    @PreDestroy
    void shutdown() {
        // The flag, not the interrupt, is what jobs check: HTTP clients may clear the interrupt flag when they abort
        // a call (okio throws InterruptedIOException), and retry it.
        stopping = true;
        executor.shutdownNow();
    }

    /** Starts a job; it runs in the background and is polled with {@link #get}. */
    public GenerationJobDto start(long lessonId, GenerateExercisesRequest request) {
        if (!lessons.existsById(lessonId)) {
            throw new NotFoundException("Lesson", lessonId);
        }
        List<ExerciseType> types = List.copyOf(new LinkedHashSet<>(request.types()));
        List<ExerciseType> notLlm = types.stream().filter(t -> !LLM_TYPES.contains(t)).toList();
        if (!notLlm.isEmpty()) {
            throw new BadRequestException(notLlm + " are generated on the fly; only FILL_BLANK, WORD_ORDER and "
                    + "TRANSLATION can be generated");
        }
        if (lessonWords.findWordsOfLesson(lessonId).isEmpty()) {
            throw new BadRequestException("Lesson " + lessonId + " has no words yet");
        }
        generator.unavailableReason().ifPresent(reason -> {
            throw new ServiceUnavailableException(reason);
        });
        evictFinished();
        Job job = new Job(ids.incrementAndGet());
        jobs.put(job.id, job);
        try {
            executor.execute(() -> run(job, lessonId, types, request.count()));
        } catch (RejectedExecutionException e) {
            jobs.remove(job.id); // shutting down; the job would otherwise stay RUNNING
            throw new ServiceUnavailableException("The app is shutting down");
        }
        return job.dto();
    }

    public GenerationJobDto get(long id) {
        Job job = jobs.get(id);
        if (job != null && job.expired(clock.instant().minus(KEEP_FINISHED))) {
            jobs.remove(id); // start() evicts the others
            job = null;
        }
        if (job == null) {
            throw new NotFoundException("Generation job", id);
        }
        return job.dto();
    }

    /** Evicts jobs that finished more than {@link #KEEP_FINISHED} ago, so the map does not grow forever. */
    private void evictFinished() {
        Instant cutoff = clock.instant().minus(KEEP_FINISHED);
        jobs.values().removeIf(job -> job.expired(cutoff));
    }

    private void run(Job job, long lessonId, List<ExerciseType> types, int count) {
        // Jobs for the same lesson run one after another, so each sees the sentences the other stored.
        ReentrantLock lock = lessonLocks.computeIfAbsent(lessonId, id -> new ReentrantLock());
        try {
            lock.lockInterruptibly(); // shutdownNow() interrupts a job still waiting for the lesson
        } catch (InterruptedException e) {
            job.finish(GenerationJobDto.Status.FAILED, INTERRUPTED, clock);
            Thread.currentThread().interrupt();
            return;
        }
        try {
            stopIfStopping();
            AllowedWords allowed = tx.execute(s -> allowedWords(lessonId));
            for (ExerciseType type : types) {
                generate(job, lessonId, type, count, allowed);
            }
            job.finish(GenerationJobDto.Status.DONE, null, clock);
        } catch (RuntimeException | Error e) {
            if (e instanceof Stopped) {
                log.info("Generation job {} for lesson {} stopped by shutdown", job.id, lessonId);
                job.finish(GenerationJobDto.Status.FAILED, INTERRUPTED, clock);
                return;
            }
            if (stopping) {
                // Most likely the HTTP client's reaction to the interrupt, but keep the cause in the log.
                log.info("Generation job {} for lesson {} failed during shutdown", job.id, lessonId, e);
                job.finish(GenerationJobDto.Status.FAILED, INTERRUPTED, clock);
                return;
            }
            // An Error too: otherwise the job would stay RUNNING and the frontend would poll forever.
            log.warn("Generation job {} for lesson {} failed", job.id, lessonId, e);
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            job.finish(GenerationJobDto.Status.FAILED,
                    message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message, clock);
        } finally {
            lock.unlock();
        }
    }

    private void generate(Job job, long lessonId, ExerciseType type, int count, AllowedWords allowed) {
        stopIfStopping();
        Context context = tx.execute(s -> context(lessonId, type));
        int needed = count;
        int round = 0; // what is left of the current round's missing count, asked for in calls of MAX_PER_CALL
        for (int calls = maxCalls(count); calls > 0 && needed > 0; calls--) {
            round = round > 0 ? Math.min(round, needed) : needed;
            int asked = Math.min(round, MAX_PER_CALL);
            round -= asked;
            needed -= call(job, lessonId, type, asked, context, allowed);
        }
    }

    /** The calls one type may make: enough for {@code count}, plus {@link #RETRIES} to make up for rejections. */
    static int maxCalls(int count) {
        return (count + MAX_PER_CALL - 1) / MAX_PER_CALL + RETRIES;
    }

    /** One call to the model; returns how many of the {@code asked} exercises were stored. */
    private int call(Job job, long lessonId, ExerciseType type, int asked, Context context, AllowedWords allowed) {
        stopIfStopping(); // make no further paid calls
        List<WordDto> targets = context.targets(asked, random);
        List<GeneratedExercise> items;
        try {
            items = generator.generate(type, targets, allowed.words(), asked);
        } catch (UnreadableReplyException e) {
            log.info("Generation job {}: rejected {} reply, {}", job.id, type, e.getMessage());
            job.rejected.incrementAndGet();
            return 0;
        }
        int stored = 0;
        for (GeneratedExercise item : items) {
            if (stored == asked) {
                break;
            }
            GeneratedExercises.Result result = GeneratedExercises.check(type, item, targets, allowed,
                    context.sentences(), context.sequence(), random);
            if (result.built() == null) {
                log.info("Generation job {}: rejected {} exercise, {}", job.id, type, result.rejection());
                job.rejected.incrementAndGet();
                continue;
            }
            stopIfStopping(); // no JDBC on an interrupted thread
            tx.executeWithoutResult(s -> store(lessonId, type, result.built()));
            context.stored(result.built());
            stored++;
            job.created.incrementAndGet();
        }
        return stored;
    }

    private void stopIfStopping() {
        if (stopping) {
            throw new Stopped();
        }
    }

    /** The job noticed {@link #shutdown()}. */
    private static final class Stopped extends RuntimeException {

        Stopped() {
            super(INTERRUPTED);
        }
    }

    private AllowedWords allowedWords(long lessonId) {
        Lesson lesson = lessons.findById(lessonId).orElseThrow(() -> new NotFoundException("Lesson", lessonId));
        return new AllowedWords(words.findAllowedHanzi(lesson.getTextbook().getId(), lesson.getLessonNo()));
    }

    /** Loaded once per type; kept up to date in memory as exercises are stored. */
    private Context context(long lessonId, ExerciseType type) {
        Map<Long, Integer> stored = new HashMap<>();
        for (ExerciseWord ew : exerciseWords.findByLessonAndType(lessonId, type)) {
            stored.merge(ew.getId().wordId(), 1, Integer::sum);
        }
        List<WordDto> lessonWordList = lessonWords.findWordsOfLesson(lessonId).stream().map(WordDto::from).toList();
        List<Exercise> existing = exercises.findByLessonIdAndTypeOrderById(lessonId, type);
        Set<String> sentences = new HashSet<>();
        existing.forEach(e -> sentences.add(GeneratedExercises.sentenceOf(codec.answer(e))));
        return new Context(lessonWordList, stored, sentences, existing.size());
    }

    private void store(long lessonId, ExerciseType type, GeneratedExercises.Built built) {
        Exercise exercise = exercises.save(new Exercise(lessons.getReferenceById(lessonId), type,
                codec.toJson(built.prompt()), codec.toJson(built.answer()), generator.modelName()));
        for (WordDto word : built.words()) {
            exerciseWords.save(new ExerciseWord(exercise, words.getReferenceById(word.id())));
        }
    }

    /**
     * The lesson's words with their count of stored exercises of the type, the sentences already stored (to skip
     * duplicates) and the running number of the next exercise.
     */
    private static final class Context {

        private final List<WordDto> lessonWords;
        private final Map<Long, Integer> stored;
        private final Set<String> sentences;
        private final Set<Long> targeted = new HashSet<>();
        private int sequence;

        Context(List<WordDto> lessonWords, Map<Long, Integer> stored, Set<String> sentences, int sequence) {
            this.lessonWords = lessonWords;
            this.stored = stored;
            this.sentences = sentences;
            this.sequence = sequence;
        }

        /**
         * Words with the fewest stored exercises of the type first, ties in random order; words already targeted
         * in this job come last until every word was, so a call that stores nothing does not starve the others.
         */
        List<WordDto> targets(int needed, Random random) {
            if (targeted.size() >= lessonWords.size()) {
                targeted.clear();
            }
            List<WordDto> sorted = new ArrayList<>(lessonWords);
            Collections.shuffle(sorted, random);
            sorted.sort(Comparator.<WordDto>comparingInt(w -> targeted.contains(w.id()) ? 1 : 0)
                    .thenComparingInt(w -> stored.getOrDefault(w.id(), 0)));
            List<WordDto> targets = List.copyOf(sorted.subList(0, Math.min(needed, sorted.size())));
            targets.forEach(w -> targeted.add(w.id()));
            return targets;
        }

        Set<String> sentences() {
            return sentences;
        }

        int sequence() {
            return sequence;
        }

        void stored(GeneratedExercises.Built built) {
            sentences.add(built.sentence());
            built.words().forEach(w -> stored.merge(w.id(), 1, Integer::sum));
            sequence++;
        }
    }

    private static final class Job {

        final long id;
        final AtomicInteger created = new AtomicInteger();
        final AtomicInteger rejected = new AtomicInteger();
        volatile GenerationJobDto.Status status = GenerationJobDto.Status.RUNNING;
        volatile String error;
        volatile Instant finishedAt;

        Job(long id) {
            this.id = id;
        }

        boolean expired(Instant cutoff) {
            return finishedAt != null && finishedAt.isBefore(cutoff);
        }

        void finish(GenerationJobDto.Status status, String error, Clock clock) {
            this.error = error;
            this.finishedAt = clock.instant();
            this.status = status;
        }

        GenerationJobDto dto() {
            return new GenerationJobDto(id, status, created.get(), rejected.get(), error);
        }
    }
}
