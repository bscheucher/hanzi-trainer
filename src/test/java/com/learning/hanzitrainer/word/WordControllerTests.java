package com.learning.hanzitrainer.word;

import com.learning.hanzitrainer.common.InvalidItemsException;
import com.learning.hanzitrainer.common.InvalidItemsException.ItemError;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
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
}
