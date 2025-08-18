package com.newsagent.api.service;

import com.newsagent.api.entity.ExperimentMetricsDaily;
import com.newsagent.api.repository.ExperimentMetricsDailyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * F2: Experiment Auto-Stop System
 * Monitors experiment performance and automatically stops underperforming experiments
 * Triggers when treatment CTR is 5+ percentage points lower than control for 24+ hours
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExperimentAutoStopService {
    
    private final ExperimentMetricsDailyRepository metricsRepository;
    private final FeatureFlagService featureFlagService;
    
    // Auto-stop thresholds
    private static final double CTR_DEGRADATION_THRESHOLD = 0.05; // 5 percentage points
    private static final int MIN_HOURS_FOR_STOP = 24; // Minimum hours before stopping
    private static final long MIN_IMPRESSIONS_THRESHOLD = 1000; // Minimum impressions for reliable metrics
    
    /**
     * Check for experiments that should be auto-stopped
     * Runs every 6 hours to avoid too frequent checks
     */
    @Scheduled(fixedRate = 21600000) // 6 hours
    @Transactional
    public void checkExperimentsForAutoStop() {
        if (!featureFlagService.isEnabled("experiment.auto_stop.enabled", true)) {
            log.debug("Experiment auto-stop disabled via feature flag");
            return;
        }
        
        try {
            // Check all active experiments
            List<String> activeExperiments = getActiveExperiments();
            
            for (String experimentKey : activeExperiments) {
                checkExperimentForAutoStop(experimentKey);
            }
            
            log.info("Completed auto-stop check for {} experiments", activeExperiments.size());
            
        } catch (Exception e) {
            log.error("Failed to run auto-stop check", e);
        }
    }
    
    /**
     * Check specific experiment for auto-stop conditions
     */
    @Transactional
    public void checkExperimentForAutoStop(String experimentKey) {
        try {
            // Skip if experiment is already disabled
            if (!featureFlagService.isEnabled("experiment." + experimentKey + ".enabled", true)) {
                log.debug("Experiment {} already disabled, skipping auto-stop check", experimentKey);
                return;
            }
            
            // Get last 3 days of metrics for analysis
            String dateFrom = LocalDate.now().minusDays(3).toString();
            String dateTo = LocalDate.now().toString();
            
            List<ExperimentMetricsDaily> metrics = metricsRepository
                .findByExperimentKeyAndDatePartitionBetweenOrderByDatePartitionDesc(
                    experimentKey, dateFrom, dateTo);
            
            if (metrics.isEmpty()) {
                log.debug("No metrics found for experiment {}, skipping auto-stop check", experimentKey);
                return;
            }
            
            // Analyze CTR degradation
            AutoStopAnalysis analysis = analyzeExperimentPerformance(experimentKey, metrics);
            
            if (analysis.shouldStop()) {
                executeAutoStop(experimentKey, analysis);
            } else {
                log.debug("Experiment {} performance is within acceptable range: {}", 
                    experimentKey, analysis.getSummary());
            }
            
        } catch (Exception e) {
            log.error("Failed to check experiment {} for auto-stop", experimentKey, e);
        }
    }
    
    /**
     * Analyze experiment performance for auto-stop decision
     */
    private AutoStopAnalysis analyzeExperimentPerformance(
            String experimentKey, 
            List<ExperimentMetricsDaily> metrics) {
        
        // Group metrics by variant and date
        Map<String, List<ExperimentMetricsDaily>> variantMetrics = metrics.stream()
            .collect(Collectors.groupingBy(ExperimentMetricsDaily::getVariant));
        
        // Find control and treatment variants
        List<ExperimentMetricsDaily> controlMetrics = variantMetrics.get("control");
        List<ExperimentMetricsDaily> treatmentMetrics = variantMetrics.get("treatment");
        
        if (controlMetrics == null || treatmentMetrics == null) {
            log.warn("Missing control or treatment metrics for experiment {}", experimentKey);
            return new AutoStopAnalysis(false, "Missing variant data", 0.0, 0.0, 0);
        }
        
        // Calculate daily CTR differences for last 3 days
        List<DailyCtrComparison> dailyComparisons = calculateDailyCtrComparisons(
            controlMetrics, treatmentMetrics);
        
        // Check if degradation threshold is consistently exceeded
        int daysWithDegradation = 0;
        double maxDegradation = 0.0;
        double avgControlCtr = 0.0;
        double avgTreatmentCtr = 0.0;
        
        for (DailyCtrComparison comparison : dailyComparisons) {
            if (comparison.isSignificantDegradation()) {
                daysWithDegradation++;
            }
            maxDegradation = Math.max(maxDegradation, comparison.getDegradation());
            avgControlCtr += comparison.getControlCtr();
            avgTreatmentCtr += comparison.getTreatmentCtr();
        }
        
        avgControlCtr /= dailyComparisons.size();
        avgTreatmentCtr /= dailyComparisons.size();
        
        // Require degradation for at least 1 full day (24+ hours)
        boolean shouldStop = daysWithDegradation >= 1 && maxDegradation >= CTR_DEGRADATION_THRESHOLD;
        
        String summary = String.format(
            "CTR: control=%.4f, treatment=%.4f, max_degradation=%.4f, days_with_degradation=%d",
            avgControlCtr, avgTreatmentCtr, maxDegradation, daysWithDegradation);
        
        return new AutoStopAnalysis(shouldStop, summary, avgControlCtr, avgTreatmentCtr, daysWithDegradation);
    }
    
    /**
     * Calculate daily CTR comparisons between control and treatment
     */
    private List<DailyCtrComparison> calculateDailyCtrComparisons(
            List<ExperimentMetricsDaily> controlMetrics,
            List<ExperimentMetricsDaily> treatmentMetrics) {
        
        Map<String, ExperimentMetricsDaily> controlByDate = controlMetrics.stream()
            .collect(Collectors.toMap(ExperimentMetricsDaily::getDatePartition, m -> m));
        
        Map<String, ExperimentMetricsDaily> treatmentByDate = treatmentMetrics.stream()
            .collect(Collectors.toMap(ExperimentMetricsDaily::getDatePartition, m -> m));
        
        List<DailyCtrComparison> comparisons = new ArrayList<>();
        
        // Compare metrics for each date
        Set<String> allDates = new HashSet<>(controlByDate.keySet());
        allDates.addAll(treatmentByDate.keySet());
        
        for (String date : allDates) {
            ExperimentMetricsDaily control = controlByDate.get(date);
            ExperimentMetricsDaily treatment = treatmentByDate.get(date);
            
            if (control != null && treatment != null) {
                // Only compare if both variants have sufficient data
                if (control.getImpressions() >= MIN_IMPRESSIONS_THRESHOLD && 
                    treatment.getImpressions() >= MIN_IMPRESSIONS_THRESHOLD) {
                    
                    double controlCtr = control.getCtr();
                    double treatmentCtr = treatment.getCtr();
                    double degradation = controlCtr - treatmentCtr;
                    
                    boolean isSignificantDegradation = degradation >= CTR_DEGRADATION_THRESHOLD;
                    
                    comparisons.add(new DailyCtrComparison(
                        date, controlCtr, treatmentCtr, degradation, isSignificantDegradation));
                }
            }
        }
        
        return comparisons;
    }
    
    /**
     * Execute auto-stop for experiment
     */
    private void executeAutoStop(String experimentKey, AutoStopAnalysis analysis) {
        try {
            // Disable experiment via feature flag
            String flagKey = "experiment." + experimentKey + ".enabled";
            featureFlagService.setFeatureFlag(flagKey, false);
            
            log.warn("AUTO-STOPPED experiment {} due to performance degradation: {}", 
                experimentKey, analysis.getSummary());
            
            // TODO: Send alert notification (email, Slack, etc.)
            // TODO: Record auto-stop event in experiment_alert table
            
        } catch (Exception e) {
            log.error("Failed to execute auto-stop for experiment {}", experimentKey, e);
        }
    }
    
    /**
     * Get list of currently active experiments
     */
    private List<String> getActiveExperiments() {
        // For now, return known experiments
        // In production, this could be retrieved from a configuration table
        List<String> experiments = new ArrayList<>();
        
        if (featureFlagService.isEnabled("experiment.ranking_ab.enabled", true)) {
            experiments.add("ranking_ab");
        }
        
        return experiments;
    }
    
    /**
     * Data classes for auto-stop analysis
     */
    private static class AutoStopAnalysis {
        private final boolean shouldStop;
        private final String summary;
        private final double avgControlCtr;
        private final double avgTreatmentCtr;
        private final int daysWithDegradation;
        
        public AutoStopAnalysis(boolean shouldStop, String summary, 
                              double avgControlCtr, double avgTreatmentCtr, 
                              int daysWithDegradation) {
            this.shouldStop = shouldStop;
            this.summary = summary;
            this.avgControlCtr = avgControlCtr;
            this.avgTreatmentCtr = avgTreatmentCtr;
            this.daysWithDegradation = daysWithDegradation;
        }
        
        public boolean shouldStop() { return shouldStop; }
        public String getSummary() { return summary; }
        public double getAvgControlCtr() { return avgControlCtr; }
        public double getAvgTreatmentCtr() { return avgTreatmentCtr; }
        public int getDaysWithDegradation() { return daysWithDegradation; }
    }
    
    private static class DailyCtrComparison {
        private final String date;
        private final double controlCtr;
        private final double treatmentCtr;
        private final double degradation;
        private final boolean isSignificantDegradation;
        
        public DailyCtrComparison(String date, double controlCtr, double treatmentCtr, 
                                double degradation, boolean isSignificantDegradation) {
            this.date = date;
            this.controlCtr = controlCtr;
            this.treatmentCtr = treatmentCtr;
            this.degradation = degradation;
            this.isSignificantDegradation = isSignificantDegradation;
        }
        
        public String getDate() { return date; }
        public double getControlCtr() { return controlCtr; }
        public double getTreatmentCtr() { return treatmentCtr; }
        public double getDegradation() { return degradation; }
        public boolean isSignificantDegradation() { return isSignificantDegradation; }
    }
}