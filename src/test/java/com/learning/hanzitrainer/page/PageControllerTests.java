package com.learning.hanzitrainer.page;

import com.learning.hanzitrainer.common.ServiceUnavailableException;
import com.learning.hanzitrainer.common.UnsupportedMediaTypeException;
import com.learning.hanzitrainer.extraction.FakeVocabularyExtractor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PageController.class)
class PageControllerTests {

    private static final MockMultipartFile FILE =
            new MockMultipartFile("file", "page.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PageService service;

    @Test
    void uploadReturns202WithPendingPage() throws Exception {
        when(service.upload(eq(3L), any(), eq(12))).thenReturn(page(ExtractionStatus.PENDING, null));

        mockMvc.perform(multipart("/api/lessons/3/pages").file(FILE).param("pageNo", "12"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.lessonId").value(3))
                .andExpect(jsonPath("$.extraction").doesNotExist())
                .andExpect(jsonPath("$.confirmed").value(false));
    }

    @Test
    void missingFileIsBadRequest() throws Exception {
        mockMvc.perform(multipart("/api/lessons/3/pages")).andExpect(status().isBadRequest());
    }

    @Test
    void unsupportedTypeIs415() throws Exception {
        when(service.upload(anyLong(), any(), any())).thenThrow(new UnsupportedMediaTypeException("JPEG or PNG"));

        mockMvc.perform(multipart("/api/lessons/3/pages").file(FILE))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.detail").value("JPEG or PNG"));
    }

    @Test
    void tooLargeIs413() throws Exception {
        when(service.upload(anyLong(), any(), any())).thenThrow(new MaxUploadSizeExceededException(20_000_000));

        mockMvc.perform(multipart("/api/lessons/3/pages").file(FILE))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.status").value(413));
    }

    @Test
    void llmNotConfiguredIs503() throws Exception {
        when(service.upload(anyLong(), any(), any())).thenThrow(new ServiceUnavailableException("set ANTHROPIC_API_KEY"));

        mockMvc.perform(multipart("/api/lessons/3/pages").file(FILE))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("set ANTHROPIC_API_KEY"));
    }

    @Test
    void getReturnsExtraction() throws Exception {
        when(service.get(8L)).thenReturn(page(ExtractionStatus.DONE, FakeVocabularyExtractor.SAMPLE));

        mockMvc.perform(get("/api/pages/8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extraction.pageType").value("VOCABULARY_LIST"))
                .andExpect(jsonPath("$.extraction.words[0].hanzi").value("朋友"))
                .andExpect(jsonPath("$.extraction.words[0].pinyinFromPage").value(true))
                .andExpect(jsonPath("$.candidates").doesNotExist());
    }

    @Test
    void imageIsServedWithItsMimeType() throws Exception {
        when(service.image(8L)).thenReturn(new PageService.StoredImage(new byte[]{1, 2, 3}, "image/png"));

        mockMvc.perform(get("/api/pages/8/image"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(content().bytes(new byte[]{1, 2, 3}));
    }

    @Test
    void rerunReturns202AndDeleteReturns204() throws Exception {
        when(service.rerun(8L)).thenReturn(page(ExtractionStatus.PENDING, null));

        mockMvc.perform(post("/api/pages/8/extract")).andExpect(status().isAccepted());
        mockMvc.perform(delete("/api/pages/8")).andExpect(status().isNoContent());
    }

    private static PageDto page(ExtractionStatus status, com.learning.hanzitrainer.extraction.ExtractionResult result) {
        return new PageDto(8L, 3L, 12, status, result, null, false, null, Instant.parse("2026-10-10T12:00:00Z"));
    }
}
