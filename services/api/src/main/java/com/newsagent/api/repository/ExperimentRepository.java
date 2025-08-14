package com.newsagent.api.repository;

import com.newsagent.api.entity.Experiment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExperimentRepository extends JpaRepository<Experiment, Long> {
    
    /**
     * Find experiment by key
     */
    Optional<Experiment> findByExperimentKey(String experimentKey);
    
    /**
     * Find active experiment by key
     */
    Optional<Experiment> findByExperimentKeyAndIsActive(String experimentKey, Boolean isActive);
    
    /**
     * Find all active experiments
     */
    List<Experiment> findByIsActiveTrue();
    
    /**
     * Find experiments that should be running now
     */
    @Query("SELECT e FROM Experiment e WHERE e.isActive = true " +
           "AND e.startDate <= :now " +
           "AND (e.endDate IS NULL OR e.endDate > :now)")
    List<Experiment> findRunningExperiments(@Param("now") OffsetDateTime now);
    
    /**
     * Find experiments that have ended but are still marked active
     */
    @Query("SELECT e FROM Experiment e WHERE e.isActive = true " +
           "AND e.endDate IS NOT NULL AND e.endDate <= :now")
    List<Experiment> findExpiredActiveExperiments(@Param("now") OffsetDateTime now);
    
    /**
     * Check if experiment key already exists
     */
    boolean existsByExperimentKey(String experimentKey);
}