package com.newsagent.api.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.OffsetDateTime;

@Entity
@Table(name = "click_log", indexes = {
    @Index(name = "idx_click_log_user_id", columnList = "user_id"),
    @Index(name = "idx_click_log_anon_id", columnList = "anon_id"),
    @Index(name = "idx_click_log_news_id", columnList = "news_id"),
    @Index(name = "idx_click_log_clicked_at", columnList = "clicked_at"),
    @Index(name = "idx_click_experiment", columnList = "experiment_key, variant"),
    @Index(name = "idx_click_date_anon", columnList = "date_partition, anon_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClickLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // Legacy field for backward compatibility (now maps to anon_id)
    @Column(name = "user_id", length = 100)
    private String userId;
    
    @Column(name = "anon_id", nullable = false, length = 36)
    private String anonId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "news_id", nullable = false)
    private News news;
    
    @Column(name = "clicked_at", nullable = false)
    @Builder.Default
    private OffsetDateTime clickedAt = OffsetDateTime.now();
    
    @Column(name = "session_id", length = 100)
    private String sessionId;
    
    @Column(name = "user_agent", length = 500)
    private String userAgent;
    
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    
    @Column(name = "rank_position")
    private Integer rankPosition; // Position in the ranking when clicked
    
    @Column(name = "importance_score")
    private Double importanceScore; // Importance score at click time
    
    // F0 Enhancement: Experiment tracking
    @Column(name = "experiment_key", length = 100)
    private String experimentKey;
    
    @Column(name = "variant", length = 50)
    private String variant;
    
    @Column(name = "dwell_time_ms")
    private Long dwellTimeMs; // Time spent on article (if tracked)
    
    @Column(name = "click_source", length = 50)
    @Builder.Default
    private String clickSource = "news_list"; // "news_list", "similar", "search"
    
    @Column(name = "personalized")
    @Builder.Default
    private Boolean personalized = false;
    
    @Column(name = "referer", length = 500)
    private String referer;
    
    @Column(name = "date_partition", nullable = false, length = 10)
    private String datePartition; // YYYY-MM-DD for partitioning
    
    @PrePersist
    private void setDatePartition() {
        if (clickedAt != null) {
            this.datePartition = clickedAt.toLocalDate().toString();
        }
        // Backward compatibility: sync userId with anonId
        if (anonId != null) {
            this.userId = anonId;
        }
    }
}