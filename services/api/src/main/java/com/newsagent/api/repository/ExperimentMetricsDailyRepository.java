package com.newsagent.api.repository;

import com.newsagent.api.entity.ExperimentMetricsDaily;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExperimentMetricsDailyRepository extends JpaRepository<ExperimentMetricsDaily, Long> {
    
    Optional<ExperimentMetricsDaily> findByExperimentKeyAndVariantAndDatePartition(
        String experimentKey, String variant, String datePartition);
    
    List<ExperimentMetricsDaily> findByExperimentKeyAndDatePartitionBetweenOrderByDatePartitionDesc(
        String experimentKey, String dateFrom, String dateTo);
    
    List<ExperimentMetricsDaily> findByExperimentKeyOrderByDatePartitionDesc(String experimentKey);
    
    @Query("SELECT emd FROM ExperimentMetricsDaily emd WHERE emd.experimentKey = :experimentKey " +
           "AND emd.datePartition >= :dateFrom ORDER BY emd.datePartition DESC, emd.variant ASC")
    List<ExperimentMetricsDaily> findLatestMetrics(@Param("experimentKey") String experimentKey, 
                                                  @Param("dateFrom") String dateFrom);
}