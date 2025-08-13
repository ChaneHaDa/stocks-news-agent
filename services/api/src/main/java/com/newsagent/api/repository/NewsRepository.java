package com.newsagent.api.repository;

import com.newsagent.api.entity.News;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NewsRepository extends JpaRepository<News, Long> {
    
    Optional<News> findByDedupKey(String dedupKey);
    
    boolean existsByDedupKey(String dedupKey);
    
    List<News> findBySourceOrderByPublishedAtDesc(String source);
    
    @Query("SELECT n FROM News n WHERE n.publishedAt >= :since ORDER BY n.publishedAt DESC")
    List<News> findRecentNews(@Param("since") OffsetDateTime since);
    
    @Query("SELECT n FROM News n " +
           "LEFT JOIN n.newsScore ns " +
           "ORDER BY COALESCE(ns.importance, 0) DESC, n.publishedAt DESC")
    Page<News> findTopNewsByImportance(Pageable pageable);
    
    @Query("SELECT n FROM News n " +
           "LEFT JOIN n.newsScore ns " +
           "WHERE (:minImportance IS NULL OR ns.importance >= :minImportance) " +
           "AND (:sources IS NULL OR n.source IN :sources) " +
           "AND (:since IS NULL OR n.publishedAt >= :since) " +
           "ORDER BY COALESCE(ns.importance, 0) DESC, n.publishedAt DESC")
    Page<News> findNewsWithFilters(
        @Param("minImportance") Double minImportance,
        @Param("sources") List<String> sources,
        @Param("since") OffsetDateTime since,
        Pageable pageable
    );
    
    @Query("SELECT COUNT(n) FROM News n WHERE n.createdAt >= :since")
    long countNewsSince(@Param("since") OffsetDateTime since);
    
    @Query("SELECT n.source, COUNT(n) FROM News n WHERE n.createdAt >= :since GROUP BY n.source")
    List<Object[]> countNewsBySourceSince(@Param("since") OffsetDateTime since);
    
    @Query("SELECT n FROM News n ORDER BY n.publishedAt DESC")
    Page<News> findByOrderByPublishedAtDesc(Pageable pageable);
}