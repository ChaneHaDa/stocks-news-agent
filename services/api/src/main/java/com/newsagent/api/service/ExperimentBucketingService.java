package com.newsagent.api.service;

import com.newsagent.api.entity.Experiment;
import com.newsagent.api.repository.ExperimentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * F2: A/B Test Experiment Bucketing Service
 * Provides consistent traffic allocation based on hash(anon_id, experiment_key)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExperimentBucketingService {
    
    private final ExperimentRepository experimentRepository;
    private final FeatureFlagService featureFlagService;
    
    /**
     * Get experiment variant for anonymous user
     * Uses consistent hashing to ensure same user always gets same variant
     */
    @Cacheable(value = "experimentBuckets", key = "#anonId + ':' + #experimentKey")
    public ExperimentAssignment getExperimentAssignment(String anonId, String experimentKey) {
        try {
            // Check if experiment is active
            Optional<Experiment> experiment = getActiveExperiment(experimentKey);
            if (experiment.isEmpty()) {
                log.debug("Experiment {} not active, returning control", experimentKey);
                return ExperimentAssignment.control(experimentKey);
            }
            
            Experiment exp = experiment.get();
            
            // Calculate bucket using consistent hashing
            int bucket = calculateBucket(anonId, experimentKey);
            String variant = assignVariant(bucket, exp);
            
            log.debug("User {} assigned to variant {} for experiment {} (bucket: {})", 
                anonId, variant, experimentKey, bucket);
            
            return ExperimentAssignment.builder()
                .experimentKey(experimentKey)
                .variant(variant)
                .bucket(bucket)
                .experimentId(exp.getId())
                .isActive(true)
                .build();
            
        } catch (Exception e) {
            log.error("Failed to assign experiment variant for user {} in experiment {}", 
                anonId, experimentKey, e);
            return ExperimentAssignment.control(experimentKey);
        }
    }
    
    /**
     * Check if user is in treatment group for a specific experiment
     */
    public boolean isInTreatment(String anonId, String experimentKey) {
        ExperimentAssignment assignment = getExperimentAssignment(anonId, experimentKey);
        return "treatment".equals(assignment.getVariant());
    }
    
    /**
     * Check if user is in control group for a specific experiment
     */
    public boolean isInControl(String anonId, String experimentKey) {
        ExperimentAssignment assignment = getExperimentAssignment(anonId, experimentKey);
        return "control".equals(assignment.getVariant());
    }
    
    /**
     * Get all active experiment assignments for a user
     */
    public Map<String, ExperimentAssignment> getAllExperimentAssignments(String anonId) {
        Map<String, ExperimentAssignment> assignments = new HashMap<>();
        
        // Get all active experiments
        experimentRepository.findByIsActiveTrue().forEach(experiment -> {
            ExperimentAssignment assignment = getExperimentAssignment(anonId, experiment.getExperimentKey());
            assignments.put(experiment.getExperimentKey(), assignment);
        });
        
        return assignments;
    }
    
    /**
     * Calculate consistent bucket (0-99) for user and experiment
     */
    private int calculateBucket(String anonId, String experimentKey) {
        try {
            // Create hash input: anon_id + experiment_key
            String hashInput = anonId + ":" + experimentKey;
            
            // Use SHA-256 for consistent hashing
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(hashInput.getBytes());
            
            // Take first 4 bytes and convert to positive integer
            int hashInt = Math.abs(
                ((hash[0] & 0xFF) << 24) |
                ((hash[1] & 0xFF) << 16) |
                ((hash[2] & 0xFF) << 8) |
                (hash[3] & 0xFF)
            );
            
            // Return bucket 0-99
            return hashInt % 100;
            
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available", e);
            // Fallback to simple hash
            return Math.abs((anonId + experimentKey).hashCode()) % 100;
        }
    }
    
    /**
     * Assign variant based on bucket and experiment configuration
     */
    private String assignVariant(int bucket, Experiment experiment) {
        try {
            // Parse traffic allocation from experiment
            // Format: {"control": 50, "treatment": 50}
            Map<String, Integer> allocation = parseTrafficAllocation(experiment.getTrafficAllocation());
            
            // Calculate cumulative percentages
            int controlEnd = allocation.getOrDefault("control", 50);
            
            if (bucket < controlEnd) {
                return "control";
            } else {
                return "treatment";
            }
            
        } catch (Exception e) {
            log.error("Failed to parse traffic allocation for experiment {}", 
                experiment.getExperimentKey(), e);
            // Default 50/50 split
            return bucket < 50 ? "control" : "treatment";
        }
    }
    
    /**
     * Parse traffic allocation JSON string
     */
    private Map<String, Integer> parseTrafficAllocation(String trafficAllocationJson) {
        Map<String, Integer> allocation = new HashMap<>();
        
        try {
            // Simple JSON parsing for {"control": 50, "treatment": 50}
            // Remove braces and split by comma
            String content = trafficAllocationJson.replaceAll("[{}\\s]", "");
            String[] pairs = content.split(",");
            
            for (String pair : pairs) {
                String[] keyValue = pair.split(":");
                if (keyValue.length == 2) {
                    String key = keyValue[0].replaceAll("\"", "");
                    int value = Integer.parseInt(keyValue[1]);
                    allocation.put(key, value);
                }
            }
            
        } catch (Exception e) {
            log.warn("Failed to parse traffic allocation: {}", trafficAllocationJson, e);
            // Default 50/50 allocation
            allocation.put("control", 50);
            allocation.put("treatment", 50);
        }
        
        return allocation;
    }
    
    /**
     * Get active experiment by key
     */
    private Optional<Experiment> getActiveExperiment(String experimentKey) {
        try {
            Optional<Experiment> experiment = experimentRepository.findByExperimentKey(experimentKey);
            
            if (experiment.isEmpty()) {
                return Optional.empty();
            }
            
            Experiment exp = experiment.get();
            OffsetDateTime now = OffsetDateTime.now();
            
            // Check if experiment is active and within date range
            if (!exp.getIsActive()) {
                return Optional.empty();
            }
            
            if (exp.getStartDate().isAfter(now)) {
                return Optional.empty();
            }
            
            if (exp.getEndDate() != null && exp.getEndDate().isBefore(now)) {
                return Optional.empty();
            }
            
            return Optional.of(exp);
            
        } catch (Exception e) {
            log.error("Failed to check experiment status for key: {}", experimentKey, e);
            return Optional.empty();
        }
    }
    
    /**
     * Experiment assignment result
     */
    public static class ExperimentAssignment {
        private String experimentKey;
        private String variant;
        private int bucket;
        private Long experimentId;
        private boolean isActive;
        
        public static ExperimentAssignment control(String experimentKey) {
            return ExperimentAssignment.builder()
                .experimentKey(experimentKey)
                .variant("control")
                .bucket(-1)
                .experimentId(null)
                .isActive(false)
                .build();
        }
        
        // Lombok builder pattern
        public static ExperimentAssignmentBuilder builder() {
            return new ExperimentAssignmentBuilder();
        }
        
        public static class ExperimentAssignmentBuilder {
            private String experimentKey;
            private String variant;
            private int bucket;
            private Long experimentId;
            private boolean isActive;
            
            public ExperimentAssignmentBuilder experimentKey(String experimentKey) {
                this.experimentKey = experimentKey;
                return this;
            }
            
            public ExperimentAssignmentBuilder variant(String variant) {
                this.variant = variant;
                return this;
            }
            
            public ExperimentAssignmentBuilder bucket(int bucket) {
                this.bucket = bucket;
                return this;
            }
            
            public ExperimentAssignmentBuilder experimentId(Long experimentId) {
                this.experimentId = experimentId;
                return this;
            }
            
            public ExperimentAssignmentBuilder isActive(boolean isActive) {
                this.isActive = isActive;
                return this;
            }
            
            public ExperimentAssignment build() {
                ExperimentAssignment assignment = new ExperimentAssignment();
                assignment.experimentKey = this.experimentKey;
                assignment.variant = this.variant;
                assignment.bucket = this.bucket;
                assignment.experimentId = this.experimentId;
                assignment.isActive = this.isActive;
                return assignment;
            }
        }
        
        // Getters
        public String getExperimentKey() { return experimentKey; }
        public String getVariant() { return variant; }
        public int getBucket() { return bucket; }
        public Long getExperimentId() { return experimentId; }
        public boolean isActive() { return isActive; }
    }
}