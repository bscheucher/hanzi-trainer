package com.learning.hanzitrainer.exercise;

import com.learning.hanzitrainer.common.BadRequestException;
import com.learning.hanzitrainer.common.NotFoundException;
import com.learning.hanzitrainer.common.ServiceUnavailableException;
import com.learning.hanzitrainer.textbook.Lesson;
import com.learning.hanzitrainer.textbook.LessonRepository;
import com.learning.hanzitrainer.word.LessonWordRepository;
import com.learning.hanzitrainer.word.WordDto;
import com.learning.hanzitrainer.word.WordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

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
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Generates sentence exercises for a lesson in the background (spec 07, "LLM generation"; spec 03). Jobs are held
 * in memory only. Per type, exercises that fail a check are discarded and the missing count is asked for again, at
 * most {@link #RETRIES} more times; each accepted exercise is stored right away.
 */
@Service
public class GenerationJobService {

    static final Set<ExerciseType> LLM_TYPES = Set.of(ExerciseType.FILL_BLANK, ExerciseType.WORD_ORDER,
            ExerciseType.TRANSLATION);
    static final int RETRIES = 2;

    private static final Logger log = LoggerFactory.getLogger(GenerationJobService.class);
    private static final int MAX_ERROR_LENGTH = 500;

    private final LessonRepository lessons;
    private final LessonWordRepository lessonWords;
    private final WordRepository words;
    private final ExerciseRepository exercises;
    private final ExerciseWordRepository exerciseWords;
    private final ExerciseCodec codec;
    private final ExerciseGenerator generator;
    private final TransactionTemplate tx;
    private final Executor executor;
    private final Random random;
    private final Map<Long, Job> jobs = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();

    @Autowired
    public GenerationJobService(LessonRepository lessons, LessonWordRepository lessonWords, WordRepository words,
                                ExerciseRepository exercises, ExerciseWordRepository exerciseWords,
                                ExerciseCodec codec, ExerciseGenerator generator, TransactionTemplate tx) {
        this(lessons, lessonWords, words, exercises, exerciseWords, codec, generator, tx,
                Executors.newVirtualThreadPerTaskExecutor(), new Random());
    }

    GenerationJobService(LessonRepository lessons, LessonWordRepository lessonWords, WordRepository words,
                         ExerciseRepository exercises, ExerciseWordRepository exerciseWords, ExerciseCodec codec,
                         ExerciseGenerator generator, TransactionTemplate tx, Executor executor, Random random) {
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
        Job job = new Job(ids.incrementAndGet());
        jobs.put(job.id, job);
        executor.execute(() -> run(job, lessonId, types, request.count()));
        return job.dto();
    }

    public GenerationJobDto get(long id) {
        Job job = jobs.get(id);
        if (job == null) {
            throw new NotFoundException("Generation job", id);
        }
        return job.dto();
    }

    private void run(Job job, long lessonId, List<ExerciseType> types, int count) {
        try {
            for (ExerciseType type : types) {
                generate(job, lessonId, type, count);
            }
            job.status = GenerationJobDto.Status.DONE;
        } catch (RuntimeException e) {
            log.warn("Generation job {} for lesson {} failed", job.id, lessonId, e);
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            job.error = message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message;
            job.status = GenerationJobDto.Status.FAILED;
        }
    }

    private void generate(Job job, long lessonId, ExerciseType type, int count) {
        int needed = count;
        for (int attempt = 0; attempt <= RETRIES && needed > 0; attempt++) {
            int asked = needed;
            Context context = tx.execute(s -> context(lessonId, type, asked));
            List<GeneratedExercise> items = generator.generate(type, context.targets(),
                    context.allowed().words(), needed);
            int sequence = context.sequence();
            for (GeneratedExercise item : items) {
                if (needed == 0) {
                    break;
                }
                GeneratedExercises.Result result = GeneratedExercises.check(type, item, context.targets(),
                        context.allowed(), context.sentences(), sequence, random);
                if (result.built() == null) {
                    log.info("Generation job {}: rejected {} exercise, {}", job.id, type, result.rejection());
                    job.rejected.incrementAndGet();
                    continue;
                }
                tx.executeWithoutResult(s -> store(lessonId, type, result.built()));
                context.sentences().add(result.built().sentence());
                sequence++;
                needed--;
                job.created.incrementAndGet();
            }
        }
    }

    /**
     * What one call to the model needs: the target words (fewest stored exercises of the type first, ties in random
     * order), the allowed words, and the sentences already stored, to skip duplicates.
     */
    private Context context(long lessonId, ExerciseType type, int needed) {
        Lesson lesson = lessons.findById(lessonId).orElseThrow(() -> new NotFoundException("Lesson", lessonId));
        Map<Long, Integer> stored = new HashMap<>();
        for (ExerciseWord ew : exerciseWords.findByLessonAndType(lessonId, type)) {
            stored.merge(ew.getId().wordId(), 1, Integer::sum);
        }
        List<WordDto> lessonWordList = new ArrayList<>(lessonWords.findWordsOfLesson(lessonId).stream()
                .map(WordDto::from).toList());
        Collections.shuffle(lessonWordList, random);
        lessonWordList.sort(Comparator.comparingInt(w -> stored.getOrDefault(w.id(), 0)));
        List<WordDto> targets = List.copyOf(lessonWordList.subList(0, Math.min(needed, lessonWordList.size())));

        AllowedWords allowed = new AllowedWords(
                words.findAllowedHanzi(lesson.getTextbook().getId(), lesson.getLessonNo()));
        List<Exercise> existing = exercises.findByLessonIdAndTypeOrderById(lessonId, type);
        Set<String> sentences = new HashSet<>();
        existing.forEach(e -> sentences.add(GeneratedExercises.sentenceOf(codec.answer(e))));
        return new Context(targets, allowed, sentences, existing.size());
    }

    private void store(long lessonId, ExerciseType type, GeneratedExercises.Built built) {
        Exercise exercise = exercises.save(new Exercise(lessons.getReferenceById(lessonId), type,
                codec.toJson(built.prompt()), codec.toJson(built.answer()), generator.modelName()));
        for (WordDto word : built.words()) {
            exerciseWords.save(new ExerciseWord(exercise, words.getReferenceById(word.id())));
        }
    }

    private record Context(List<WordDto> targets, AllowedWords allowed, Set<String> sentences, int sequence) {
    }

    private static final class Job {

        final long id;
        final AtomicInteger created = new AtomicInteger();
        final AtomicInteger rejected = new AtomicInteger();
        volatile GenerationJobDto.Status status = GenerationJobDto.Status.RUNNING;
        volatile String error;

        Job(long id) {
            this.id = id;
        }

        GenerationJobDto dto() {
            return new GenerationJobDto(id, status, created.get(), rejected.get(), error);
        }
    }
}
