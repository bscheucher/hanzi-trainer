package com.learning.hanzitrainer.textbook;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TextbookRepository extends JpaRepository<Textbook, Long> {

    @Query("""
            select new com.learning.hanzitrainer.textbook.TextbookDto(
                t.id, t.title, t.edition, (select count(l) from Lesson l where l.textbook = t))
            from Textbook t order by t.createdAt, t.id""")
    List<TextbookDto> findAllWithLessonCount();

}
