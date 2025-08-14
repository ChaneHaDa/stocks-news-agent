package com.newsagent.api.repository;

import com.newsagent.api.entity.FeatureFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, Long> {
    
    /**
     * Find feature flag by key
     */
    Optional<FeatureFlag> findByFlagKey(String flagKey);
    
    /**
     * Check if feature flag exists
     */
    boolean existsByFlagKey(String flagKey);
    
    /**
     * Find all enabled flags
     */
    List<FeatureFlag> findByIsEnabledTrue();
    
    /**
     * Find flags by category
     */
    List<FeatureFlag> findByCategory(String category);
    
    /**
     * Find flags by environment
     */
    List<FeatureFlag> findByEnvironment(String environment);
    
    /**
     * Find flags by category and enabled status
     */
    List<FeatureFlag> findByCategoryAndIsEnabled(String category, Boolean isEnabled);
}