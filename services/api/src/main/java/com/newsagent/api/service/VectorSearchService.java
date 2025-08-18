package com.newsagent.api.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.newsagent.api.entity.NewsEmbedding;
import com.newsagent.api.repository.NewsEmbeddingRepository;
import com.newsagent.api.repository.NewsRepository;
import com.newsagent.api.model.NewsItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

/**
 * Vector-based similarity search service
 * Supports both H2 (JSON cosine) and PostgreSQL (pgvector) backends
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VectorSearchService {
    
    private final NewsEmbeddingRepository newsEmbeddingRepository;
    private final NewsRepository newsRepository;
    private final NewsService newsService;
    private final EmbeddingService embeddingService;
    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    
    @Value("${app.database.type:auto}")
    private String databaseType;
    
    /**
     * Find similar news articles using vector similarity
     */
    public List<NewsItem> findSimilarNews(Long newsId, int limit) {
        try {
            // Get the target news embedding
            Optional<NewsEmbedding> targetEmbedding = newsEmbeddingRepository.findByNewsId(newsId);
            if (targetEmbedding.isEmpty()) {
                log.warn("No embedding found for news ID: {}", newsId);
                return Collections.emptyList();
            }
            
            // Use appropriate search method based on database type
            if (isPostgreSQL()) {
                return findSimilarNewsPgVector(targetEmbedding.get(), limit);
            } else {
                return findSimilarNewsH2(targetEmbedding.get(), limit);
            }
            
        } catch (Exception e) {
            log.error("Failed to find similar news for ID: {}", newsId, e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Search using pgvector (PostgreSQL)
     */
    private List<NewsItem> findSimilarNewsPgVector(NewsEmbedding targetEmbedding, int limit) {
        List<NewsItem> results = new ArrayList<>();
        
        String sql = """
            SELECT ne.news_id, 
                   (ne.vector_pg <=> ?) as distance,
                   (1 - (ne.vector_pg <=> ?)) as similarity
            FROM news_embedding_v2 ne
            WHERE ne.news_id != ? 
              AND ne.vector_pg IS NOT NULL
            ORDER BY ne.vector_pg <=> ?
            LIMIT ?
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            String vectorPg = targetEmbedding.getVectorPg();
            if (vectorPg == null) {
                log.warn("No pgvector data for target embedding: {}", targetEmbedding.getId());
                return results;
            }
            
            stmt.setString(1, vectorPg);
            stmt.setString(2, vectorPg);
            stmt.setLong(3, targetEmbedding.getNews().getId());
            stmt.setString(4, vectorPg);
            stmt.setInt(5, limit);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Long newsId = rs.getLong("news_id");
                    double similarity = rs.getDouble("similarity");
                    
                    // Only include results with similarity > 0.3 (reasonable threshold)
                    if (similarity > 0.3) {
                        Optional<NewsItem> newsItem = newsService.getNewsById(newsId);
                        if (newsItem.isPresent()) {
                            NewsItem item = newsItem.get();
                            // Add similarity score as importance for display
                            item.setImportance(similarity);
                            results.add(item);
                        }
                    }
                }
            }
            
            log.debug("Found {} similar news using pgvector for news ID: {}", 
                results.size(), targetEmbedding.getNews().getId());
            
        } catch (Exception e) {
            log.error("Failed to search using pgvector", e);
        }
        
        return results;
    }
    
    /**
     * Search using manual cosine similarity (H2)
     */
    private List<NewsItem> findSimilarNewsH2(NewsEmbedding targetEmbedding, int limit) {
        List<NewsItem> results = new ArrayList<>();
        
        try {
            // Parse target vector
            List<Float> targetVector = objectMapper.readValue(
                targetEmbedding.getVectorText(), 
                new TypeReference<List<Float>>() {}
            );
            
            // Get all embeddings except target
            Pageable pageable = PageRequest.of(0, Math.max(limit * 10, 1000)); // Get more candidates
            List<NewsEmbedding> candidates = newsEmbeddingRepository
                .findByNewsIdNotOrderByCreatedAtDesc(targetEmbedding.getNews().getId(), pageable)
                .getContent();
            
            // Calculate similarities
            List<SimilarityResult> similarities = new ArrayList<>();
            
            for (NewsEmbedding candidate : candidates) {
                try {
                    List<Float> candidateVector = objectMapper.readValue(
                        candidate.getVectorText(), 
                        new TypeReference<List<Float>>() {}
                    );
                    
                    double similarity = calculateCosineSimilarity(targetVector, candidateVector);
                    
                    // Only include results with similarity > 0.3
                    if (similarity > 0.3) {
                        similarities.add(new SimilarityResult(candidate.getNews().getId(), similarity));
                    }
                    
                } catch (Exception e) {
                    log.warn("Failed to parse embedding for news ID: {}", candidate.getNews().getId());
                }
            }
            
            // Sort by similarity (descending) and take top N
            similarities.sort((a, b) -> Double.compare(b.similarity, a.similarity));
            
            for (int i = 0; i < Math.min(limit, similarities.size()); i++) {
                SimilarityResult result = similarities.get(i);
                Optional<NewsItem> newsItem = newsService.getNewsById(result.newsId);
                
                if (newsItem.isPresent()) {
                    NewsItem item = newsItem.get();
                    item.setImportance(result.similarity);
                    results.add(item);
                }
            }
            
            log.debug("Found {} similar news using H2 cosine similarity for news ID: {}", 
                results.size(), targetEmbedding.getNews().getId());
            
        } catch (Exception e) {
            log.error("Failed to search using H2 cosine similarity", e);
        }
        
        return results;
    }
    
    /**
     * Calculate cosine similarity between two vectors
     */
    private double calculateCosineSimilarity(List<Float> vector1, List<Float> vector2) {
        if (vector1.size() != vector2.size()) {
            throw new IllegalArgumentException("Vector dimensions must match");
        }
        
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        
        for (int i = 0; i < vector1.size(); i++) {
            dotProduct += vector1.get(i) * vector2.get(i);
            norm1 += vector1.get(i) * vector1.get(i);
            norm2 += vector2.get(i) * vector2.get(i);
        }
        
        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
    
    /**
     * Find news by semantic text search (if query text provided)
     */
    public List<NewsItem> findSimilarNewsByText(String queryText, int limit) {
        try {
            // Generate embedding for query text
            // This would need a lightweight embedding call to ML service
            // For now, return empty list as this requires ML service integration
            log.debug("Text-based similarity search not implemented yet for query: {}", queryText);
            return Collections.emptyList();
            
        } catch (Exception e) {
            log.error("Failed to find similar news by text: {}", queryText, e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Batch update embeddings for news without embeddings
     */
    public int processEmbeddingBacklog(int batchSize) {
        try {
            // Find news without embeddings
            List<Long> newsIdsWithoutEmbeddings = newsEmbeddingRepository
                .findNewsIdsWithoutEmbeddings(PageRequest.of(0, batchSize));
            
            if (newsIdsWithoutEmbeddings.isEmpty()) {
                log.debug("No news items found without embeddings");
                return 0;
            }
            
            // Get news entities by IDs
            List<com.newsagent.api.entity.News> newsList = newsRepository.findAllById(newsIdsWithoutEmbeddings);
            
            // Generate embeddings in batch
            int processed = embeddingService.generateEmbeddingsBatch(newsList);
            
            log.info("Processed embedding backlog: {} items generated from {} candidates", 
                processed, newsIdsWithoutEmbeddings.size());
            
            return processed;
            
        } catch (Exception e) {
            log.error("Failed to process embedding backlog", e);
            return 0;
        }
    }
    
    /**
     * Check if using PostgreSQL database
     */
    private boolean isPostgreSQL() {
        if ("postgresql".equalsIgnoreCase(databaseType)) {
            return true;
        }
        
        if ("auto".equalsIgnoreCase(databaseType)) {
            try (Connection conn = dataSource.getConnection()) {
                String dbProduct = conn.getMetaData().getDatabaseProductName().toLowerCase();
                return dbProduct.contains("postgresql");
            } catch (Exception e) {
                log.debug("Could not detect database type, defaulting to H2: {}", e.getMessage());
                return false;
            }
        }
        
        return false;
    }
    
    /**
     * Helper class for similarity results
     */
    private static class SimilarityResult {
        final Long newsId;
        final double similarity;
        
        SimilarityResult(Long newsId, double similarity) {
            this.newsId = newsId;
            this.similarity = similarity;
        }
    }
}