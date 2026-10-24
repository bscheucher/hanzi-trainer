package com.learning.hanzitrainer.exercise;

import com.learning.hanzitrainer.common.NotFoundException;
import com.learning.hanzitrainer.textbook.LessonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Stored exercises of a lesson, deleting one, and answering one (spec 03, "Exercises"). */
@Service
@Transactional
public class ExerciseService {

    private final LessonRepository lessons;
    private final ExerciseRepository exercises;
    private final ExerciseWordRepository exerciseWords;
    private final ExerciseGrader grader;

    public ExerciseService(LessonRepository lessons, ExerciseRepository exercises,
                           ExerciseWordRepository exerciseWords, ExerciseGrader grader) {
        this.lessons = lessons;
        this.exercises = exercises;
        this.exerciseWords = exerciseWords;
        this.grader = grader;
    }

    @Transactional(readOnly = true)
    public List<ExerciseDto> list(long lessonId, ExerciseType type) {
        if (!lessons.existsById(lessonId)) {
            throw new NotFoundException("Lesson", lessonId);
        }
        List<Exercise> found = type == null ? exercises.findByLessonIdOrderById(lessonId)
                : exercises.findByLessonIdAndTypeOrderById(lessonId, type);
        return toDtos(found, exerciseWords);
    }

    /**
     * Grades without storing anything: free practice does not touch review cards (spec 07). Runs outside a
     * transaction, so no database connection is held while a TRANSLATION answer waits for the LLM grader.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AnswerResult answer(long id, AnswerRequest request) {
        return grader.grade(find(id), request.response(), request.durationMs());
    }

    public void delete(long id) {
        exercises.delete(find(id));
    }

    private Exercise find(long id) {
        return exercises.findById(id).orElseThrow(() -> new NotFoundException("Exercise", id));
    }

    static List<ExerciseDto> toDtos(List<Exercise> list, ExerciseWordRepository exerciseWords) {
        Map<Long, List<Long>> wordIds = new HashMap<>();
        if (!list.isEmpty()) {
            for (ExerciseWord ew : exerciseWords.findByExerciseIds(list.stream().map(Exercise::getId).toList())) {
                wordIds.computeIfAbsent(ew.getId().exerciseId(), id -> new ArrayList<>()).add(ew.getId().wordId());
            }
        }
        return list.stream()
                .map(e -> new ExerciseDto(e.getId(), e.getType(), e.getPrompt(),
                        wordIds.getOrDefault(e.getId(), List.of()).stream().sorted().toList()))
                .toList();
    }
}
