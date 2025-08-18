package com.newsagent.api.service;

import com.newsagent.api.model.NewsItem;
import com.newsagent.api.service.ExperimentBucketingService.ExperimentAssignment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * F2: Experimental News Service with A/B Testing
 * Provides news ranking with experiment-aware variant selection
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExperimentalNewsService {
    
    private final NewsService newsService;
    private final ExperimentBucketingService experimentBucketingService;
    private final AnalyticsLoggingService analyticsLoggingService;
    private final FeatureFlagService featureFlagService;
    
    private static final String RANKING_EXPERIMENT_KEY = "ranking_ab";
    
    /**
     * Get top news with A/B test experiment integration
     */
    public ExperimentalNewsResponse getTopNewsWithExperiment(
            String anonId,
            int limit,
            Set<String> tickerFilters,
            String lang,
            OffsetDateTime since,
            String sort,
            boolean applyDiversity
    ) {
        try {
            // Check if ranking A/B test is enabled
            if (!featureFlagService.isEnabled("experiment.rank_ab.enabled", false)) {
                log.debug("Ranking A/B test disabled, using standard ranking");
                return createControlResponse(anonId, limit, tickerFilters, lang, since, sort, applyDiversity);
            }
            
            // Get experiment assignment for user
            ExperimentAssignment assignment = experimentBucketingService
                .getExperimentAssignment(anonId, RANKING_EXPERIMENT_KEY);
            
            // Get news based on assigned variant
            List<NewsItem> newsItems;
            boolean isPersonalized = false;
            
            if ("treatment".equals(assignment.getVariant()) && assignment.isActive()) {
                // Treatment: Use personalized ranking
                log.debug("User {} in treatment group - using personalized ranking", anonId);
                newsItems = newsService.getTopNews(limit, tickerFilters, lang, since, sort, applyDiversity, true, anonId);
                isPersonalized = true;
            } else {
                // Control: Use standard ranking
                log.debug("User {} in control group - using standard ranking", anonId);
                newsItems = newsService.getTopNews(limit, tickerFilters, lang, since, sort, applyDiversity, false, null);
                isPersonalized = false;
            }
            
            // Log impression for analytics
            logImpression(anonId, assignment, newsItems, isPersonalized, applyDiversity);
            
            return ExperimentalNewsResponse.builder()
                .newsItems(newsItems)
                .experimentKey(assignment.getExperimentKey())
                .variant(assignment.getVariant())
                .isPersonalized(isPersonalized)
                .diversityApplied(applyDiversity)
                .totalResults(newsItems.size())
                .build();
            
        } catch (Exception e) {
            log.error("Error in experimental news service for user {}", anonId, e);
            // Fallback to control
            return createControlResponse(anonId, limit, tickerFilters, lang, since, sort, applyDiversity);
        }
    }
    
    /**
     * Create control response (fallback)
     */
    private ExperimentalNewsResponse createControlResponse(
            String anonId,
            int limit,
            Set<String> tickerFilters,
            String lang,
            OffsetDateTime since,
            String sort,
            boolean applyDiversity
    ) {
        List<NewsItem> newsItems = newsService.getTopNews(
            limit, tickerFilters, lang, since, sort, applyDiversity, false, null);
        
        return ExperimentalNewsResponse.builder()
            .newsItems(newsItems)
            .experimentKey(RANKING_EXPERIMENT_KEY)
            .variant("control")
            .isPersonalized(false)
            .diversityApplied(applyDiversity)
            .totalResults(newsItems.size())
            .build();
    }
    
    /**
     * Log impression with experiment metadata
     */
    private void logImpression(
            String anonId,
            ExperimentAssignment assignment,
            List<NewsItem> newsItems,
            boolean isPersonalized,
            boolean diversityApplied
    ) {
        try {
            if (!featureFlagService.isEnabled("analytics.impression_logging.enabled", true)) {
                return;
            }
            
            // Log each news item impression with experiment context
            for (int i = 0; i < newsItems.size(); i++) {
                NewsItem item = newsItems.get(i);
                
                analyticsLoggingService.logImpressionWithExperiment(
                    anonId,
                    Long.parseLong(item.getId()),
                    i + 1, // position (1-based)
                    "top", // page type
                    assignment.getExperimentKey(),
                    assignment.getVariant(),
                    item.getImportance(),
                    0.0, // rank score - would need to be calculated
                    isPersonalized,
                    diversityApplied
                );
            }
            
            log.debug("Logged {} impressions for user {} in experiment {} variant {}", 
                newsItems.size(), anonId, assignment.getExperimentKey(), assignment.getVariant());
            
        } catch (Exception e) {
            log.error("Failed to log impressions for user {} in experiment", anonId, e);
        }
    }
    
    /**
     * Get experiment status for user
     */
    public Map<String, ExperimentAssignment> getUserExperimentStatus(String anonId) {
        return experimentBucketingService.getAllExperimentAssignments(anonId);
    }
    
    /**
     * Check if user is in specific experiment variant
     */
    public boolean isUserInTreatment(String anonId, String experimentKey) {
        return experimentBucketingService.isInTreatment(anonId, experimentKey);
    }
    
    /**
     * Response wrapper with experiment metadata
     */
    public static class ExperimentalNewsResponse {
        private List<NewsItem> newsItems;
        private String experimentKey;
        private String variant;
        private boolean isPersonalized;
        private boolean diversityApplied;
        private int totalResults;
        
        public static ExperimentalNewsResponseBuilder builder() {
            return new ExperimentalNewsResponseBuilder();
        }
        
        public static class ExperimentalNewsResponseBuilder {
            private List<NewsItem> newsItems;
            private String experimentKey;
            private String variant;
            private boolean isPersonalized;
            private boolean diversityApplied;
            private int totalResults;
            
            public ExperimentalNewsResponseBuilder newsItems(List<NewsItem> newsItems) {
                this.newsItems = newsItems;
                return this;
            }
            
            public ExperimentalNewsResponseBuilder experimentKey(String experimentKey) {
                this.experimentKey = experimentKey;
                return this;
            }
            
            public ExperimentalNewsResponseBuilder variant(String variant) {
                this.variant = variant;
                return this;
            }
            
            public ExperimentalNewsResponseBuilder isPersonalized(boolean isPersonalized) {
                this.isPersonalized = isPersonalized;
                return this;
            }
            
            public ExperimentalNewsResponseBuilder diversityApplied(boolean diversityApplied) {
                this.diversityApplied = diversityApplied;
                return this;
            }
            
            public ExperimentalNewsResponseBuilder totalResults(int totalResults) {
                this.totalResults = totalResults;
                return this;
            }
            
            public ExperimentalNewsResponse build() {
                ExperimentalNewsResponse response = new ExperimentalNewsResponse();
                response.newsItems = this.newsItems;
                response.experimentKey = this.experimentKey;
                response.variant = this.variant;
                response.isPersonalized = this.isPersonalized;
                response.diversityApplied = this.diversityApplied;
                response.totalResults = this.totalResults;
                return response;
            }
        }
        
        // Getters
        public List<NewsItem> getNewsItems() { return newsItems; }
        public String getExperimentKey() { return experimentKey; }
        public String getVariant() { return variant; }
        public boolean isPersonalized() { return isPersonalized; }
        public boolean isDiversityApplied() { return diversityApplied; }
        public int getTotalResults() { return totalResults; }
    }
}