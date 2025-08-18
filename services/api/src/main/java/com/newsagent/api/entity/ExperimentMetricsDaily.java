package com.newsagent.api.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.OffsetDateTime;

/**
 * Daily aggregated metrics for A/B test experiments
 */
@Entity
@Table(name = "experiment_metrics_daily", indexes = {
    @Index(name = "idx_experiment_metrics_daily_key_variant", columnList = "experiment_key, variant"),
    @Index(name = "idx_experiment_metrics_daily_date", columnList = "date_partition"),
    @Index(name = "idx_experiment_metrics_daily_calculated", columnList = "calculated_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExperimentMetricsDaily {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "experiment_key", nullable = false, length = 100)
    private String experimentKey;
    
    @Column(name = "variant", nullable = false, length = 50)
    private String variant;
    
    @Column(name = "date_partition", nullable = false, length = 10)
    private String datePartition; // YYYY-MM-DD
    
    // Core metrics
    @Column(name = "impressions")
    @Builder.Default
    private Long impressions = 0L;
    
    @Column(name = "clicks")
    @Builder.Default
    private Long clicks = 0L;
    
    @Column(name = "unique_users")
    @Builder.Default
    private Long uniqueUsers = 0L;
    
    // Calculated metrics
    @Column(name = "ctr")
    @Builder.Default
    private Double ctr = 0.0;
    
    @Column(name = "avg_dwell_time_ms")
    @Builder.Default
    private Double avgDwellTimeMs = 0.0;
    
    @Column(name = "avg_position")
    @Builder.Default
    private Double avgPosition = 0.0;
    
    // Advanced metrics
    @Column(name = "hide_rate")
    @Builder.Default
    private Double hideRate = 0.0;
    
    @Column(name = "diversity_score")
    @Builder.Default
    private Double diversityScore = 0.0;
    
    @Column(name = "personalization_score")
    @Builder.Default
    private Double personalizationScore = 0.0;
    
    // Metadata
    @Column(name = "calculated_at")
    @Builder.Default
    private OffsetDateTime calculatedAt = OffsetDateTime.now();
    
    @Column(name = "is_final")
    @Builder.Default
    private Boolean isFinal = false;
}