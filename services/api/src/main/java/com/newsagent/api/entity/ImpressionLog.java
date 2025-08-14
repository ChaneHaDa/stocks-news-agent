package com.newsagent.api.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.OffsetDateTime;

/**
 * Impression Log for tracking news article views
 * Essential for A/B testing and personalization metrics
 */
@Entity
@Table(name = "impression_log", indexes = {
    @Index(name = "idx_impression_anon_id", columnList = "anon_id"),
    @Index(name = "idx_impression_news_id", columnList = "news_id"),
    @Index(name = "idx_impression_timestamp", columnList = "timestamp"),
    @Index(name = "idx_impression_experiment", columnList = "experiment_key, variant"),
    @Index(name = "idx_impression_date_anon", columnList = "date_partition, anon_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImpressionLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "anon_id", nullable = false, length = 36)
    private String anonId;
    
    @Column(name = "news_id", nullable = false)
    private Long newsId;
    
    @Column(name = "session_id", length = 100)
    private String sessionId;
    
    @Column(name = "position", nullable = false)
    private Integer position; // Position in the ranked list (1-based)
    
    @Column(name = "page_type", length = 50)
    @Builder.Default
    private String pageType = "top"; // "top", "search", "similar", etc.
    
    @Column(name = "experiment_key", length = 100)
    private String experimentKey;
    
    @Column(name = "variant", length = 50)
    private String variant;
    
    @Column(name = "importance_score")
    private Double importanceScore; // Score at time of impression
    
    @Column(name = "rank_score")
    private Double rankScore; // Final rank score at time of impression
    
    @Column(name = "personalized")
    @Builder.Default
    private Boolean personalized = false;
    
    @Column(name = "diversity_applied")
    @Builder.Default
    private Boolean diversityApplied = true;
    
    @Column(name = "user_agent", length = 500)
    private String userAgent;
    
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    
    @Column(name = "referer", length = 500)
    private String referer;
    
    @Column(name = "timestamp", nullable = false)
    @Builder.Default
    private OffsetDateTime timestamp = OffsetDateTime.now();
    
    @Column(name = "date_partition", nullable = false, length = 10)
    private String datePartition; // YYYY-MM-DD for partitioning
    
    @PrePersist
    private void setDatePartition() {
        if (timestamp != null) {
            this.datePartition = timestamp.toLocalDate().toString();
        }
    }
}