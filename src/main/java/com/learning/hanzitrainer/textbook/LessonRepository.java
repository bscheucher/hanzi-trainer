package com.learning.hanzitrainer.textbook;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LessonRepository extends JpaRepository<Lesson, Long> {

    String SUMMARY = """
            select new com.learning.hanzitrainer.textbook.LessonSummaryDto(
                l.id, l.textbook.id, l.lessonNo, l.title,
                (select count(p) from PageImage p where p.lesson = l),
                (select count(lw) from LessonWord lw where lw.lesson = l),
                (select count(p) from PageImage p where p.lesson = l
                    and p.extractionStatus = com.learning.hanzitrainer.page.ExtractionStatus.DONE
                    and p.confirmedAt is null))
            from Lesson l""";

    @Query(SUMMARY + " where l.textbook.id = :textbookId order by l.lessonNo")
    List<LessonSummaryDto> findSummaries(@Param("textbookId") long textbookId);

    @Query(SUMMARY + " where l.id = :id")
    Optional<LessonSummaryDto> findSummary(@Param("id") long id);

    boolean existsByTextbookIdAndLessonNo(long textbookId, int lessonNo);
}
