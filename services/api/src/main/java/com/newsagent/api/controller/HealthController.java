package com.newsagent.api.controller;

import com.newsagent.api.dto.HealthResponse;
import com.newsagent.api.repository.NewsRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Tag(name = "Health", description = "Health Check API")
public class HealthController {

    private final NewsRepository newsRepository;

    @GetMapping("/healthz")
    @Operation(summary = "Health check endpoint", description = "Service health status")
    @ApiResponse(responseCode = "200", description = "Service is healthy")
    public HealthResponse health() {
        return new HealthResponse(true);
    }
    
    @GetMapping("/health/detailed")
    @Operation(summary = "Detailed health check", description = "Detailed service health status with metrics")
    @ApiResponse(responseCode = "200", description = "Detailed health information")
    public ResponseEntity<Map<String, Object>> detailedHealth() {
        try {
            // Check database connectivity
            long totalNews = newsRepository.count();
            
            // Check recent activity
            OffsetDateTime yesterday = OffsetDateTime.now().minus(24, ChronoUnit.HOURS);
            long recentNews = newsRepository.countNewsSince(yesterday);
            
            Map<String, Object> health = Map.of(
                "status", "UP",
                "timestamp", OffsetDateTime.now(),
                "database", Map.of(
                    "status", "UP",
                    "totalNews", totalNews,
                    "newsLast24h", recentNews
                ),
                "application", Map.of(
                    "name", "news-agent-api",
                    "version", "0.1.0"
                )
            );
            
            return ResponseEntity.ok(health);
            
        } catch (Exception e) {
            Map<String, Object> health = Map.of(
                "status", "DOWN",
                "timestamp", OffsetDateTime.now(),
                "error", e.getMessage()
            );
            
            return ResponseEntity.status(503).body(health);
        }
    }
}