package com.newsagent.api.repository;

import com.newsagent.api.entity.NewsEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NewsEmbeddingRepository extends JpaRepository<NewsEmbedding, Long> {
    
    Optional<NewsEmbedding> findByNewsId(Long newsId);
    
    @Query("SELECT ne FROM NewsEmbedding ne WHERE ne.news.id IN :newsIds")
    List<NewsEmbedding> findByNewsIdIn(@Param("newsIds") List<Long> newsIds);
    
    @Query("SELECT COUNT(ne) FROM NewsEmbedding ne WHERE ne.modelVersion = :modelVersion")
    long countByModelVersion(@Param("modelVersion") String modelVersion);
    
    @Query("SELECT ne FROM NewsEmbedding ne WHERE ne.dimension = :dimension")
    List<NewsEmbedding> findByDimension(@Param("dimension") Integer dimension);
    
    boolean existsByNewsId(Long newsId);
}