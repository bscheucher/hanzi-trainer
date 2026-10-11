package com.learning.hanzitrainer.word;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LessonWordRepository extends JpaRepository<LessonWord, LessonWordId> {

    @Query("select lw.word from LessonWord lw where lw.lesson.id = :lessonId order by lw.word.createdAt, lw.word.id")
    List<Word> findWordsOfLesson(@Param("lessonId") long lessonId);

    @Query("""
            select lw from LessonWord lw join fetch lw.lesson l join fetch l.textbook t
            where lw.word.id = :wordId order by t.id, l.lessonNo""")
    List<LessonWord> findLinksOfWord(@Param("wordId") long wordId);
}
