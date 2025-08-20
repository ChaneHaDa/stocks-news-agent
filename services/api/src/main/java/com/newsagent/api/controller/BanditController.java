package com.newsagent.api.controller;

import com.newsagent.api.entity.News;
import com.newsagent.api.model.NewsItem;
import com.newsagent.api.service.BanditService;
import com.newsagent.api.service.NewsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Multi-Armed Bandit Controller for F5 Implementation
 * Provides real-time recommendation decisions and reward collection
 */
@RestController
@RequestMapping("/bandit")
@Tag(name = "Multi-Armed Bandit", description = "F5: Real-time recommendation optimization")
public class BanditController {
    
    private static final Logger logger = LoggerFactory.getLogger(BanditController.class);
    
    @Autowired
    private BanditService banditService;
    
    @Autowired
    private NewsService newsService;
    
    @Operation(summary = "Get bandit-optimized news recommendations")
    @GetMapping("/recommendations")
    public ResponseEntity<Map<String, Object>> getBanditRecommendations(
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "20") int limit) {
        
        try {
            logger.info("Getting bandit recommendations for user: {}, limit: {}", userId, limit);
            
            // Get candidate news
            List<NewsItem> candidateNewsItems = newsService.getTopNews(limit * 2, null, null, null, "rank", false);
            
            if (candidateNewsItems.isEmpty()) {
                return ResponseEntity.ok(createEmptyResponse("No candidate news available"));
            }
            
            // Make bandit decision
            BanditService.BanditDecisionResponse decision = 
                banditService.makeRecommendationDecision(userId, candidateNewsItems);
            
            // Prepare response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("decision_id", decision.getDecisionId());
            response.put("selected_arm_id", decision.getSelectedArmId());
            response.put("selected_arm_name", decision.getSelectedArmName());
            response.put("selection_reason", decision.getSelectionReason());
            response.put("decision_value", decision.getDecisionValue());
            response.put("recommended_news", decision.getRecommendedNews());
            response.put("total_candidates", candidateNewsItems.size());
            response.put("returned_count", decision.getRecommendedNews().size());
            
            logger.info("Bandit recommendations completed - Arm: {}, Reason: {}, Count: {}", 
                       decision.getSelectedArmName(), decision.getSelectionReason(), 
                       decision.getRecommendedNews().size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to get bandit recommendations for user: {}", userId, e);
            return ResponseEntity.status(500).body(createErrorResponse(
                "Failed to get bandit recommendations: " + e.getMessage()));
        }
    }
    
    @Operation(summary = "Record reward for bandit learning")
    @PostMapping("/reward")
    public ResponseEntity<Map<String, Object>> recordReward(
            @RequestBody RewardRequest request) {
        
        try {
            logger.info("Recording bandit reward - Decision: {}, Type: {}, Value: {}", 
                       request.getDecisionId(), request.getRewardType(), request.getRewardValue());
            
            // Validate request
            if (request.getDecisionId() == null || request.getRewardType() == null || 
                request.getRewardValue() == null || request.getRewardValue() < 0) {
                return ResponseEntity.badRequest().body(createErrorResponse(
                    "Invalid reward request: decision_id, reward_type, and non-negative reward_value are required"));
            }
            
            // Record reward
            banditService.recordReward(
                request.getDecisionId(),
                request.getRewardType(),
                request.getRewardValue(),
                request.getNewsId(),
                request.getUserId()
            );
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Reward recorded successfully");
            response.put("decision_id", request.getDecisionId());
            response.put("reward_type", request.getRewardType());
            response.put("reward_value", request.getRewardValue());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to record bandit reward", e);
            return ResponseEntity.status(500).body(createErrorResponse(
                "Failed to record reward: " + e.getMessage()));
        }
    }
    
    @Operation(summary = "Get bandit performance metrics")
    @GetMapping("/performance")
    public ResponseEntity<Map<String, Object>> getBanditPerformance(
            @RequestParam(defaultValue = "1") Long experimentId,
            @RequestParam(defaultValue = "24") int timeWindowHours) {
        
        try {
            logger.info("Getting bandit performance for experiment: {}, window: {}h", 
                       experimentId, timeWindowHours);
            
            Map<String, Object> performance = banditService.getBanditPerformance(experimentId, timeWindowHours);
            
            if (performance == null) {
                return ResponseEntity.ok(createEmptyResponse("No performance data available"));
            }
            
            performance.put("success", true);
            return ResponseEntity.ok(performance);
            
        } catch (Exception e) {
            logger.error("Failed to get bandit performance", e);
            return ResponseEntity.status(500).body(createErrorResponse(
                "Failed to get performance metrics: " + e.getMessage()));
        }
    }
    
    @Operation(summary = "Record click reward automatically")
    @PostMapping("/click")
    public ResponseEntity<Map<String, Object>> recordClickReward(
            @RequestParam Long decisionId,
            @RequestParam Long newsId,
            @RequestParam(required = false) String userId) {
        
        try {
            // Record click as reward (CTR = 1.0 for click, 0.0 for no click)
            banditService.recordReward(decisionId, "CLICK", 1.0, newsId, userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Click reward recorded");
            response.put("decision_id", decisionId);
            response.put("news_id", newsId);
            response.put("reward_value", 1.0);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to record click reward", e);
            return ResponseEntity.status(500).body(createErrorResponse(
                "Failed to record click: " + e.getMessage()));
        }
    }
    
    @Operation(summary = "Record engagement reward (dwell time)")
    @PostMapping("/engagement")
    public ResponseEntity<Map<String, Object>> recordEngagementReward(
            @RequestParam Long decisionId,
            @RequestParam Long newsId,
            @RequestParam Double dwellTimeSeconds,
            @RequestParam(required = false) String userId) {
        
        try {
            // Normalize dwell time to 0-1 range (max 60 seconds = 1.0)
            Double normalizedReward = Math.min(dwellTimeSeconds / 60.0, 1.0);
            
            banditService.recordReward(decisionId, "ENGAGEMENT", normalizedReward, newsId, userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Engagement reward recorded");
            response.put("decision_id", decisionId);
            response.put("news_id", newsId);
            response.put("dwell_time_seconds", dwellTimeSeconds);
            response.put("normalized_reward", normalizedReward);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to record engagement reward", e);
            return ResponseEntity.status(500).body(createErrorResponse(
                "Failed to record engagement: " + e.getMessage()));
        }
    }
    
    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", message);
        return response;
    }
    
    private Map<String, Object> createEmptyResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("recommended_news", List.of());
        response.put("returned_count", 0);
        return response;
    }
    
    /**
     * Request DTO for reward recording
     */
    public static class RewardRequest {
        private Long decisionId;
        private String rewardType;
        private Double rewardValue;
        private Long newsId;
        private String userId;
        
        // Constructors
        public RewardRequest() {}
        
        // Getters and Setters
        public Long getDecisionId() { return decisionId; }
        public void setDecisionId(Long decisionId) { this.decisionId = decisionId; }
        
        public String getRewardType() { return rewardType; }
        public void setRewardType(String rewardType) { this.rewardType = rewardType; }
        
        public Double getRewardValue() { return rewardValue; }
        public void setRewardValue(Double rewardValue) { this.rewardValue = rewardValue; }
        
        public Long getNewsId() { return newsId; }
        public void setNewsId(Long newsId) { this.newsId = newsId; }
        
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
    }
}