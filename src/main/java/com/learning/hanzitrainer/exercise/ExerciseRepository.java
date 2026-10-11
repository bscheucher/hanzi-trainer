package com.learning.hanzitrainer.exercise;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExerciseRepository extends JpaRepository<Exercise, Long> {

    List<Exercise> findByLessonIdOrderById(long lessonId);

    List<Exercise> findByLessonIdAndTypeOrderById(long lessonId, ExerciseType type);
}
