package com.newsagent.api.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.List;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Entity
@Table(name = "news_embedding_v2", indexes = {
    @Index(name = "idx_news_embedding_v2_news_id", columnList = "news_id"),
    @Index(name = "idx_news_embedding_v2_dimension", columnList = "dimension"),
    @Index(name = "idx_news_embedding_v2_model", columnList = "model_version")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewsEmbedding {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @OneToOne
    @JoinColumn(name = "news_id", nullable = false, unique = true)
    private News news;
    
    @Column(name = "vector_text", columnDefinition = "TEXT", nullable = false)
    private String vectorText; // JSON array of floats for H2 compatibility: [0.1, 0.2, ...]
    
    @Column(name = "vector_pg", columnDefinition = "TEXT")
    private String vectorPg; // pgvector format string for PostgreSQL (will be null in H2)
    
    @Column(nullable = false)
    @Builder.Default
    private Integer dimension = 768;
    
    @Column(name = "model_version", length = 50)
    @Builder.Default
    private String modelVersion = "sentence-transformers";
    
    @Column(name = "l2_norm")
    private Double l2Norm;
    
    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
    
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
    
    /**
     * Get embedding vector as List of Doubles for clustering
     */
    public List<Double> getEmbeddingVector() {
        if (vectorText == null || vectorText.trim().isEmpty()) {
            return List.of();
        }
        
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(vectorText, new TypeReference<List<Double>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}