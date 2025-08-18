package com.newsagent.api.service;

import com.newsagent.api.entity.FeatureFlag;
import com.newsagent.api.repository.FeatureFlagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Feature Flag Service for runtime configuration control
 * Provides centralized flag management with caching
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FeatureFlagService {
    
    private final FeatureFlagRepository featureFlagRepository;
    
    /**
     * Get boolean flag value with caching
     */
    @Cacheable(value = "featureFlags", key = "#flagKey")
    public boolean isEnabled(String flagKey) {
        return getValue(flagKey, Boolean.class, false);
    }
    
    /**
     * Get boolean flag value with custom default
     */
    @Cacheable(value = "featureFlags", key = "#flagKey")
    public boolean isEnabled(String flagKey, boolean defaultValue) {
        return getValue(flagKey, Boolean.class, defaultValue);
    }
    
    /**
     * Get string flag value with caching
     */
    @Cacheable(value = "featureFlags", key = "#flagKey")
    public String getString(String flagKey, String defaultValue) {
        return getValue(flagKey, String.class, defaultValue);
    }
    
    /**
     * Get integer flag value with caching
     */
    @Cacheable(value = "featureFlags", key = "#flagKey")
    public int getInteger(String flagKey, int defaultValue) {
        return getValue(flagKey, Integer.class, defaultValue);
    }
    
    /**
     * Get double flag value with caching
     */
    @Cacheable(value = "featureFlags", key = "#flagKey")
    public double getDouble(String flagKey, double defaultValue) {
        return getValue(flagKey, Double.class, defaultValue);
    }
    
    /**
     * Generic method to get flag value
     */
    @SuppressWarnings("unchecked")
    private <T> T getValue(String flagKey, Class<T> type, T defaultValue) {
        try {
            Optional<FeatureFlag> flagOpt = featureFlagRepository.findByFlagKey(flagKey);
            
            if (flagOpt.isPresent()) {
                FeatureFlag flag = flagOpt.get();
                T value = flag.getValue(type);
                return value != null ? value : defaultValue;
            }
            
            log.debug("Feature flag not found: {}, using default: {}", flagKey, defaultValue);
            return defaultValue;
            
        } catch (Exception e) {
            log.error("Error getting feature flag: {}, using default: {}", flagKey, defaultValue, e);
            return defaultValue;
        }
    }
    
    /**
     * Create or update feature flag
     */
    @Transactional
    @CacheEvict(value = "featureFlags", key = "#request.flagKey")
    public FeatureFlag createOrUpdateFlag(CreateFlagRequest request) {
        Optional<FeatureFlag> existingFlag = featureFlagRepository.findByFlagKey(request.getFlagKey());
        
        if (existingFlag.isPresent()) {
            FeatureFlag flag = existingFlag.get();
            flag.updateValue(request.getFlagValue(), request.getUpdatedBy());
            flag.setDescription(request.getDescription());
            flag.setIsEnabled(request.getIsEnabled());
            
            return featureFlagRepository.save(flag);
        } else {
            FeatureFlag newFlag = FeatureFlag.builder()
                .flagKey(request.getFlagKey())
                .name(request.getName())
                .description(request.getDescription())
                .category(request.getCategory())
                .valueType(request.getValueType())
                .flagValue(request.getFlagValue())
                .defaultValue(request.getDefaultValue())
                .isEnabled(request.getIsEnabled())
                .environment(request.getEnvironment())
                .createdBy(request.getCreatedBy())
                .build();
            
            return featureFlagRepository.save(newFlag);
        }
    }
    
    /**
     * Toggle flag on/off
     */
    @Transactional
    @CacheEvict(value = "featureFlags", key = "#flagKey")
    public boolean toggleFlag(String flagKey, String updatedBy) {
        FeatureFlag flag = featureFlagRepository.findByFlagKey(flagKey)
            .orElseThrow(() -> new IllegalArgumentException("Feature flag not found: " + flagKey));
        
        flag.setIsEnabled(!flag.getIsEnabled());
        flag.setUpdatedBy(updatedBy);
        flag.setUpdatedAt(java.time.OffsetDateTime.now());
        
        featureFlagRepository.save(flag);
        
        log.info("Toggled feature flag: {} to {}", flagKey, flag.getIsEnabled());
        return flag.getIsEnabled();
    }
    
    /**
     * Get all flags by category
     */
    @Transactional(readOnly = true)
    public List<FeatureFlag> getFlagsByCategory(String category) {
        return featureFlagRepository.findByCategory(category);
    }
    
    /**
     * Get all enabled flags
     */
    @Transactional(readOnly = true)
    public List<FeatureFlag> getEnabledFlags() {
        return featureFlagRepository.findByIsEnabledTrue();
    }
    
    /**
     * Clear all feature flag cache
     */
    @CacheEvict(value = "featureFlags", allEntries = true)
    public void clearCache() {
        log.info("Cleared all feature flag cache");
    }
    
    /**
     * Initialize default feature flags for F0
     */
    @Transactional
    public void initializeDefaultFlags() {
        createDefaultFlagIfNotExists("experiment.rank_ab.enabled", "false", 
            "Enable A/B testing for ranking algorithm", "experiment", "boolean");
            
        createDefaultFlagIfNotExists("feature.personalization.enabled", "true", 
            "Enable personalization features", "feature", "boolean");
            
        createDefaultFlagIfNotExists("feature.diversity_filter.enabled", "true", 
            "Enable MMR diversity filtering", "feature", "boolean");
            
        createDefaultFlagIfNotExists("analytics.impression_logging.enabled", "true", 
            "Enable impression logging", "analytics", "boolean");
            
        createDefaultFlagIfNotExists("analytics.click_logging.enabled", "true", 
            "Enable click logging", "analytics", "boolean");
            
        createDefaultFlagIfNotExists("config.mmr_lambda", "0.7", 
            "MMR lambda parameter for diversity", "config", "double");
            
        log.info("Initialized default feature flags");
    }
    
    private void createDefaultFlagIfNotExists(String flagKey, String defaultValue, 
                                            String description, String category, String valueType) {
        if (!featureFlagRepository.existsByFlagKey(flagKey)) {
            FeatureFlag flag = FeatureFlag.builder()
                .flagKey(flagKey)
                .name(flagKey.replace(".", " ").toUpperCase())
                .description(description)
                .category(category)
                .valueType(valueType)
                .flagValue(defaultValue)
                .defaultValue(defaultValue)
                .isEnabled(true)
                .environment("all")
                .createdBy("system")
                .build();
                
            featureFlagRepository.save(flag);
        }
    }
    
    @lombok.Builder
    @lombok.Data
    public static class CreateFlagRequest {
        private String flagKey;
        private String name;
        private String description;
        private String category;
        private String valueType;
        private String flagValue;
        private String defaultValue;
        private Boolean isEnabled;
        private String environment;
        private String createdBy;
        private String updatedBy;
    }
}