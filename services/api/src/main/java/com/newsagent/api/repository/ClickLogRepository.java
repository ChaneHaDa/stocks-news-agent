package com.newsagent.api.repository;

import com.newsagent.api.entity.ClickLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface ClickLogRepository extends JpaRepository<ClickLog, Long> {
    
    // Legacy method (backward compatibility)
    @Query("SELECT cl FROM ClickLog cl WHERE cl.userId = :userId AND cl.clickedAt >= :since ORDER BY cl.clickedAt DESC")
    List<ClickLog> findByUserIdAndClickedAtAfter(@Param("userId") String userId, @Param("since") OffsetDateTime since);
    
    // F0 Enhanced: Anonymous user tracking
    @Query("SELECT cl FROM ClickLog cl WHERE cl.anonId = :anonId AND cl.clickedAt >= :since ORDER BY cl.clickedAt DESC")
    List<ClickLog> findByAnonIdAndClickedAtAfter(@Param("anonId") String anonId, @Param("since") OffsetDateTime since);
    
    @Query("SELECT cl FROM ClickLog cl WHERE cl.news.id = :newsId")
    List<ClickLog> findByNewsId(@Param("newsId") Long newsId);
    
    @Query("SELECT COUNT(cl) FROM ClickLog cl WHERE cl.userId = :userId")
    long countByUserId(@Param("userId") String userId);
    
    @Query("SELECT COUNT(cl) FROM ClickLog cl WHERE cl.anonId = :anonId")
    long countByAnonId(@Param("anonId") String anonId);
    
    @Query("SELECT COUNT(cl) FROM ClickLog cl WHERE cl.clickedAt >= :since")
    long countByClickedAtAfter(@Param("since") OffsetDateTime since);
    
    @Query("SELECT cl.news.id, COUNT(cl) as clickCount FROM ClickLog cl WHERE cl.clickedAt >= :since GROUP BY cl.news.id ORDER BY clickCount DESC")
    List<Object[]> findPopularNewsByClickCount(@Param("since") OffsetDateTime since);
    
    // F0 Enhanced: Experiment analytics
    @Query("SELECT cl.variant, COUNT(cl) FROM ClickLog cl " +
           "WHERE cl.experimentKey = :experimentKey " +
           "AND cl.datePartition BETWEEN :dateFrom AND :dateTo " +
           "GROUP BY cl.variant")
    List<Object[]> countClicksByExperimentAndVariant(@Param("experimentKey") String experimentKey,
                                                     @Param("dateFrom") String dateFrom,
                                                     @Param("dateTo") String dateTo);
    
    /**
     * Get CTR by position for analysis
     */
    @Query("SELECT cl.rankPosition, COUNT(cl) FROM ClickLog cl " +
           "WHERE cl.experimentKey = :experimentKey " +
           "AND cl.datePartition BETWEEN :dateFrom AND :dateTo " +
           "AND cl.rankPosition IS NOT NULL " +
           "GROUP BY cl.rankPosition ORDER BY cl.rankPosition")
    List<Object[]> getClicksByPosition(@Param("experimentKey") String experimentKey,
                                       @Param("dateFrom") String dateFrom,
                                       @Param("dateTo") String dateTo);
    
    /**
     * Clean up old click logs (for GDPR compliance)
     */
    @Query("DELETE FROM ClickLog cl WHERE cl.clickedAt < :cutoffDate")
    void deleteClicksBefore(@Param("cutoffDate") OffsetDateTime cutoffDate);
}