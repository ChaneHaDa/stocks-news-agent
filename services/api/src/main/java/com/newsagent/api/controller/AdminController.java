package com.newsagent.api.controller;

import com.newsagent.api.config.MlServiceConfig;
import com.newsagent.api.service.MlClient;
import com.newsagent.api.service.NewsIngestService;
import com.newsagent.api.service.TopicClusteringService;
import com.newsagent.api.service.EmbeddingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin", description = "Administrative endpoints")
public class AdminController {
    
    private final NewsIngestService newsIngestService;
    private final TopicClusteringService topicClusteringService;
    private final EmbeddingService embeddingService;
    private final MlServiceConfig mlConfig;
    private final MlClient mlClient;
    
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
        // Check ML service health
        boolean mlHealthy = mlClient.isHealthy();
        
        Map<String, Object> mlFeatures = new HashMap<>();
        mlFeatures.put("importance", mlConfig.isEnableImportance());
        mlFeatures.put("summarize", mlConfig.isEnableSummarize());
        mlFeatures.put("embed", mlConfig.isEnableEmbed());
        
        Map<String, Object> mlStatus = new HashMap<>();
        mlStatus.put("healthy", mlHealthy);
        mlStatus.put("base_url", mlConfig.getBaseUrl());
        mlStatus.put("features", mlFeatures);
        
        Map<String, Object> status = new HashMap<>();
        status.put("timestamp", OffsetDateTime.now());
        status.put("service", "news-agent-api");
        status.put("status", "running");
        status.put("features", Map.of(
            "rss_collection", true,
            "scoring", true,
            "scheduling", true
        ));
        status.put("ml_service", mlStatus);
        
        return ResponseEntity.ok(status);
    }
    
    @PostMapping("/features/importance")
    @Operation(summary = "Toggle importance feature", description = "중요도 스코어링 기능 on/off")
    public ResponseEntity<Map<String, Object>> toggleImportance(@RequestParam boolean enabled) {
        log.info("Toggling importance feature: {} -> {}", mlConfig.isEnableImportance(), enabled);
        
        mlConfig.setEnableImportance(enabled);
        
        Map<String, Object> response = new HashMap<>();
        response.put("feature", "importance");
        response.put("enabled", enabled);
        response.put("timestamp", OffsetDateTime.now());
        response.put("message", "Feature " + (enabled ? "enabled" : "disabled"));
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/features/summarize")
    @Operation(summary = "Toggle summarize feature", description = "요약 생성 기능 on/off")
    public ResponseEntity<Map<String, Object>> toggleSummarize(@RequestParam boolean enabled) {
        log.info("Toggling summarize feature: {} -> {}", mlConfig.isEnableSummarize(), enabled);
        
        mlConfig.setEnableSummarize(enabled);
        
        Map<String, Object> response = new HashMap<>();
        response.put("feature", "summarize");
        response.put("enabled", enabled);
        response.put("timestamp", OffsetDateTime.now());
        response.put("message", "Feature " + (enabled ? "enabled" : "disabled"));
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/features/embed")
    @Operation(summary = "Toggle embed feature", description = "임베딩 생성 기능 on/off")
    public ResponseEntity<Map<String, Object>> toggleEmbed(@RequestParam boolean enabled) {
        log.info("Toggling embed feature: {} -> {}", mlConfig.isEnableEmbed(), enabled);
        
        mlConfig.setEnableEmbed(enabled);
        
        Map<String, Object> response = new HashMap<>();
        response.put("feature", "embed");
        response.put("enabled", enabled);
        response.put("timestamp", OffsetDateTime.now());
        response.put("message", "Feature " + (enabled ? "enabled" : "disabled"));
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/features")
    @Operation(summary = "Get all feature flags", description = "모든 feature flag 상태 조회")
    public ResponseEntity<Map<String, Object>> getFeatures() {
        Map<String, Object> features = new HashMap<>();
        features.put("importance", mlConfig.isEnableImportance());
        features.put("summarize", mlConfig.isEnableSummarize());
        features.put("embed", mlConfig.isEnableEmbed());
        features.put("timestamp", OffsetDateTime.now());
        
        return ResponseEntity.ok(features);
    }
    
    @PostMapping("/clustering")
    @Operation(summary = "Manually trigger topic clustering", 
               description = "Triggers immediate topic clustering for recent news articles")
    public ResponseEntity<Map<String, Object>> triggerClustering() {
        log.info("Manual topic clustering triggered");
        
        try {
            TopicClusteringService.ClusteringResult result = topicClusteringService.performClustering();
            
            Map<String, Object> response = Map.of(
                "success", true,
                "message", "Topic clustering completed",
                "startTime", result.getStartTime(),
                "endTime", result.getEndTime(),
                "durationMs", result.getDurationMillis(),
                "totalArticles", result.getTotalArticles(),
                "articlesWithEmbeddings", result.getArticlesWithEmbeddings(),
                "clustersGenerated", result.getClustersGenerated(),
                "duplicateGroupsFound", result.getDuplicateGroupsFound(),
                "topicsAssigned", result.getTopicsAssigned()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Manual topic clustering failed", e);
            
            Map<String, Object> errorResponse = Map.of(
                "success", false,
                "message", "Topic clustering failed: " + e.getMessage(),
                "timestamp", OffsetDateTime.now()
            );
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}