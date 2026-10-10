package com.learning.hanzitrainer.textbook;

/** @param pendingReviewPages pages with a finished extraction whose vocabulary is not confirmed yet */
public record LessonSummaryDto(Long id, Long textbookId, int lessonNo, String title, long pageCount,
                               long wordCount, long pendingReviewPages) {
}
