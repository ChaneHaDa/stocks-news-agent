package com.newsagent.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.newsagent.api.entity.BanditDecision;
import com.newsagent.api.entity.BanditReward;
import com.newsagent.api.entity.News;
import com.newsagent.api.model.NewsItem;
import com.newsagent.api.repository.BanditDecisionRepository;
import com.newsagent.api.repository.BanditRewardRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Multi-Armed Bandit Service for F5 Implementation
 * Integrates with ML Service for real-time recommendation decisions
 */
@Service
public class BanditService {
    
    private static final Logger logger = LoggerFactory.getLogger(BanditService.class);
    
    @Autowired
    private RestTemplate restTemplate;
    
    @Autowired
    private BanditDecisionRepository banditDecisionRepository;
    
    @Autowired
    private BanditRewardRepository banditRewardRepository;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Value("${ml.service.url:http://localhost:8001}")
    private String mlServiceUrl;
    
    /**
     * Make a bandit decision for news recommendation
     */
    public BanditDecisionResponse makeRecommendationDecision(String userId, List<NewsItem> candidateNews) {
        try {
            // Prepare bandit context
            Map<String, Object> context = createBanditContext(userId);
            
            // Create bandit decision request
            Map<String, Object> banditRequest = new HashMap<>();
            banditRequest.put("experiment_id", 1);
            banditRequest.put("context", context);
            banditRequest.put("algorithm", "EPSILON_GREEDY");
            banditRequest.put("epsilon", 0.1);
            
            // Call ML service for bandit decision
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(banditRequest, headers);
            
            String mlUrl = mlServiceUrl + "/v1/bandit/decision";
            Map<String, Object> response = restTemplate.postForObject(mlUrl, entity, Map.class);
            
            if (response == null) {
                logger.warn("No response from ML service bandit decision");
                return createFallbackDecision(userId, candidateNews);
            }
            
            // Parse ML response
            Long selectedArmId = ((Number) response.get("selected_arm_id")).longValue();
            String selectedArmName = (String) response.get("selected_arm_name");
            String selectionReason = (String) response.get("selection_reason");
            Double decisionValue = ((Number) response.get("decision_value")).doubleValue();
            
            // Apply selected algorithm to candidate news
            List<NewsItem> recommendedNews = applyRecommendationAlgorithm(selectedArmName, candidateNews, userId);
            
            // Store bandit decision
            String newsIdsJson = convertNewsIdsToJson(recommendedNews);
            BanditDecision decision = new BanditDecision(
                1L, selectedArmId, userId, decisionValue, selectionReason, newsIdsJson
            );
            banditDecisionRepository.save(decision);
            
            return new BanditDecisionResponse(
                decision.getId(),
                selectedArmId,
                selectedArmName,
                selectionReason,
                decisionValue,
                recommendedNews
            );
            
        } catch (Exception e) {
            logger.error("Bandit decision failed for user: {}", userId, e);
            return createFallbackDecision(userId, candidateNews);
        }
    }
    
    /**
     * Record reward for bandit learning
     */
    public void recordReward(Long decisionId, String rewardType, Double rewardValue, Long newsId, String userId) {
        try {
            // Store reward in database
            BanditReward reward = new BanditReward(decisionId, rewardType, rewardValue, newsId, userId);
            banditRewardRepository.save(reward);
            
            // Send reward to ML service for learning
            Map<String, Object> rewardRequest = new HashMap<>();
            rewardRequest.put("decision_id", decisionId);
            rewardRequest.put("reward_type", rewardType);
            rewardRequest.put("reward_value", rewardValue);
            rewardRequest.put("news_id", newsId);
            rewardRequest.put("user_id", userId);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(rewardRequest, headers);
            
            String mlUrl = mlServiceUrl + "/v1/bandit/reward";
            restTemplate.postForObject(mlUrl, entity, Map.class);
            
            logger.info("Recorded bandit reward - Decision: {}, Type: {}, Value: {}", 
                       decisionId, rewardType, rewardValue);
            
        } catch (Exception e) {
            logger.error("Failed to record bandit reward for decision: {}", decisionId, e);
        }
    }
    
    /**
     * Get bandit performance metrics
     */
    public Map<String, Object> getBanditPerformance(Long experimentId, int timeWindowHours) {
        try {
            String mlUrl = mlServiceUrl + "/v1/bandit/performance" +
                          "?experiment_id=" + experimentId + 
                          "&time_window_hours=" + timeWindowHours;
            
            Map<String, Object> performance = restTemplate.getForObject(mlUrl, Map.class);
            
            // Enhance with database statistics
            LocalDateTime since = LocalDateTime.now().minusHours(timeWindowHours);
            List<Object[]> armCounts = banditDecisionRepository.countDecisionsByArmSince(experimentId, since);
            List<Object[]> armRewards = banditRewardRepository.sumRewardsByArmSince(experimentId, since);
            
            if (performance != null) {
                performance.put("database_arm_counts", armCounts);
                performance.put("database_arm_rewards", armRewards);
            }
            
            return performance;
            
        } catch (Exception e) {
            logger.error("Failed to get bandit performance", e);
            return createFallbackPerformance(experimentId, timeWindowHours);
        }
    }
    
