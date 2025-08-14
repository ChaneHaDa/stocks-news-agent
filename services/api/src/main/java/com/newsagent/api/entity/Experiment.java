package com.newsagent.api.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.OffsetDateTime;

/**
 * Experiment configuration for A/B testing
 */
@Entity
@Table(name = "experiment", indexes = {
    @Index(name = "idx_experiment_key", columnList = "experiment_key", unique = true),
    @Index(name = "idx_experiment_active", columnList = "is_active"),
    @Index(name = "idx_experiment_dates", columnList = "start_date, end_date")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Experiment {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "experiment_key", nullable = false, unique = true, length = 100)
    private String experimentKey; // e.g., "rank_personalization_v1"
    
    @Column(name = "name", nullable = false, length = 200)
    private String name; // Human-readable name
    
    @Column(name = "description", length = 1000)
    private String description;
    
    @Column(name = "variants", nullable = false, length = 500)
    private String variants; // JSON array: ["control", "treatment"]
    
    @Column(name = "traffic_allocation", nullable = false, length = 500)
    private String trafficAllocation; // JSON object: {"control": 50, "treatment": 50}
    
    @Column(name = "start_date", nullable = false)
    private OffsetDateTime startDate;
    
    @Column(name = "end_date")
    private OffsetDateTime endDate;
    
    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = false;
    
    @Column(name = "auto_stop_enabled")
    @Builder.Default
    private Boolean autoStopEnabled = true;
    
    @Column(name = "auto_stop_threshold")
    @Builder.Default
    private Double autoStopThreshold = -0.05; // -5% CTR degradation
    
    @Column(name = "minimum_sample_size")
    @Builder.Default
    private Integer minimumSampleSize = 5000; // per variant
    
    @Column(name = "created_by", length = 100)
    private String createdBy;
    
    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
    
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
    
    /**
     * Check if experiment is currently running
     */
    public boolean isRunning() {
        if (!isActive) {
            return false;
        }
        
        OffsetDateTime now = OffsetDateTime.now();
        
        if (now.isBefore(startDate)) {
            return false; // Not started yet
        }
        
        if (endDate != null && now.isAfter(endDate)) {
            return false; // Already ended
        }
        
        return true;
    }
    
    /**
     * Stop experiment (set inactive)
     */
    public void stop(String reason) {
        this.isActive = false;
        this.updatedAt = OffsetDateTime.now();
        // Note: reason could be stored in a separate audit table
    }
}