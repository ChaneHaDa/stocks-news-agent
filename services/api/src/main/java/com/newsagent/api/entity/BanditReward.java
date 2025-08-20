package com.newsagent.api.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "bandit_reward")
public class BanditReward {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "decision_id", nullable = false)
    private Long decisionId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decision_id", insertable = false, updatable = false)
    private BanditDecision decision;
    
    @Column(name = "reward_type", nullable = false, length = 50)
    private String rewardType;
    
    @Column(name = "reward_value", nullable = false)
    private Double rewardValue;
    
    @Column(name = "news_id")
    private Long newsId;
    
    @Column(name = "user_id")
    private String userId;
    
    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;
    
    // Constructors
    public BanditReward() {
        this.collectedAt = LocalDateTime.now();
    }
    
    public BanditReward(Long decisionId, String rewardType, Double rewardValue, 
                       Long newsId, String userId) {
        this();
        this.decisionId = decisionId;
        this.rewardType = rewardType;
        this.rewardValue = rewardValue;
        this.newsId = newsId;
        this.userId = userId;
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Long getDecisionId() { return decisionId; }
    public void setDecisionId(Long decisionId) { this.decisionId = decisionId; }
    
    public BanditDecision getDecision() { return decision; }
    public void setDecision(BanditDecision decision) { this.decision = decision; }
    
    public String getRewardType() { return rewardType; }
    public void setRewardType(String rewardType) { this.rewardType = rewardType; }
    
    public Double getRewardValue() { return rewardValue; }
    public void setRewardValue(Double rewardValue) { this.rewardValue = rewardValue; }
    
    public Long getNewsId() { return newsId; }
    public void setNewsId(Long newsId) { this.newsId = newsId; }
    
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public LocalDateTime getCollectedAt() { return collectedAt; }
    public void setCollectedAt(LocalDateTime collectedAt) { this.collectedAt = collectedAt; }
}