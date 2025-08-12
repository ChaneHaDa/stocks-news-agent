package com.newsagent.api.repository;

import com.newsagent.api.entity.NewsScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NewsScoreRepository extends JpaRepository<NewsScore, Long> {
    
    Optional<NewsScore> findByNewsId(Long newsId);
    
    @Query("SELECT ns FROM NewsScore ns WHERE ns.importance >= :minImportance ORDER BY ns.importance DESC")
    List<NewsScore> findByMinImportance(@Param("minImportance") Double minImportance);
    
    @Query("SELECT AVG(ns.importance) FROM NewsScore ns WHERE ns.createdAt >= :since")
    Optional<Double> findAverageImportanceSince(@Param("since") OffsetDateTime since);
    
    @Query("SELECT MAX(ns.importance) FROM NewsScore ns WHERE ns.createdAt >= :since")
    Optional<Double> findMaxImportanceSince(@Param("since") OffsetDateTime since);
    
    @Query("SELECT COUNT(ns) FROM NewsScore ns WHERE ns.importance >= :minImportance AND ns.createdAt >= :since")
    long countHighImportanceNewsSince(@Param("minImportance") Double minImportance, @Param("since") OffsetDateTime since);
    
    @Modifying
    @Transactional
    @Query("UPDATE NewsScore ns SET ns.importance = :importance, ns.reasonJson = :reasonJson, ns.rankScore = :rankScore, ns.updatedAt = :now WHERE ns.newsId = :newsId")
    int updateScore(
        @Param("newsId") Long newsId,
        @Param("importance") Double importance,
        @Param("reasonJson") String reasonJsonString,
        @Param("rankScore") Double rankScore,
        @Param("now") OffsetDateTime now
    );
}