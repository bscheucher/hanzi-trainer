package com.learning.hanzitrainer.page;

import com.learning.hanzitrainer.TestImages;
import com.learning.hanzitrainer.common.NotFoundException;
import com.learning.hanzitrainer.common.ServiceUnavailableException;
import com.learning.hanzitrainer.extraction.UnavailableVocabularyExtractor;
import com.learning.hanzitrainer.textbook.Lesson;
import com.learning.hanzitrainer.textbook.LessonRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PageServiceTests {

    private final PageImageRepository pages = mock(PageImageRepository.class);
    private final LessonRepository lessons = mock(LessonRepository.class);
    private final FileStorage storage = mock(FileStorage.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final PageService service = new PageService(pages, lessons, storage,
            new UnavailableVocabularyExtractor("needs ANTHROPIC_API_KEY"), mock(PageMapper.class), events);

    @Test
    void uploadIs503WhenLlmIsNotConfigured() {
        when(lessons.findById(1L)).thenReturn(Optional.of(mock(Lesson.class)));

        assertThatThrownBy(() -> service.upload(1, TestImages.png(10, 10), null))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessage("needs ANTHROPIC_API_KEY");
        verify(storage, never()).store(anyLong(), any(), any());
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void rerunIs503WhenLlmIsNotConfigured() {
        PageImage page = new PageImage(mock(Lesson.class), 1, "lesson-1/a.png", "image/png");
        page.setExtractionStatus(ExtractionStatus.FAILED);
        when(pages.findById(5L)).thenReturn(Optional.of(page));

        assertThatThrownBy(() -> service.rerun(5)).isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void uploadToUnknownLessonIs404() {
        when(lessons.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upload(9, TestImages.png(10, 10), null))
                .isInstanceOf(NotFoundException.class);
    }
}
