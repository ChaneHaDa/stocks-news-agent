package com.newsagent.api.controller;

import com.newsagent.api.model.NewsItem;
import com.newsagent.api.service.VectorSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * F1: Vector-based similarity search API endpoints
 * Supports both pgvector (PostgreSQL) and manual cosine similarity (H2)
 */
@RestController
@RequestMapping("/api/v1/vector")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Vector Search", description = "Vector-based similarity search operations")
public class VectorSearchController {
    
    private final VectorSearchService vectorSearchService;
    
    @GetMapping("/similar/{newsId}")
    @Operation(
        summary = "Find similar news articles",
        description = "Find news articles similar to the given news ID using vector embeddings. " +
                     "Uses pgvector for PostgreSQL or manual cosine similarity for H2."
    )
    public ResponseEntity<List<NewsItem>> findSimilarNews(
        @Parameter(description = "Target news article ID") 
        @PathVariable Long newsId,
        
        @Parameter(description = "Maximum number of similar articles to return (default: 10)")
        @RequestParam(defaultValue = "10") int limit
    ) {
        try {
            if (limit < 1 || limit > 100) {
                return ResponseEntity.badRequest().build();
            }
            
            List<NewsItem> similarNews = vectorSearchService.findSimilarNews(newsId, limit);
            
            log.debug("Found {} similar news for news ID: {}", similarNews.size(), newsId);
            
            return ResponseEntity.ok(similarNews);
            
        } catch (Exception e) {
            log.error("Failed to find similar news for ID: {}", newsId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @PostMapping("/similar/text")
    @Operation(
        summary = "Find news by semantic text search",
        description = "Find news articles similar to the provided text query using semantic embeddings"
    )
    public ResponseEntity<List<NewsItem>> findSimilarNewsByText(
        @Parameter(description = "Text query for semantic search")
        @RequestBody Map<String, Object> request
    ) {
        try {
            String queryText = (String) request.get("query");
            Integer limit = (Integer) request.getOrDefault("limit", 10);
            
            if (queryText == null || queryText.trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            
            if (limit < 1 || limit > 100) {
                limit = 10;
            }
            
            List<NewsItem> similarNews = vectorSearchService.findSimilarNewsByText(queryText, limit);
            
            log.debug("Found {} similar news for text query: '{}'", similarNews.size(), queryText);
            
            return ResponseEntity.ok(similarNews);
            
        } catch (Exception e) {
            log.error("Failed to find similar news by text", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @PostMapping("/embeddings/backlog")
    @Operation(
        summary = "Process embedding backlog",
        description = "Generate embeddings for news articles that don't have embeddings yet. " +
                     "Used for batch processing and catching up on missing embeddings."
    )
    public ResponseEntity<Map<String, Object>> processEmbeddingBacklog(
        @Parameter(description = "Batch size for processing (default: 50, max: 200)")
        @RequestParam(defaultValue = "50") int batchSize
    ) {
        try {
            if (batchSize < 1 || batchSize > 200) {
                batchSize = 50;
            }
            
            int processed = vectorSearchService.processEmbeddingBacklog(batchSize);
            
            Map<String, Object> response = Map.of(
                "processed", processed,
                "batchSize", batchSize,
                "status", processed > 0 ? "success" : "no_work"
            );
            
            log.info("Processed embedding backlog: {} items", processed);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to process embedding backlog", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/health")
    @Operation(
        summary = "Vector search health check",
        description = "Check the health and status of vector search capabilities"
    )
    public ResponseEntity<Map<String, Object>> healthCheck() {
        try {
            // Basic health check - could be expanded with more detailed status
            Map<String, Object> health = Map.of(
                "status", "healthy",
                "service", "vector-search",
                "capabilities", List.of("similarity_search", "embedding_generation"),
                "timestamp", System.currentTimeMillis()
            );
            
            return ResponseEntity.ok(health);
            
        } catch (Exception e) {
            log.error("Vector search health check failed", e);
            
            Map<String, Object> health = Map.of(
                "status", "unhealthy",
                "service", "vector-search",
                "error", e.getMessage(),
                "timestamp", System.currentTimeMillis()
            );
            
            return ResponseEntity.status(503).body(health);
        }
    }
}