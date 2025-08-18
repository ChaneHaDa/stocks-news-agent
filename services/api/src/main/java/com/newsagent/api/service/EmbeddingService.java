package com.newsagent.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.newsagent.api.entity.News;
import com.newsagent.api.entity.NewsEmbedding;
import com.newsagent.api.repository.NewsEmbeddingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingService {
    
    private final MlClient mlClient;
    private final NewsEmbeddingRepository newsEmbeddingRepository;
    private final ObjectMapper objectMapper;
    
    /**
     * Generate and save embedding for a single news article
     */
    @Transactional
    public Optional<NewsEmbedding> generateEmbedding(News news) {
        try {
            // Check if embedding already exists
            if (newsEmbeddingRepository.existsByNewsId(news.getId())) {
                log.debug("Embedding already exists for news ID: {}", news.getId());
                return newsEmbeddingRepository.findByNewsId(news.getId());
            }
            
            // Prepare text for embedding (title + truncated body)
            String embeddingText = prepareTextForEmbedding(news.getTitle(), news.getBody());
            
            if (embeddingText.trim().isEmpty()) {
                log.warn("Empty embedding text for news ID: {}", news.getId());
                return Optional.empty();
            }
            
            // Call ML service for embedding
            MlClient.EmbedRequest embedRequest = MlClient.EmbedRequest.builder()
                .items(List.of(MlClient.TextItem.builder()
                    .id(news.getId().toString())
                    .text(embeddingText)
                    .build()))
                .build();
            
            Optional<MlClient.EmbedResponse> mlResponse = mlClient.embed(embedRequest);
            
            if (mlResponse.isEmpty() || mlResponse.get().getResults().isEmpty()) {
                log.warn("No embedding response from ML service for news ID: {}", news.getId());
                return Optional.empty();
            }
            
            MlClient.EmbedResult result = mlResponse.get().getResults().get(0);
            
            // Convert vector to JSON string for storage
            String vectorJson;
            try {
                vectorJson = objectMapper.writeValueAsString(result.getVector());
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize embedding vector for news ID: {}", news.getId(), e);
                return Optional.empty();
            }
            
            // Create and save NewsEmbedding entity
            NewsEmbedding embedding = NewsEmbedding.builder()
                .news(news)
                .vectorText(vectorJson)
                .vectorPg(convertToPgVector(result.getVector())) // For PostgreSQL
                .dimension(result.getVector().size())
                .modelVersion(mlResponse.get().getModelVersion())
                .l2Norm(result.getNorm())
                .createdAt(OffsetDateTime.now())
                .build();
            
            embedding = newsEmbeddingRepository.save(embedding);
            
            log.debug("Generated embedding for news ID: {} (dimension: {})", 
                news.getId(), embedding.getDimension());
            
            return Optional.of(embedding);
            
        } catch (Exception e) {
            log.error("Failed to generate embedding for news ID: {}", news.getId(), e);
            return Optional.empty();
        }
    }
    
    /**
     * Generate embeddings for multiple news articles
     */
    @Transactional
    public int generateEmbeddingsBatch(List<News> newsList) {
        if (newsList.isEmpty()) {
            return 0;
        }
        
        try {
            // Filter out news that already have embeddings
            List<News> newsWithoutEmbeddings = newsList.stream()
                .filter(news -> !newsEmbeddingRepository.existsByNewsId(news.getId()))
                .toList();
            
            if (newsWithoutEmbeddings.isEmpty()) {
                log.debug("All news items already have embeddings");
                return 0;
            }
            
            // Prepare batch request
            List<MlClient.TextItem> textItems = newsWithoutEmbeddings.stream()
                .map(news -> MlClient.TextItem.builder()
                    .id(news.getId().toString())
                    .text(prepareTextForEmbedding(news.getTitle(), news.getBody()))
                    .build())
                .filter(item -> !item.getText().trim().isEmpty())
                .toList();
            
            if (textItems.isEmpty()) {
                log.warn("No valid text items for embedding generation");
                return 0;
            }
            
            // Call ML service
            MlClient.EmbedRequest embedRequest = MlClient.EmbedRequest.builder()
                .items(textItems)
                .build();
            
            Optional<MlClient.EmbedResponse> mlResponse = mlClient.embed(embedRequest);
            
            if (mlResponse.isEmpty()) {
                log.warn("No response from ML service for batch embedding");
                return 0;
            }
            
            // Save embeddings
            int savedCount = 0;
            for (MlClient.EmbedResult result : mlResponse.get().getResults()) {
                try {
                    Long newsId = Long.parseLong(result.getId());
                    News news = newsWithoutEmbeddings.stream()
                        .filter(n -> n.getId().equals(newsId))
                        .findFirst()
                        .orElse(null);
                    
                    if (news == null) {
                        log.warn("News not found for embedding result ID: {}", result.getId());
                        continue;
                    }
                    
                    // Convert vector to JSON
                    String vectorJson = objectMapper.writeValueAsString(result.getVector());
                    
                    NewsEmbedding embedding = NewsEmbedding.builder()
                        .news(news)
                        .vectorText(vectorJson)
                        .vectorPg(convertToPgVector(result.getVector())) // For PostgreSQL
                        .dimension(result.getVector().size())
                        .modelVersion(mlResponse.get().getModelVersion())
                        .l2Norm(result.getNorm())
                        .createdAt(OffsetDateTime.now())
                        .build();
                    
                    newsEmbeddingRepository.save(embedding);
                    savedCount++;
                    
                } catch (Exception e) {
                    log.error("Failed to save embedding for result ID: {}", result.getId(), e);
                }
            }
            
            log.info("Generated {} embeddings from {} news articles", savedCount, newsWithoutEmbeddings.size());
            return savedCount;
            
        } catch (Exception e) {
            log.error("Failed to generate embeddings batch", e);
            return 0;
        }
    }
    
    /**
     * Get embedding vector as float array
     */
    public Optional<List<Float>> getEmbeddingVector(Long newsId) {
        try {
            Optional<NewsEmbedding> embedding = newsEmbeddingRepository.findByNewsId(newsId);
            
            if (embedding.isEmpty()) {
                return Optional.empty();
            }
            
            List<Float> vector = objectMapper.readValue(
                embedding.get().getVectorText(), 
                new TypeReference<List<Float>>() {}
            );
            
            return Optional.of(vector);
            
        } catch (Exception e) {
            log.error("Failed to parse embedding vector for news ID: {}", newsId, e);
            return Optional.empty();
        }
    }
    
    /**
     * Calculate cosine similarity between two news articles
     */
    public Optional<Double> calculateSimilarity(Long newsId1, Long newsId2) {
        try {
            Optional<List<Float>> vector1 = getEmbeddingVector(newsId1);
            Optional<List<Float>> vector2 = getEmbeddingVector(newsId2);
            
            if (vector1.isEmpty() || vector2.isEmpty()) {
                return Optional.empty();
            }
            
            return Optional.of(cosineSimilarity(vector1.get(), vector2.get()));
            
        } catch (Exception e) {
            log.error("Failed to calculate similarity between news {} and {}", newsId1, newsId2, e);
            return Optional.empty();
        }
    }
    
    /**
     * Prepare text for embedding (title + body, max 512 chars)
     */
    private String prepareTextForEmbedding(String title, String body) {
        if (title == null) title = "";
        if (body == null) body = "";
        
        String combined = title.trim() + " " + body.trim();
        
        // Truncate to max 512 characters for embedding efficiency
        if (combined.length() > 512) {
            combined = combined.substring(0, 512);
        }
        
        return combined.trim();
    }
    
    /**
     * Calculate cosine similarity between two vectors
     */
    private double cosineSimilarity(List<Float> vector1, List<Float> vector2) {
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
     * Convert float vector to pgvector format string
     */
    private String convertToPgVector(List<Float> vector) {
        if (vector == null || vector.isEmpty()) {
            return null;
        }
        
        try {
            // pgvector format: "[0.1,0.2,0.3]"
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < vector.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(vector.get(i));
            }
            sb.append("]");
            return sb.toString();
        } catch (Exception e) {
            log.warn("Failed to convert vector to pgvector format", e);
            return null;
        }
    }
}