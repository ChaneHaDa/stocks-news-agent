package com.newsagent.api.repository;

import com.newsagent.api.entity.NewsEmbedding;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    
    @Query("SELECT ne FROM NewsEmbedding ne WHERE ne.news.id != :newsId ORDER BY ne.createdAt DESC")
    Page<NewsEmbedding> findByNewsIdNotOrderByCreatedAtDesc(@Param("newsId") Long newsId, Pageable pageable);
    
    @Query("SELECT n.id FROM News n WHERE n.id NOT IN (SELECT ne.news.id FROM NewsEmbedding ne)")
    List<Long> findNewsIdsWithoutEmbeddings(Pageable pageable);
    
    boolean existsByNewsId(Long newsId);
}