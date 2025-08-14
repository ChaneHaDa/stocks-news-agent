package com.newsagent.api.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.OffsetDateTime;

@Entity
@Table(name = "user_preference", indexes = {
    @Index(name = "idx_user_preference_user_id", columnList = "user_id"),
    @Index(name = "idx_user_preference_active", columnList = "is_active")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPreference {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_id", nullable = false, length = 100)
    private String userId; // For now, can be session ID or IP-based identifier
    
    @Column(name = "interested_tickers", length = 1000)
    private String interestedTickers; // JSON array: ["005930", "035720", "000660"]
    
    @Column(name = "interested_keywords", length = 1000)
    private String interestedKeywords; // JSON array: ["반도체", "AI", "전기차"]
    
    @Column(name = "diversity_weight")
    @Builder.Default
    private Double diversityWeight = 0.7; // MMR lambda parameter
    
    @Column(name = "personalization_enabled")
    @Builder.Default
    private Boolean personalizationEnabled = false;
    
    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;
    
    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
    
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}