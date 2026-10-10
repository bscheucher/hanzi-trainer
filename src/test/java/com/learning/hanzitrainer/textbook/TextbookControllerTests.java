package com.learning.hanzitrainer.textbook;

import com.learning.hanzitrainer.common.ConflictException;
import com.learning.hanzitrainer.common.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TextbookController.class)
class TextbookControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TextbookService service;

    @Test
    void listsTextbooks() throws Exception {
        when(service.listTextbooks()).thenReturn(List.of(new TextbookDto(1L, "HSK 1", "3rd", 2)));

        mockMvc.perform(get("/api/textbooks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("HSK 1"))
                .andExpect(jsonPath("$[0].edition").value("3rd"))
                .andExpect(jsonPath("$[0].lessonCount").value(2));
    }

    @Test
    void createsTextbook() throws Exception {
        when(service.createTextbook(new CreateTextbookRequest("HSK 1", null)))
                .thenReturn(new TextbookDto(7L, "HSK 1", null, 0));

        mockMvc.perform(post("/api/textbooks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"HSK 1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    void blankTitleIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/textbooks").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(service);
    }

    @Test
    void createsLesson() throws Exception {
        when(service.createLesson(eq(1L), any()))
                .thenReturn(new LessonSummaryDto(5L, 1L, 3, "你好", 0, 0, 0));

        mockMvc.perform(post("/api/textbooks/1/lessons").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lessonNo\":3,\"title\":\"你好\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lessonNo").value(3))
                .andExpect(jsonPath("$.pendingReviewPages").value(0));
        verify(service).createLesson(1L, new CreateLessonRequest(3, "你好"));
    }

    @Test
    void lessonNumberMustBePositive() throws Exception {
        mockMvc.perform(post("/api/textbooks/1/lessons").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lessonNo\":0}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/textbooks/1/lessons").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateLessonIs409() throws Exception {
        when(service.createLesson(eq(1L), any())).thenThrow(new ConflictException("Lesson 1 already exists"));

        mockMvc.perform(post("/api/textbooks/1/lessons").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lessonNo\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Lesson 1 already exists"));
    }

    @Test
    void lessonDetailIncludesSummaryFieldsAtTopLevel() throws Exception {
        when(service.getLesson(5L)).thenReturn(new LessonDetailDto(5L, 1L, 3, "你好", 2, 0, 1, List.of(), List.of()));

        mockMvc.perform(get("/api/lessons/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.textbookId").value(1))
                .andExpect(jsonPath("$.pageCount").value(2))
                .andExpect(jsonPath("$.pages").isArray())
                .andExpect(jsonPath("$.words").isArray());
    }

    @Test
    void deleteUnknownLessonIs404() throws Exception {
        doThrow(new NotFoundException("Lesson", 9)).when(service).deleteLesson(9L);

        mockMvc.perform(delete("/api/lessons/9")).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/lessons/5")).andExpect(status().isNoContent());
    }
}
