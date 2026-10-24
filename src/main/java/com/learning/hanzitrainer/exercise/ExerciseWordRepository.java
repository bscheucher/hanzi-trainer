package com.learning.hanzitrainer.exercise;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface ExerciseWordRepository extends JpaRepository<ExerciseWord, ExerciseWordId> {

    /** The words of these exercises, to build {@code ExerciseDto.wordIds}. */
    @Query("select ew from ExerciseWord ew where ew.exercise.id in :exerciseIds")
    List<ExerciseWord> findByExerciseIds(@Param("exerciseIds") Collection<Long> exerciseIds);

    /** Stored exercises of one type in a lesson, with the word each practises. */
    @Query("""
            select ew from ExerciseWord ew join fetch ew.exercise e
            where e.lesson.id = :lessonId and e.type = :type""")
    List<ExerciseWord> findByLessonAndType(@Param("lessonId") long lessonId, @Param("type") ExerciseType type);

    /** Stored exercises of one type that practise the word, in any lesson (reviews, spec 09). */
    @Query("select ew.exercise from ExerciseWord ew where ew.word.id = :wordId and ew.exercise.type = :type")
    List<Exercise> findExercisesOfWord(@Param("wordId") long wordId, @Param("type") ExerciseType type);

    /** Words with at least one stored exercise of the type. */
    @Query("select distinct ew.word.id from ExerciseWord ew where ew.exercise.type = :type")
    Set<Long> findWordIdsWithType(@Param("type") ExerciseType type);

    boolean existsByExerciseIdAndWordId(long exerciseId, long wordId);
}
