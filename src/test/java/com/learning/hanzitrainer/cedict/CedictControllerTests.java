package com.learning.hanzitrainer.cedict;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.stream.IntStream;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CedictController.class)
class CedictControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CedictService cedictService;

    @Test
    void returnsEntries() throws Exception {
        when(cedictService.lookup("学习")).thenReturn(List.of(entry(1, "学习", "xue2 xi2")));

        mockMvc.perform(get("/api/dictionary").param("q", "学习"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].simplified").value("学习"))
                .andExpect(jsonPath("$[0].traditional").value("學習"))
                .andExpect(jsonPath("$[0].pinyin").value("xuéxí"))
                .andExpect(jsonPath("$[0].pinyinNumbered").value("xue2 xi2"))
                .andExpect(jsonPath("$[0].definitions[1]").value("to study"));
    }

    @Test
    void returnsAtMost20Entries() throws Exception {
        when(cedictService.lookup("了"))
                .thenReturn(IntStream.rangeClosed(1, 25).mapToObj(i -> entry(i, "了", "le5")).toList());

        mockMvc.perform(get("/api/dictionary").param("q", "了"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(20));
    }

    @Test
    void blankQueryIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/dictionary").param("q", " "))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mockMvc.perform(get("/api/dictionary"))
                .andExpect(status().isBadRequest());
    }

    private static CedictEntry entry(long id, String simplified, String pinyinNumbered) {
        CedictEntry e = new CedictEntry(simplified, "學習", pinyinNumbered,
                PinyinConverter.toToneMarks(pinyinNumbered), List.of("to learn", "to study"));
        ReflectionTestUtils.setField(e, "id", id);
        return e;
    }
}
