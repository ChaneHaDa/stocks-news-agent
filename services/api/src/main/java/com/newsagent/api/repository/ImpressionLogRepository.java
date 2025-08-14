package com.newsagent.api.repository;

import com.newsagent.api.entity.ImpressionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface ImpressionLogRepository extends JpaRepository<ImpressionLog, Long> {
    
    /**
     * Count impressions by anon_id in date range
     */
    @Query("SELECT COUNT(il) FROM ImpressionLog il WHERE il.anonId = :anonId " +
           "AND il.datePartition BETWEEN :dateFrom AND :dateTo")
    long countByAnonIdInDateRange(@Param("anonId") String anonId, 
                                  @Param("dateFrom") String dateFrom, 
                                  @Param("dateTo") String dateTo);
    
    /**
     * Count impressions by experiment and variant
     */
    @Query("SELECT il.variant, COUNT(il) FROM ImpressionLog il " +
           "WHERE il.experimentKey = :experimentKey " +
           "AND il.datePartition BETWEEN :dateFrom AND :dateTo " +
           "GROUP BY il.variant")
    List<Object[]> countImpressionsByExperimentAndVariant(@Param("experimentKey") String experimentKey,
                                                          @Param("dateFrom") String dateFrom,
                                                          @Param("dateTo") String dateTo);
    
    /**
     * Find impressions for specific experiment in date range
     */
    @Query("SELECT il FROM ImpressionLog il WHERE il.experimentKey = :experimentKey " +
           "AND il.datePartition BETWEEN :dateFrom AND :dateTo " +
           "ORDER BY il.timestamp DESC")
    List<ImpressionLog> findByExperimentInDateRange(@Param("experimentKey") String experimentKey,
                                                    @Param("dateFrom") String dateFrom,
                                                    @Param("dateTo") String dateTo);
    
    /**
     * Get daily impression counts for analytics
     */
    @Query("SELECT il.datePartition, COUNT(il) FROM ImpressionLog il " +
           "WHERE il.datePartition BETWEEN :dateFrom AND :dateTo " +
           "GROUP BY il.datePartition ORDER BY il.datePartition")
    List<Object[]> getDailyImpressionCounts(@Param("dateFrom") String dateFrom,
                                           @Param("dateTo") String dateTo);
    
    /**
     * Clean up old impression logs (for GDPR compliance)
     */
    @Query("DELETE FROM ImpressionLog il WHERE il.timestamp < :cutoffDate")
    void deleteImpressionsBefore(@Param("cutoffDate") OffsetDateTime cutoffDate);
}