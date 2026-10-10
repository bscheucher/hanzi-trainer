package com.learning.hanzitrainer.word;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LessonWordRepository extends JpaRepository<LessonWord, LessonWordId> {

    @Query("select lw.word from LessonWord lw where lw.lesson.id = :lessonId order by lw.word.createdAt, lw.word.id")
    List<Word> findWordsOfLesson(@Param("lessonId") long lessonId);
}
