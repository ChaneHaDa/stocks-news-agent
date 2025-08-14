package com.newsagent.api.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.OffsetDateTime;

/**
 * Feature Flag Entity for runtime configuration control
 */
@Entity
@Table(name = "feature_flag", indexes = {
    @Index(name = "idx_feature_flag_key", columnList = "flag_key", unique = true),
    @Index(name = "idx_feature_flag_enabled", columnList = "is_enabled"),
    @Index(name = "idx_feature_flag_category", columnList = "category")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeatureFlag {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "flag_key", nullable = false, unique = true, length = 100)
    private String flagKey; // e.g., "experiment.rank_ab.enabled"
    
    @Column(name = "name", nullable = false, length = 200)
    private String name; // Human-readable name
    
    @Column(name = "description", length = 1000)
    private String description;
    
    @Column(name = "category", length = 50)
    @Builder.Default
    private String category = "general"; // "experiment", "feature", "config"
    
    @Column(name = "value_type", nullable = false, length = 20)
    @Builder.Default
    private String valueType = "boolean"; // "boolean", "string", "integer", "double"
    
    @Column(name = "flag_value", nullable = false, length = 500)
    private String flagValue; // Stored as string, parsed based on valueType
    
    @Column(name = "default_value", length = 500)
    private String defaultValue;
    
    @Column(name = "is_enabled")
    @Builder.Default
    private Boolean isEnabled = true;
    
    @Column(name = "environment", length = 20)
    @Builder.Default
    private String environment = "all"; // "development", "staging", "production", "all"
    
    @Column(name = "created_by", length = 100)
    private String createdBy;
    
    @Column(name = "updated_by", length = 100)
    private String updatedBy;
    
    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
    
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
    
    /**
     * Get typed value
     */
    @SuppressWarnings("unchecked")
    public <T> T getValue(Class<T> type) {
        if (!isEnabled) {
            return parseValue(defaultValue, type);
        }
        
        return parseValue(flagValue, type);
    }
    
    @SuppressWarnings("unchecked")
    private <T> T parseValue(String value, Class<T> type) {
        if (value == null) {
            return null;
        }
        
        if (type == Boolean.class || type == boolean.class) {
            return (T) Boolean.valueOf(value);
        }
        
        if (type == String.class) {
            return (T) value;
        }
        
        if (type == Integer.class || type == int.class) {
            return (T) Integer.valueOf(value);
        }
        
        if (type == Double.class || type == double.class) {
            return (T) Double.valueOf(value);
        }
        
        throw new IllegalArgumentException("Unsupported type: " + type);
    }
    
    /**
     * Update flag value
     */
    public void updateValue(String newValue, String updatedBy) {
        this.flagValue = newValue;
        this.updatedBy = updatedBy;
        this.updatedAt = OffsetDateTime.now();
    }
}