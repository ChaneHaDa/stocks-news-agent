package com.newsagent.api.controller;

import com.newsagent.api.service.NewsIngestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.Map;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin", description = "Administrative endpoints")
public class AdminController {
    
    private final NewsIngestService newsIngestService;
    
    @PostMapping("/ingest")
    @Operation(summary = "Manually trigger news collection", 
               description = "Triggers immediate collection of news from all RSS sources")
    public ResponseEntity<Map<String, Object>> triggerIngest() {
        log.info("Manual news ingestion triggered");
        
        try {
            NewsIngestService.IngestResult result = newsIngestService.ingestAllSources();
            
            Map<String, Object> response = Map.of(
                "success", true,
                "message", "News ingestion completed",
                "startTime", result.getStartTime(),
                "endTime", result.getEndTime(),
                "durationMs", result.getDurationMillis(),
                "itemsFetched", result.getItemsFetched(),
                "itemsProcessed", result.getItemsProcessed(),
                "itemsSaved", result.getItemsSaved(),
                "itemsSkipped", result.getItemsSkipped(),
                "errors", result.getErrors()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Manual news ingestion failed", e);
            
            Map<String, Object> errorResponse = Map.of(
                "success", false,
                "message", "News ingestion failed: " + e.getMessage(),
                "timestamp", OffsetDateTime.now()
            );
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    @GetMapping("/status")
    @Operation(summary = "Get system status", 
               description = "Returns current system status and configuration")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = Map.of(
            "timestamp", OffsetDateTime.now(),
            "service", "news-agent-api",
            "status", "running",
            "features", Map.of(
                "rss_collection", true,
                "scoring", true,
                "scheduling", true
            )
        );
        
        return ResponseEntity.ok(status);
    }
}