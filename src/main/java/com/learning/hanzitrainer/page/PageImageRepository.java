package com.learning.hanzitrainer.page;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PageImageRepository extends JpaRepository<PageImage, Long> {

    @Query("""
            select p from PageImage p where p.lesson.id = :lessonId
            order by p.pageNo asc nulls last, p.createdAt asc, p.id asc""")
    List<PageImage> findByLessonOrdered(@Param("lessonId") long lessonId);

    /** Atomic status transition; returns 0 if the page is gone or not in {@code from}. */
    @Modifying
    @Query("update PageImage p set p.extractionStatus = :to where p.id = :id and p.extractionStatus = :from")
    int transition(@Param("id") long id, @Param("from") ExtractionStatus from, @Param("to") ExtractionStatus to);

    /** Back to PENDING (error cleared) only if currently in one of {@code from}; returns 0 otherwise. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update PageImage p set p.extractionStatus = :to, p.errorMessage = null
            where p.id = :id and p.extractionStatus in :from""")
    int restart(@Param("id") long id, @Param("from") Collection<ExtractionStatus> from,
                @Param("to") ExtractionStatus to);

    @Modifying
    @Query("""
            update PageImage p set p.extractionStatus = :failed, p.errorMessage = :message
            where p.extractionStatus in :unfinished""")
    int failAll(@Param("unfinished") Collection<ExtractionStatus> unfinished,
                @Param("failed") ExtractionStatus failed, @Param("message") String message);
}
