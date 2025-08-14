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
    
    @Query("SELECT cl FROM ClickLog cl WHERE cl.userId = :userId AND cl.clickedAt >= :since ORDER BY cl.clickedAt DESC")
    List<ClickLog> findByUserIdAndClickedAtAfter(@Param("userId") String userId, @Param("since") OffsetDateTime since);
    
    @Query("SELECT cl FROM ClickLog cl WHERE cl.news.id = :newsId")
    List<ClickLog> findByNewsId(@Param("newsId") Long newsId);
    
    @Query("SELECT COUNT(cl) FROM ClickLog cl WHERE cl.userId = :userId")
    long countByUserId(@Param("userId") String userId);
    
    @Query("SELECT COUNT(cl) FROM ClickLog cl WHERE cl.clickedAt >= :since")
    long countByClickedAtAfter(@Param("since") OffsetDateTime since);
    
    @Query("SELECT cl.news.id, COUNT(cl) as clickCount FROM ClickLog cl WHERE cl.clickedAt >= :since GROUP BY cl.news.id ORDER BY clickCount DESC")
    List<Object[]> findPopularNewsByClickCount(@Param("since") OffsetDateTime since);
}