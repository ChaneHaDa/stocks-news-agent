package com.newsagent.api.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.OffsetDateTime;

@Entity
@Table(name = "news_embedding", indexes = {
    @Index(name = "idx_news_embedding_news_id", columnList = "news_id"),
    @Index(name = "idx_news_embedding_dimension", columnList = "dimension"),
    @Index(name = "idx_news_embedding_model", columnList = "model_version")
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
    
    @Column(name = "vector", columnDefinition = "TEXT", nullable = false)
    private String vector; // JSON array of floats: [0.1, 0.2, ...]
    
    @Column(nullable = false)
    private Integer dimension;
    
    @Column(name = "model_version", length = 50)
    private String modelVersion;
    
    @Column(name = "l2_norm")
    private Double l2Norm;
    
    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
    
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}