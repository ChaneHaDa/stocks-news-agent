package com.newsagent.api.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.OffsetDateTime;

@Entity
@Table(name = "news", indexes = {
    @Index(name = "idx_news_published", columnList = "published_at"),
    @Index(name = "idx_news_dedup", columnList = "dedup_key"),
    @Index(name = "idx_news_source", columnList = "source")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class News {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String source;
    
    @Column(nullable = false, length = 2048)
    private String url;
    
    @Column(name = "published_at")
    private OffsetDateTime publishedAt;
    
    @Column(nullable = false, length = 1000)
    private String title;
    
    @Column(length = 10000)
    private String body;
    
    @Column(name = "dedup_key", unique = true, length = 64)
    private String dedupKey;
    
    @Column(length = 10)
    @Builder.Default
    private String lang = "ko";
    
    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
    
    @OneToOne(mappedBy = "news", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private NewsScore newsScore;
}