package com.learning.hanzitrainer.textbook;

import com.learning.hanzitrainer.common.ConflictException;
import com.learning.hanzitrainer.common.NotFoundException;
import com.learning.hanzitrainer.page.FileStorage;
import com.learning.hanzitrainer.page.PageImageRepository;
import com.learning.hanzitrainer.page.PageMapper;
import com.learning.hanzitrainer.word.LessonWordRepository;
import com.learning.hanzitrainer.word.WordDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Service
@Transactional
public class TextbookService {

    private final TextbookRepository textbooks;
    private final LessonRepository lessons;
    private final PageImageRepository pages;
    private final LessonWordRepository lessonWords;
    private final PageMapper pageMapper;
    private final FileStorage storage;

    public TextbookService(TextbookRepository textbooks, LessonRepository lessons, PageImageRepository pages,
                           LessonWordRepository lessonWords, PageMapper pageMapper, FileStorage storage) {
        this.textbooks = textbooks;
        this.lessons = lessons;
        this.pages = pages;
        this.lessonWords = lessonWords;
        this.pageMapper = pageMapper;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public List<TextbookDto> listTextbooks() {
        return textbooks.findAllWithLessonCount();
    }

    public TextbookDto createTextbook(CreateTextbookRequest request) {
        Textbook textbook = textbooks.save(new Textbook(request.title().strip(), blankToNull(request.edition())));
        return new TextbookDto(textbook.getId(), textbook.getTitle(), textbook.getEdition(), 0);
    }

    @Transactional(readOnly = true)
    public List<LessonSummaryDto> listLessons(long textbookId) {
        requireTextbook(textbookId);
        return lessons.findSummaries(textbookId);
    }

    public LessonSummaryDto createLesson(long textbookId, CreateLessonRequest request) {
        Textbook textbook = requireTextbook(textbookId);
        if (lessons.existsByTextbookIdAndLessonNo(textbookId, request.lessonNo())) {
            throw new ConflictException("Lesson " + request.lessonNo() + " already exists in this textbook");
        }
        Lesson lesson = lessons.save(new Lesson(textbook, request.lessonNo(), blankToNull(request.title())));
        return new LessonSummaryDto(lesson.getId(), textbookId, lesson.getLessonNo(), lesson.getTitle(), 0, 0, 0);
    }

    @Transactional(readOnly = true)
    public LessonDetailDto getLesson(long lessonId) {
        LessonSummaryDto summary = lessons.findSummary(lessonId)
                .orElseThrow(() -> new NotFoundException("Lesson", lessonId));
        var pageDtos = pages.findByLessonOrdered(lessonId).stream().map(pageMapper::toDto).toList();
        var words = lessonWords.findWordsOfLesson(lessonId).stream().map(WordDto::from).toList();
        return LessonDetailDto.of(summary, pageDtos, words);
    }

    /** Pages, lesson words and exercises go with it (FK cascade); words stay (spec 02, invariant 5). */
    public void deleteLesson(long lessonId) {
        Lesson lesson = lessons.findById(lessonId).orElseThrow(() -> new NotFoundException("Lesson", lessonId));
        lessons.delete(lesson);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storage.deleteLesson(lessonId);
            }
        });
    }

    private Textbook requireTextbook(long textbookId) {
        return textbooks.findById(textbookId).orElseThrow(() -> new NotFoundException("Textbook", textbookId));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
