package com.newsagent.api.service;

import com.newsagent.api.entity.*;
import com.newsagent.api.repository.ImpressionLogRepository;
import com.newsagent.api.repository.ClickLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Analytics Logging Service for F0
 * Handles impression and click logging for A/B testing and personalization
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsLoggingService {
    
    private final ImpressionLogRepository impressionLogRepository;
    private final ClickLogRepository clickLogRepository;
    
    /**
     * Log news impressions (when articles are shown to user)
     * Called from /news/top endpoint
     */
    @Async
    @Transactional
    public void logImpressions(LogImpressionsRequest request) {
        try {
            for (int i = 0; i < request.getNewsItems().size(); i++) {
                NewsItem newsItem = request.getNewsItems().get(i);
                
                ImpressionLog impression = ImpressionLog.builder()
                    .anonId(request.getAnonId())
                    .newsId(newsItem.getNewsId())
                    .sessionId(request.getSessionId())
                    .position(i + 1) // 1-based position
                    .pageType(request.getPageType())
                    .experimentKey(request.getExperimentKey())
                    .variant(request.getVariant())
                    .importanceScore(newsItem.getImportanceScore())
                    .rankScore(newsItem.getRankScore())
                    .personalized(request.getPersonalized())
                    .diversityApplied(request.getDiversityApplied())
                    .userAgent(request.getUserAgent())
                    .ipAddress(request.getIpAddress())
                    .referer(request.getReferer())
                    .build();
                
                impressionLogRepository.save(impression);
            }
            
            log.debug("Logged {} impressions for anon_id: {}", 
                request.getNewsItems().size(), request.getAnonId());
                
        } catch (Exception e) {
            log.error("Failed to log impressions for anon_id: {}", request.getAnonId(), e);
        }
    }
    
    /**
     * Log news click (when user clicks on article)
     * Enhanced version of existing click logging
     */
    @Async
    @Transactional
    public void logClick(LogClickRequest request) {
        try {
            ClickLog clickLog = ClickLog.builder()
                .anonId(request.getAnonId())
                .news(News.builder().id(request.getNewsId()).build()) // Lazy reference
                .sessionId(request.getSessionId())
                .rankPosition(request.getRankPosition())
                .importanceScore(request.getImportanceScore())
                .experimentKey(request.getExperimentKey())
                .variant(request.getVariant())
                .dwellTimeMs(request.getDwellTimeMs())
                .clickSource(request.getClickSource())
                .personalized(request.getPersonalized())
                .userAgent(request.getUserAgent())
                .ipAddress(request.getIpAddress())
                .referer(request.getReferer())
                .build();
            
            clickLogRepository.save(clickLog);
            
            log.debug("Logged click for anon_id: {} on news_id: {}", 
                request.getAnonId(), request.getNewsId());
                
        } catch (Exception e) {
            log.error("Failed to log click for anon_id: {} on news_id: {}", 
                request.getAnonId(), request.getNewsId(), e);
        }
    }
    
    /**
     * Get CTR (Click-Through Rate) for experiment analysis
     */
    @Transactional(readOnly = true)
    public ExperimentMetrics getExperimentMetrics(String experimentKey, String dateFrom, String dateTo) {
        // Get impression counts by variant
        List<Object[]> impressionCounts = impressionLogRepository
            .countImpressionsByExperimentAndVariant(experimentKey, dateFrom, dateTo);
        
        // Get click counts by variant
        List<Object[]> clickCounts = clickLogRepository
            .countClicksByExperimentAndVariant(experimentKey, dateFrom, dateTo);
        
        // Calculate CTR
        ExperimentMetrics metrics = new ExperimentMetrics();
        
        for (Object[] row : impressionCounts) {
            String variant = (String) row[0];
            Long impressions = (Long) row[1];
            metrics.addImpressions(variant, impressions);
        }
        
        for (Object[] row : clickCounts) {
            String variant = (String) row[0];
            Long clicks = (Long) row[1];
            metrics.addClicks(variant, clicks);
        }
        
        return metrics;
    }
    
    /**
     * Extract request metadata helper
     */
    public static RequestMetadata extractRequestMetadata(HttpServletRequest request) {
        return RequestMetadata.builder()
            .userAgent(request.getHeader("User-Agent"))
            .ipAddress(extractIpAddress(request))
            .referer(request.getHeader("Referer"))
            .build();
    }
    
    private static String extractIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
    
    // DTOs
    @lombok.Builder
    @lombok.Data
    public static class LogImpressionsRequest {
        private String anonId;
        private String sessionId;
        private List<NewsItem> newsItems;
        private String pageType; // "top", "search", "similar"
        private String experimentKey;
        private String variant;
        private Boolean personalized;
        private Boolean diversityApplied;
        private String userAgent;
        private String ipAddress;
        private String referer;
    }
    
    @lombok.Builder
    @lombok.Data
    public static class NewsItem {
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
        private String userAgent;
        private String ipAddress;
        private String referer;
    }
    
    @lombok.Builder
    @lombok.Data
    public static class RequestMetadata {
        private String userAgent;
        private String ipAddress;
        private String referer;
    }
    
    @lombok.Data
    public static class ExperimentMetrics {
        private java.util.Map<String, Long> impressionsByVariant = new java.util.HashMap<>();
        private java.util.Map<String, Long> clicksByVariant = new java.util.HashMap<>();
        
        public void addImpressions(String variant, Long impressions) {
            impressionsByVariant.put(variant, impressions);
        }
        
        public void addClicks(String variant, Long clicks) {
            clicksByVariant.put(variant, clicks);
        }
        
        public double getCTR(String variant) {
            Long impressions = impressionsByVariant.get(variant);
            Long clicks = clicksByVariant.get(variant);
            
            if (impressions == null || impressions == 0) {
                return 0.0;
            }
            
            return (clicks != null ? clicks.doubleValue() : 0.0) / impressions.doubleValue();
        }
    }
}