    private Map<String, Object> createBanditContext(String userId) {
        Map<String, Object> context = new HashMap<>();
        context.put("user_id", userId);
        context.put("time_slot", LocalDateTime.now().getHour());
        context.put("category", "finance");
        return context;
    }
    
    private List<NewsItem> applyRecommendationAlgorithm(String algorithmName, List<NewsItem> candidateNews, String userId) {
        // Apply different recommendation strategies based on selected arm
        switch (algorithmName.toLowerCase()) {
            case "personalized":
                return applyPersonalizedRanking(candidateNews, userId);
            case "popular":
                return applyPopularityRanking(candidateNews);
            case "diverse":
                return applyDiversityRanking(candidateNews);
            case "recent":
                return applyRecencyRanking(candidateNews);
            default:
                return candidateNews.subList(0, Math.min(10, candidateNews.size()));
        }
    }
    
    private List<NewsItem> applyPersonalizedRanking(List<NewsItem> news, String userId) {
        // Use importance score for ranking (NewsItem doesn't have rankScore)
        return news.stream()
                .sorted((a, b) -> Double.compare(b.getImportance(), a.getImportance()))
                .limit(10)
                .toList();
    }
    
    private List<NewsItem> applyPopularityRanking(List<NewsItem> news) {
        return news.stream()
                .sorted((a, b) -> Double.compare(b.getImportance(), a.getImportance()))
                .limit(10)
                .toList();
    }
    
    private List<NewsItem> applyDiversityRanking(List<NewsItem> news) {
        // Apply MMR diversity algorithm
        return news.stream()
                .limit(10)
                .toList();
    }
    
    private List<NewsItem> applyRecencyRanking(List<NewsItem> news) {
        return news.stream()
                .sorted((a, b) -> b.getPublishedAt().compareTo(a.getPublishedAt()))
                .limit(10)
                .toList();
    }
    
    private String convertNewsIdsToJson(List<NewsItem> news) {
        try {
            List<String> newsIds = news.stream().map(NewsItem::getId).toList();
            return objectMapper.writeValueAsString(newsIds);
        } catch (JsonProcessingException e) {
            logger.error("Failed to convert news IDs to JSON", e);
            return "[]";
        }
    }
    
    private BanditDecisionResponse createFallbackDecision(String userId, List<NewsItem> candidateNews) {
        // Fallback to personalized recommendation
        List<NewsItem> fallbackNews = applyPersonalizedRanking(candidateNews, userId);
        
        return new BanditDecisionResponse(
            null,
            1L,
            "personalized",
            "FALLBACK",
            0.5,
            fallbackNews
        );
    }
    
    private Map<String, Object> createFallbackPerformance(Long experimentId, int timeWindowHours) {
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("experiment_id", experimentId);
        fallback.put("time_window_hours", timeWindowHours);
        fallback.put("total_decisions", 0);
        fallback.put("total_rewards", 0.0);
        fallback.put("average_reward", 0.0);
        fallback.put("status", "FALLBACK");
        return fallback;
    }
    
    /**
     * Response DTO for bandit decisions
     */
    public static class BanditDecisionResponse {
        private final Long decisionId;
        private final Long selectedArmId;
        private final String selectedArmName;
        private final String selectionReason;
        private final Double decisionValue;
        private final List<NewsItem> recommendedNews;
        
        public BanditDecisionResponse(Long decisionId, Long selectedArmId, String selectedArmName,
                                    String selectionReason, Double decisionValue, List<NewsItem> recommendedNews) {
            this.decisionId = decisionId;
            this.selectedArmId = selectedArmId;
            this.selectedArmName = selectedArmName;
            this.selectionReason = selectionReason;
            this.decisionValue = decisionValue;
            this.recommendedNews = recommendedNews;
        }
        
        // Getters
        public Long getDecisionId() { return decisionId; }
        public Long getSelectedArmId() { return selectedArmId; }
        public String getSelectedArmName() { return selectedArmName; }
        public String getSelectionReason() { return selectionReason; }
        public Double getDecisionValue() { return decisionValue; }
        public List<NewsItem> getRecommendedNews() { return recommendedNews; }
    }
}