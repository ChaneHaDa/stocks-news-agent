package com.newsagent.api;

import com.newsagent.api.service.NewsIngestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test")
public class AdminControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NewsIngestService newsIngestService;

    @Test
    void testTriggerIngest_Success() throws Exception {
        // Mock successful ingest result
        NewsIngestService.IngestResult result = NewsIngestService.IngestResult.builder()
            .startTime(OffsetDateTime.now().minusMinutes(1))
            .endTime(OffsetDateTime.now())
            .itemsFetched(10)
            .itemsProcessed(10)
            .itemsSaved(8)
            .itemsSkipped(2)
            .errors(0)
            .build();

        when(newsIngestService.ingestAllSources()).thenReturn(result);

        mockMvc.perform(post("/admin/ingest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.itemsFetched", is(10)))
                .andExpect(jsonPath("$.itemsSaved", is(8)))
                .andExpect(jsonPath("$.itemsSkipped", is(2)))
                .andExpect(jsonPath("$.errors", is(0)));
    }

    @Test
    void testTriggerIngest_Failure() throws Exception {
        when(newsIngestService.ingestAllSources()).thenThrow(new RuntimeException("Test error"));

        mockMvc.perform(post("/admin/ingest"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message").value(containsString("Test error")));
    }

    @Test
    void testGetStatus() throws Exception {
        mockMvc.perform(get("/admin/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service", is("news-agent-api")))
                .andExpect(jsonPath("$.status", is("running")))
                .andExpect(jsonPath("$.features.rss_collection", is(true)))
                .andExpect(jsonPath("$.features.scoring", is(true)))
                .andExpect(jsonPath("$.features.scheduling", is(true)));
    }
}