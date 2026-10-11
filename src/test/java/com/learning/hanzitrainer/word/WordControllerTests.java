package com.learning.hanzitrainer.word;

import com.learning.hanzitrainer.common.InvalidItemsException;
import com.learning.hanzitrainer.common.InvalidItemsException.ItemError;
import com.learning.hanzitrainer.common.NotFoundException;
import com.learning.hanzitrainer.review.CardState;
import com.learning.hanzitrainer.review.CardType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WordController.class)
class WordControllerTests {

    private static final String BODY = """
            {"sourcePageId": 3, "items": [{"hanzi": "朋友", "pinyin": "péngyou", "meaningEn": "friend",
             "acceptMismatch": false}]}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConfirmationService service;

    @MockitoBean
    private WordService wordService;

    private static final WordDto XUEXI = new WordDto(5L, "学习", "xuéxí", "xue2 xi2", "to study", "lernen", "v.", true);

    @Test
    void confirmReturnsCounts() throws Exception {
        when(service.confirm(eq(7L), any())).thenReturn(new ConfirmResult(1, 0, 1,
                List.of(new WordDto(5L, "朋友", "péngyou", "peng2 you5", "friend", null, null, true))));

        mockMvc.perform(post("/api/lessons/7/words/confirm").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.linked").value(1))
                .andExpect(jsonPath("$.words[0].pinyinNumbered").value("peng2 you5"));
    }

    @Test
    void invalidItemsAre400WithErrorsByIndex() throws Exception {
        when(service.confirm(eq(7L), any())).thenThrow(new InvalidItemsException("1 item(s) need attention",
                List.of(new ItemError(0, "了: pinyin 'lè' differs from CC-CEDICT"))));

        mockMvc.perform(post("/api/lessons/7/words/confirm").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid items"))
                .andExpect(jsonPath("$.errors[0].index").value(0))
                .andExpect(jsonPath("$.errors[0].message").value("了: pinyin 'lè' differs from CC-CEDICT"));
    }

    @Test
    void emptyItemListIs400() throws Exception {
        mockMvc.perform(post("/api/lessons/7/words/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\": []}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void searchReturnsPagedWords() throws Exception {
        when(wordService.search(eq(7L), eq("xuexi"), eq(PageRequest.of(1, 20))))
                .thenReturn(new PageImpl<>(List.of(XUEXI), PageRequest.of(1, 20), 21));

        mockMvc.perform(get("/api/words").param("lessonId", "7").param("q", "xuexi").param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].hanzi").value("学习"))
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.totalElements").value(21))
                .andExpect(jsonPath("$.page.totalPages").value(2));
    }

    @Test
    void searchDefaultsToFirstPageOf50() throws Exception {
        when(wordService.search(isNull(), isNull(), eq(PageRequest.of(0, 50)))).thenReturn(Page.empty());

        mockMvc.perform(get("/api/words")).andExpect(status().isOk());
        verify(wordService).search(null, null, PageRequest.of(0, 50));
    }

    @Test
    void oversizedPageIs400() throws Exception {
        mockMvc.perform(get("/api/words").param("size", "201")).andExpect(status().isBadRequest());
        verifyNoInteractions(wordService);
    }

    @Test
    void detailIncludesLessonsAndCards() throws Exception {
        when(wordService.detail(5)).thenReturn(new WordDetailDto(5L, "学习", "xuéxí", "xue2 xi2", "to study",
                null, null, true, List.of(new WordDetailDto.LessonRef(7L, 1, "HSK 1", "我们学习中文。")),
                List.of(new WordDetailDto.CardRef(CardType.WRITING, CardState.NEW, Instant.parse("2026-10-11T10:00:00Z")))));

        mockMvc.perform(get("/api/words/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pinyinNumbered").value("xue2 xi2"))
                .andExpect(jsonPath("$.lessons[0].textbookTitle").value("HSK 1"))
                .andExpect(jsonPath("$.lessons[0].exampleSentence").value("我们学习中文。"))
                .andExpect(jsonPath("$.cards[0].cardType").value("WRITING"))
                .andExpect(jsonPath("$.cards[0].state").value("NEW"));
    }

    @Test
    void unknownWordIs404() throws Exception {
        when(wordService.detail(9)).thenThrow(new NotFoundException("Word", 9));

        mockMvc.perform(get("/api/words/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Word 9 not found"));
    }

    @Test
    void patchUpdatesTheWord() throws Exception {
        when(wordService.update(eq(5L), any())).thenReturn(XUEXI);

        mockMvc.perform(patch("/api/words/5").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"meaningDe\": \"lernen\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meaningDe").value("lernen"));
        verify(wordService).update(5L, new UpdateWordRequest(null, "lernen", null));
    }

    @Test
    void patchWithBlankEnglishMeaningIs400() throws Exception {
        mockMvc.perform(patch("/api/words/5").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"meaningEn\": \"  \"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(wordService);
    }

    @Test
    void deleteAndUnlinkAre204() throws Exception {
        mockMvc.perform(delete("/api/words/5")).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/lessons/7/words/5")).andExpect(status().isNoContent());
        verify(wordService).delete(5);
        verify(wordService).unlink(7, 5);
    }
}
