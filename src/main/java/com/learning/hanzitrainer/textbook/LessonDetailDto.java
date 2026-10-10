package com.learning.hanzitrainer.textbook;

import com.learning.hanzitrainer.page.PageDto;
import com.learning.hanzitrainer.word.WordDto;

import java.util.List;

/** {@link LessonSummaryDto} fields plus pages and words (spec 03). */
public record LessonDetailDto(Long id, Long textbookId, int lessonNo, String title, long pageCount,
                              long wordCount, long pendingReviewPages, List<PageDto> pages, List<WordDto> words) {

    static LessonDetailDto of(LessonSummaryDto s, List<PageDto> pages, List<WordDto> words) {
        return new LessonDetailDto(s.id(), s.textbookId(), s.lessonNo(), s.title(), s.pageCount(), s.wordCount(),
                s.pendingReviewPages(), pages, words);
    }
}
