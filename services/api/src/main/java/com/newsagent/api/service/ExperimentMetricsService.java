package com.newsagent.api.service;

import com.newsagent.api.repository.ImpressionLogRepository;
import com.newsagent.api.repository.ClickLogRepository;
import com.newsagent.api.repository.ExperimentMetricsDailyRepository;
import com.newsagent.api.entity.ExperimentMetricsDaily;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * F2: Experiment Metrics Collection and Calculation Service
 * Calculates CTR, dwell time, diversity, and other A/B test metrics
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExperimentMetricsService {
    
    private final ImpressionLogRepository impressionLogRepository;
    private final ClickLogRepository clickLogRepository;
    private final ExperimentMetricsDailyRepository metricsRepository;
    private final FeatureFlagService featureFlagService;
    
    /**
     * Calculate daily metrics for all active experiments
     * Runs every hour to ensure fresh data
     */
    @Scheduled(fixedRate = 3600000) // 1 hour
    @Transactional
    public void calculateDailyMetrics() {
        if (!featureFlagService.isEnabled("analytics.metrics_calculation.enabled", true)) {
            log.debug("Metrics calculation disabled via feature flag");
            return;
        }
        
        try {
            String today = LocalDate.now().toString();
            String yesterday = LocalDate.now().minusDays(1).toString();
            
            // Calculate metrics for today and yesterday
            calculateMetricsForDate("ranking_ab", today);
            calculateMetricsForDate("ranking_ab", yesterday);
            
            log.info("Completed daily metrics calculation for dates: {}, {}", today, yesterday);
            
        } catch (Exception e) {
            log.error("Failed to calculate daily metrics", e);
        }
    }
    
    /**
     * Calculate metrics for specific experiment and date
     */
    @Transactional
    public void calculateMetricsForDate(String experimentKey, String datePartition) {
        try {
            // Get variants for this experiment
            Set<String> variants = getExperimentVariants(experimentKey, datePartition);
            
            for (String variant : variants) {
                calculateVariantMetrics(experimentKey, variant, datePartition);
            }
            
        } catch (Exception e) {
            log.error("Failed to calculate metrics for experiment {} on date {}", 
                experimentKey, datePartition, e);
        }
    }
    
    /**
     * Calculate metrics for specific variant
     */
    private void calculateVariantMetrics(String experimentKey, String variant, String datePartition) {
        try {
            // Get basic counts
            Long impressions = impressionLogRepository.countByExperimentKeyAndVariantAndDatePartition(
                experimentKey, variant, datePartition);
            
            Long clicks = clickLogRepository.countByExperimentKeyAndVariantAndDatePartition(
                experimentKey, variant, datePartition);
            
            Long uniqueUsers = impressionLogRepository.countDistinctAnonIdByExperimentKeyAndVariantAndDatePartition(
                experimentKey, variant, datePartition);
            
            // Calculate CTR
            double ctr = impressions > 0 ? (double) clicks / impressions : 0.0;
            
            // Calculate average dwell time
            Double avgDwellTime = clickLogRepository.averageDwellTimeByExperimentKeyAndVariantAndDatePartition(
                experimentKey, variant, datePartition);
            if (avgDwellTime == null) avgDwellTime = 0.0;
            
            // Calculate average position
            Double avgPosition = impressionLogRepository.averagePositionByExperimentKeyAndVariantAndDatePartition(
                experimentKey, variant, datePartition);
            if (avgPosition == null) avgPosition = 0.0;
            
            // Calculate advanced metrics
            double hideRate = calculateHideRate(experimentKey, variant, datePartition);
            double diversityScore = calculateDiversityScore(experimentKey, variant, datePartition);
            double personalizationScore = calculatePersonalizationScore(experimentKey, variant, datePartition);
            
            // Save or update metrics
            ExperimentMetricsDaily metrics = metricsRepository
                .findByExperimentKeyAndVariantAndDatePartition(experimentKey, variant, datePartition)
                .orElse(ExperimentMetricsDaily.builder()
                    .experimentKey(experimentKey)
                    .variant(variant)
                    .datePartition(datePartition)
                    .build());
            
            metrics.setImpressions(impressions);
            metrics.setClicks(clicks);
            metrics.setUniqueUsers(uniqueUsers);
            metrics.setCtr(ctr);
            metrics.setAvgDwellTimeMs(avgDwellTime);
            metrics.setAvgPosition(avgPosition);
            metrics.setHideRate(hideRate);
            metrics.setDiversityScore(diversityScore);
            metrics.setPersonalizationScore(personalizationScore);
            metrics.setCalculatedAt(OffsetDateTime.now());
            metrics.setIsFinal(false); // Mark as non-final for ongoing days
            
            metricsRepository.save(metrics);
            
            log.debug("Calculated metrics for experiment {} variant {} on {}: CTR={:.4f}, impressions={}, clicks={}", 
                experimentKey, variant, datePartition, ctr, impressions, clicks);
            
        } catch (Exception e) {
            log.error("Failed to calculate metrics for experiment {} variant {} on date {}", 
                experimentKey, variant, datePartition, e);
        }
    }
    
    /**
     * Get experiment variants for a specific date
     */
    private Set<String> getExperimentVariants(String experimentKey, String datePartition) {
        return impressionLogRepository.findDistinctVariantsByExperimentKeyAndDatePartition(
            experimentKey, datePartition);
    }
    
    /**
     * Calculate hide rate (bounce rate approximation)
     * Users who viewed but didn't click any article
     */
    private double calculateHideRate(String experimentKey, String variant, String datePartition) {
        try {
            Long usersWithImpressions = impressionLogRepository
                .countDistinctAnonIdByExperimentKeyAndVariantAndDatePartition(
                    experimentKey, variant, datePartition);
            
            Long usersWithClicks = clickLogRepository
                .countDistinctAnonIdByExperimentKeyAndVariantAndDatePartition(
                    experimentKey, variant, datePartition);
            
            if (usersWithImpressions == 0) return 0.0;
            
            long usersWithoutClicks = usersWithImpressions - usersWithClicks;
            return (double) usersWithoutClicks / usersWithImpressions;
            
        } catch (Exception e) {
            log.warn("Failed to calculate hide rate for {} {} {}", experimentKey, variant, datePartition, e);
            return 0.0;
        }
    }
    
    /**
     * Calculate diversity score based on topic distribution
     */
    private double calculateDiversityScore(String experimentKey, String variant, String datePartition) {
        try {
            // Get count of impressions with diversity applied
            Long diversityAppliedCount = impressionLogRepository
                .countByExperimentKeyAndVariantAndDatePartitionAndDiversityApplied(
                    experimentKey, variant, datePartition, true);
            
            Long totalImpressions = impressionLogRepository
                .countByExperimentKeyAndVariantAndDatePartition(
                    experimentKey, variant, datePartition);
            
            if (totalImpressions == 0) return 0.0;
            
            // Return percentage of impressions with diversity applied
            return (double) diversityAppliedCount / totalImpressions;
            
        } catch (Exception e) {
            log.warn("Failed to calculate diversity score for {} {} {}", experimentKey, variant, datePartition, e);
            return 0.0;
        }
    }
    
    /**
     * Calculate personalization score
     */
    private double calculatePersonalizationScore(String experimentKey, String variant, String datePartition) {
        try {
            // Get count of impressions with personalization applied
            Long personalizedCount = impressionLogRepository
                .countByExperimentKeyAndVariantAndDatePartitionAndPersonalized(
                    experimentKey, variant, datePartition, true);
            
            Long totalImpressions = impressionLogRepository
                .countByExperimentKeyAndVariantAndDatePartition(
                    experimentKey, variant, datePartition);
            
            if (totalImpressions == 0) return 0.0;
            
            // Return percentage of impressions with personalization applied
            return (double) personalizedCount / totalImpressions;
            
        } catch (Exception e) {
            log.warn("Failed to calculate personalization score for {} {} {}", 
                experimentKey, variant, datePartition, e);
            return 0.0;
        }
    }
    
    /**
     * Get experiment metrics for date range
     */
    @Transactional(readOnly = true)
    public List<ExperimentMetricsDaily> getExperimentMetrics(
            String experimentKey, 
            String dateFrom, 
            String dateTo
    ) {
        return metricsRepository.findByExperimentKeyAndDatePartitionBetweenOrderByDatePartitionDesc(
            experimentKey, dateFrom, dateTo);
    }
    
    /**
     * Get latest metrics for experiment
     */
    @Transactional(readOnly = true)
    public List<ExperimentMetricsDaily> getLatestExperimentMetrics(String experimentKey, int days) {
        String dateFrom = LocalDate.now().minusDays(days).toString();
        String dateTo = LocalDate.now().toString();
        
        return getExperimentMetrics(experimentKey, dateFrom, dateTo);
    }
    
    /**
     * Compare metrics between variants
     */
    @Transactional(readOnly = true)
    public ExperimentComparison compareVariants(String experimentKey, int days) {
        List<ExperimentMetricsDaily> metrics = getLatestExperimentMetrics(experimentKey, days);
        
        // Group by variant
        Map<String, List<ExperimentMetricsDaily>> variantMetrics = new HashMap<>();
        metrics.forEach(metric -> {
            variantMetrics.computeIfAbsent(metric.getVariant(), k -> new ArrayList<>()).add(metric);
        });
        
        // Calculate aggregated metrics for each variant
        Map<String, VariantSummary> summaries = new HashMap<>();
        variantMetrics.forEach((variant, variantData) -> {
            summaries.put(variant, calculateVariantSummary(variantData));
        });
        
        return new ExperimentComparison(experimentKey, summaries, days);
    }
    
    /**
     * Calculate summary for variant
     */
    private VariantSummary calculateVariantSummary(List<ExperimentMetricsDaily> metrics) {
        if (metrics.isEmpty()) {
            return new VariantSummary(0, 0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        }
        
        long totalImpressions = metrics.stream().mapToLong(ExperimentMetricsDaily::getImpressions).sum();
        long totalClicks = metrics.stream().mapToLong(ExperimentMetricsDaily::getClicks).sum();
        long totalUniqueUsers = metrics.stream().mapToLong(ExperimentMetricsDaily::getUniqueUsers).sum();
        
        double avgCtr = metrics.stream().mapToDouble(ExperimentMetricsDaily::getCtr).average().orElse(0.0);
        double avgDwellTime = metrics.stream().mapToDouble(ExperimentMetricsDaily::getAvgDwellTimeMs).average().orElse(0.0);
        double avgPosition = metrics.stream().mapToDouble(ExperimentMetricsDaily::getAvgPosition).average().orElse(0.0);
        double avgHideRate = metrics.stream().mapToDouble(ExperimentMetricsDaily::getHideRate).average().orElse(0.0);
        double avgDiversityScore = metrics.stream().mapToDouble(ExperimentMetricsDaily::getDiversityScore).average().orElse(0.0);
        double avgPersonalizationScore = metrics.stream().mapToDouble(ExperimentMetricsDaily::getPersonalizationScore).average().orElse(0.0);
        
        return new VariantSummary(
            totalImpressions, totalClicks, totalUniqueUsers,
            avgCtr, avgDwellTime, avgPosition, avgHideRate, avgDiversityScore, avgPersonalizationScore
        );
    }
    
    /**
     * Data classes for experiment comparison
     */
    public static class ExperimentComparison {
        private String experimentKey;
        private Map<String, VariantSummary> variants;
        private int daysPeriod;
        
        public ExperimentComparison(String experimentKey, Map<String, VariantSummary> variants, int daysPeriod) {
            this.experimentKey = experimentKey;
            this.variants = variants;
            this.daysPeriod = daysPeriod;
        }
        
        // Getters
        public String getExperimentKey() { return experimentKey; }
        public Map<String, VariantSummary> getVariants() { return variants; }
        public int getDaysPeriod() { return daysPeriod; }
    }
    
    public static class VariantSummary {
        private long totalImpressions;
        private long totalClicks;
        private long totalUniqueUsers;
        private double avgCtr;
        private double avgDwellTimeMs;
        private double avgPosition;
        private double avgHideRate;
        private double avgDiversityScore;
        private double avgPersonalizationScore;
        
        public VariantSummary(long totalImpressions, long totalClicks, long totalUniqueUsers,
                            double avgCtr, double avgDwellTimeMs, double avgPosition,
                            double avgHideRate, double avgDiversityScore, double avgPersonalizationScore) {
            this.totalImpressions = totalImpressions;
            this.totalClicks = totalClicks;
            this.totalUniqueUsers = totalUniqueUsers;
            this.avgCtr = avgCtr;
            this.avgDwellTimeMs = avgDwellTimeMs;
            this.avgPosition = avgPosition;
            this.avgHideRate = avgHideRate;
            this.avgDiversityScore = avgDiversityScore;
            this.avgPersonalizationScore = avgPersonalizationScore;
        }
        
        // Getters
        public long getTotalImpressions() { return totalImpressions; }
        public long getTotalClicks() { return totalClicks; }
        public long getTotalUniqueUsers() { return totalUniqueUsers; }
        public double getAvgCtr() { return avgCtr; }
        public double getAvgDwellTimeMs() { return avgDwellTimeMs; }
        public double getAvgPosition() { return avgPosition; }
        public double getAvgHideRate() { return avgHideRate; }
        public double getAvgDiversityScore() { return avgDiversityScore; }
        public double getAvgPersonalizationScore() { return avgPersonalizationScore; }
    }
}