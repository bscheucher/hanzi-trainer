package com.learning.hanzitrainer.cedict;

import com.learning.hanzitrainer.common.ServiceUnavailableException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CedictAdminControllerTests {

    @Nested
    @WebMvcTest(CedictAdminController.class)
    class Enabled {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private CedictImporter importer;

        @Test
        void importReturnsRowsAndDuration() throws Exception {
            when(importer.importConfiguredFile()).thenReturn(new CedictImporter.ImportResult(125251, 4200));

            mockMvc.perform(post("/api/admin/cedict/import"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rows").value(125251))
                    .andExpect(jsonPath("$.durationMs").value(4200));
        }

        @Test
        void missingFileIs503() throws Exception {
            when(importer.importConfiguredFile()).thenThrow(new ServiceUnavailableException("no file"));

            mockMvc.perform(post("/api/admin/cedict/import"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.detail").value("no file"));
        }
    }

    @Nested
    @WebMvcTest(controllers = CedictAdminController.class, properties = "hanzi.admin.enabled=false")
    class Disabled {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private CedictImporter importer;

        @Test
        void endpointDoesNotExist() throws Exception {
            mockMvc.perform(post("/api/admin/cedict/import"))
                    .andExpect(status().isNotFound());
        }
    }
}
