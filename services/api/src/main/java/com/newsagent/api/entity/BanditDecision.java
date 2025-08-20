package com.newsagent.api.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "bandit_decision")
public class BanditDecision {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "experiment_id", nullable = false)
    private Long experimentId;
    
    @Column(name = "arm_id", nullable = false)
    private Long armId;
    
    @Column(name = "context_id")
    private Long contextId;
    
    @Column(name = "user_id")
    private String userId;
    
    @Column(name = "decision_value")
    private Double decisionValue;
    
    @Column(name = "selection_reason", length = 100)
    private String selectionReason;
    
    @Column(name = "news_ids", columnDefinition = "TEXT")
    private String newsIds; // JSON array of news IDs
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    // Constructors
    public BanditDecision() {
        this.createdAt = LocalDateTime.now();
    }
    
    public BanditDecision(Long experimentId, Long armId, String userId, 
                         Double decisionValue, String selectionReason, String newsIds) {
        this();
        this.experimentId = experimentId;
        this.armId = armId;
        this.userId = userId;
        this.decisionValue = decisionValue;
        this.selectionReason = selectionReason;
        this.newsIds = newsIds;
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Long getExperimentId() { return experimentId; }
    public void setExperimentId(Long experimentId) { this.experimentId = experimentId; }
    
    public Long getArmId() { return armId; }
    public void setArmId(Long armId) { this.armId = armId; }
    
    public Long getContextId() { return contextId; }
    public void setContextId(Long contextId) { this.contextId = contextId; }
    
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public Double getDecisionValue() { return decisionValue; }
    public void setDecisionValue(Double decisionValue) { this.decisionValue = decisionValue; }
    
    public String getSelectionReason() { return selectionReason; }
    public void setSelectionReason(String selectionReason) { this.selectionReason = selectionReason; }
    
    public String getNewsIds() { return newsIds; }
    public void setNewsIds(String newsIds) { this.newsIds = newsIds; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}