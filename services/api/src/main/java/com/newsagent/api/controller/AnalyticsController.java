package com.newsagent.api.controller;

import com.newsagent.api.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;

/**
 * Analytics Controller for F0
 * Handles anonymous user identification, experiment assignment, and behavioral logging
 */
@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
@Slf4j
public class AnalyticsController {
    
    private final AnonymousUserService anonymousUserService;
    private final ExperimentService experimentService;
    private final AnalyticsLoggingService analyticsLoggingService;
    private final FeatureFlagService featureFlagService;
    
    /**
     * GET /analytics/anon-id
     * Get or create anonymous user ID
     */
    @GetMapping("/anon-id")
    public ResponseEntity<AnonIdResponse> getAnonId(
            HttpServletRequest request, 
            HttpServletResponse response) {
        
        var anonUser = anonymousUserService.getOrCreateAnonymousUser(request, response);
        
        return ResponseEntity.ok(AnonIdResponse.builder()
            .anonId(anonUser.getAnonId())
            .isNewUser(anonUser.getSessionCount() == 1)
            .newUser(anonUser.getSessionCount() == 1) // For backward compatibility
            .sessionCount(anonUser.getSessionCount())
            .build());
    }
    
    /**
     * GET /analytics/experiment/{experimentKey}/assignment
     * Get experiment variant assignment for anonymous user
     */
    @GetMapping("/experiment/{experimentKey}/assignment")
    public ResponseEntity<ExperimentAssignmentResponse> getExperimentAssignment(
            @PathVariable String experimentKey,
            @RequestParam String anonId) {
        
        var assignment = experimentService.getVariantAssignment(anonId, experimentKey);
        
        return ResponseEntity.ok(ExperimentAssignmentResponse.builder()
            .experimentKey(assignment.getExperimentKey())
            .variant(assignment.getVariant())
            .isActive(assignment.isActive())
            .experimentId(assignment.getExperimentId())
            .build());
    }
    
    /**
     * POST /analytics/impressions
     * Log news impressions for analytics
     */
    @PostMapping("/impressions")
    public ResponseEntity<Void> logImpressions(
            @RequestBody LogImpressionsRequest request,
            HttpServletRequest httpRequest) {
        
        if (!featureFlagService.isEnabled("analytics.impression_logging.enabled")) {
            return ResponseEntity.ok().build();
        }
        
        var metadata = AnalyticsLoggingService.extractRequestMetadata(httpRequest);
        
        var loggingRequest = AnalyticsLoggingService.LogImpressionsRequest.builder()
            .anonId(request.getAnonId())
            .sessionId(request.getSessionId())
            .newsItems(request.getNewsItems().stream()
                .map(item -> AnalyticsLoggingService.NewsItem.builder()
                    .newsId(item.getNewsId())
                    .importanceScore(item.getImportanceScore())
                    .rankScore(item.getRankScore())
                    .build())
                .toList())
            .pageType(request.getPageType())
            .experimentKey(request.getExperimentKey())
            .variant(request.getVariant())
            .personalized(request.getPersonalized())
            .diversityApplied(request.getDiversityApplied())
            .userAgent(metadata.getUserAgent())
            .ipAddress(metadata.getIpAddress())
            .referer(metadata.getReferer())
            .build();
        
        analyticsLoggingService.logImpressions(loggingRequest);
        
        return ResponseEntity.ok().build();
    }
    
    /**
     * POST /analytics/clicks
     * Log news click for analytics
     */
    @PostMapping("/clicks")
    public ResponseEntity<Void> logClick(
            @RequestBody LogClickRequest request,
            HttpServletRequest httpRequest) {
        
        if (!featureFlagService.isEnabled("analytics.click_logging.enabled")) {
            return ResponseEntity.ok().build();
        }
        
        var metadata = AnalyticsLoggingService.extractRequestMetadata(httpRequest);
        
        var loggingRequest = AnalyticsLoggingService.LogClickRequest.builder()
            .anonId(request.getAnonId())
            .newsId(request.getNewsId())
            .sessionId(request.getSessionId())
            .rankPosition(request.getRankPosition())
            .importanceScore(request.getImportanceScore())
            .experimentKey(request.getExperimentKey())
            .variant(request.getVariant())
            .dwellTimeMs(request.getDwellTimeMs())
            .clickSource(request.getClickSource())
            .personalized(request.getPersonalized())
            .userAgent(metadata.getUserAgent())
            .ipAddress(metadata.getIpAddress())
            .referer(metadata.getReferer())
            .build();
        
        analyticsLoggingService.logClick(loggingRequest);
        
        return ResponseEntity.ok().build();
    }
    
    /**
     * GET /analytics/experiment/{experimentKey}/metrics
     * Get experiment metrics for analysis
     */
    @GetMapping("/experiment/{experimentKey}/metrics")
    public ResponseEntity<ExperimentMetricsResponse> getExperimentMetrics(
            @PathVariable String experimentKey,
            @RequestParam(required = false, defaultValue = "7") int days) {
        
        String dateTo = java.time.LocalDate.now().toString();
        String dateFrom = java.time.LocalDate.now().minusDays(days).toString();
        
        var metrics = analyticsLoggingService.getExperimentMetrics(experimentKey, dateFrom, dateTo);
        
        var response = ExperimentMetricsResponse.builder()
            .experimentKey(experimentKey)
            .dateFrom(dateFrom)
            .dateTo(dateTo)
            .impressionsByVariant(metrics.getImpressionsByVariant())
            .clicksByVariant(metrics.getClicksByVariant())
            .ctrByVariant(Map.of())
            .build();
        
        // Calculate CTR for each variant
        for (String variant : metrics.getImpressionsByVariant().keySet()) {
            double ctr = metrics.getCTR(variant);
            response.getCtrByVariant().put(variant, ctr);
        }
        
        return ResponseEntity.ok(response);
    }
    
    // DTOs
    @lombok.Builder
    @lombok.Data
    public static class AnonIdResponse {
        private String anonId;
        private boolean isNewUser;
        private boolean newUser; // For backward compatibility
        private int sessionCount;
    }
    
    @lombok.Builder
    @lombok.Data
    public static class ExperimentAssignmentResponse {
        private String experimentKey;
        private String variant;
        private boolean isActive;
        private Long experimentId;
    }
    
    @lombok.Builder
    @lombok.Data
    public static class LogImpressionsRequest {
        private String anonId;
        private String sessionId;
        private java.util.List<NewsItemDto> newsItems;
        private String pageType;
        private String experimentKey;
        private String variant;
        private Boolean personalized;
        private Boolean diversityApplied;
    }
    
    @lombok.Builder
    @lombok.Data
    public static class NewsItemDto {
        private Long newsId;
        private Double importanceScore;
        private Double rankScore;
    }
    
    @lombok.Builder
    @lombok.Data
    public static class LogClickRequest {
        private String anonId;
        private Long newsId;
        private String sessionId;
        private Integer rankPosition;
        private Double importanceScore;
        private String experimentKey;
        private String variant;
        private Long dwellTimeMs;
        private String clickSource;
        private Boolean personalized;
    }
    
    @lombok.Builder
    @lombok.Data
    public static class ExperimentMetricsResponse {
        private String experimentKey;
        private String dateFrom;
        private String dateTo;
        private Map<String, Long> impressionsByVariant;
        private Map<String, Long> clicksByVariant;
        private Map<String, Double> ctrByVariant;
    }
}