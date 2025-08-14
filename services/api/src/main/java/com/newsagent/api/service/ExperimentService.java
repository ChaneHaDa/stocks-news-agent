package com.newsagent.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.newsagent.api.entity.Experiment;
import com.newsagent.api.repository.ExperimentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Experiment Service for A/B testing
 * Provides consistent experiment variant assignment based on hash(anon_id, experiment_key)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExperimentService {
    
    private final ExperimentRepository experimentRepository;
    private final ObjectMapper objectMapper;
    
    /**
     * Get experiment variant for anonymous user
     * Uses hash(anon_id, experiment_key) mod 100 for consistent assignment
     */
    @Transactional(readOnly = true)
    public ExperimentAssignment getVariantAssignment(String anonId, String experimentKey) {
        Optional<Experiment> experimentOpt = experimentRepository.findByExperimentKeyAndIsActive(experimentKey, true);
        
        if (experimentOpt.isEmpty()) {
            log.debug("Experiment not found or inactive: {}", experimentKey);
            return ExperimentAssignment.builder()
                .experimentKey(experimentKey)
                .variant("control") // Default fallback
                .isActive(false)
                .build();
        }
        
        Experiment experiment = experimentOpt.get();
        
        if (!experiment.isRunning()) {
            log.debug("Experiment not running: {}", experimentKey);
            return ExperimentAssignment.builder()
                .experimentKey(experimentKey)
                .variant("control")
                .isActive(false)
                .build();
        }
        
        // Calculate hash-based assignment
        String variant = calculateVariant(anonId, experimentKey, experiment);
        
        log.debug("Assigned variant '{}' to anon_id '{}' for experiment '{}'", 
            variant, anonId, experimentKey);
        
        return ExperimentAssignment.builder()
            .experimentKey(experimentKey)
            .variant(variant)
            .isActive(true)
            .experimentId(experiment.getId())
            .build();
    }
    
    /**
     * Calculate variant using consistent hashing
     * hash(anon_id + experiment_key) mod 100 determines traffic bucket
     */
    private String calculateVariant(String anonId, String experimentKey, Experiment experiment) {
        try {
            // Parse traffic allocation
            Map<String, Integer> allocation = objectMapper.readValue(
                experiment.getTrafficAllocation(), 
                new TypeReference<Map<String, Integer>>() {}
            );
            
            // Calculate hash
            String hashInput = anonId + ":" + experimentKey;
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(hashInput.getBytes(StandardCharsets.UTF_8));
            
            // Convert to positive integer and get bucket (0-99)
            int bucket = Math.abs(bytesToInt(hash)) % 100;
            
            // Determine variant based on cumulative allocation
            int cumulative = 0;
            for (Map.Entry<String, Integer> entry : allocation.entrySet()) {
                cumulative += entry.getValue();
                if (bucket < cumulative) {
                    return entry.getKey();
                }
            }
            
            // Fallback to first variant if allocation doesn't add up to 100
            return allocation.keySet().iterator().next();
            
        } catch (Exception e) {
            log.error("Error calculating variant for experiment {}: {}", experimentKey, e.getMessage());
            return "control"; // Safe fallback
        }
    }
    
    /**
     * Convert byte array to integer
     */
    private int bytesToInt(byte[] bytes) {
        return ((bytes[0] & 0xFF) << 24) |
               ((bytes[1] & 0xFF) << 16) |
               ((bytes[2] & 0xFF) << 8) |
               (bytes[3] & 0xFF);
    }
    
    /**
     * Get all active experiments
     */
    @Transactional(readOnly = true)
    public List<Experiment> getActiveExperiments() {
        return experimentRepository.findByIsActiveTrue();
    }
    
    /**
     * Create new experiment
     */
    @Transactional
    public Experiment createExperiment(CreateExperimentRequest request) {
        try {
            // Validate variants and allocation
            List<String> variants = request.getVariants();
            Map<String, Integer> allocation = request.getTrafficAllocation();
            
            if (variants.isEmpty()) {
                throw new IllegalArgumentException("Experiment must have at least one variant");
            }
            
            int totalAllocation = allocation.values().stream().mapToInt(Integer::intValue).sum();
            if (totalAllocation != 100) {
                throw new IllegalArgumentException("Traffic allocation must sum to 100%");
            }
            
            Experiment experiment = Experiment.builder()
                .experimentKey(request.getExperimentKey())
                .name(request.getName())
                .description(request.getDescription())
                .variants(objectMapper.writeValueAsString(variants))
                .trafficAllocation(objectMapper.writeValueAsString(allocation))
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .isActive(false) // Start inactive, activate manually
                .minimumSampleSize(request.getMinimumSampleSize())
                .createdBy(request.getCreatedBy())
                .build();
            
            return experimentRepository.save(experiment);
            
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize experiment configuration", e);
        }
    }
    
    /**
     * Activate experiment
     */
    @Transactional
    public void activateExperiment(String experimentKey) {
        Experiment experiment = experimentRepository.findByExperimentKey(experimentKey)
            .orElseThrow(() -> new IllegalArgumentException("Experiment not found: " + experimentKey));
        
        experiment.setIsActive(true);
        experiment.setUpdatedAt(java.time.OffsetDateTime.now());
        experimentRepository.save(experiment);
        
        log.info("Activated experiment: {}", experimentKey);
    }
    
    /**
     * Stop experiment
     */
    @Transactional
    public void stopExperiment(String experimentKey, String reason) {
        Experiment experiment = experimentRepository.findByExperimentKey(experimentKey)
            .orElseThrow(() -> new IllegalArgumentException("Experiment not found: " + experimentKey));
        
        experiment.stop(reason);
        experimentRepository.save(experiment);
        
        log.info("Stopped experiment: {} (reason: {})", experimentKey, reason);
    }
    
    @lombok.Builder
    @lombok.Data
    public static class ExperimentAssignment {
        private String experimentKey;
        private String variant;
        private boolean isActive;
        private Long experimentId;
    }
    
    @lombok.Builder
    @lombok.Data
    public static class CreateExperimentRequest {
        private String experimentKey;
        private String name;
        private String description;
        private List<String> variants;
        private Map<String, Integer> trafficAllocation;
        private java.time.OffsetDateTime startDate;
        private java.time.OffsetDateTime endDate;
        private Integer minimumSampleSize;
        private String createdBy;
    }
}