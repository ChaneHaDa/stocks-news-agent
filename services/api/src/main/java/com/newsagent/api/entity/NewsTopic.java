package com.newsagent.api.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.OffsetDateTime;

@Entity
@Table(name = "news_topic", indexes = {
    @Index(name = "idx_news_topic_news_id", columnList = "news_id"),
    @Index(name = "idx_news_topic_topic_id", columnList = "topic_id"),
    @Index(name = "idx_news_topic_group_id", columnList = "group_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewsTopic {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @OneToOne
    @JoinColumn(name = "news_id", nullable = false, unique = true)
    private News news;
    
    @Column(name = "topic_id", nullable = false)
    private String topicId; // Generated topic cluster ID
    
    @Column(name = "group_id")
    private String groupId; // Duplicate news group ID (if similar title/content)
    
    @Column(name = "topic_keywords", length = 500)
    private String topicKeywords; // JSON array: ["삼성전자", "실적", "반도체"]
    
    @Column(name = "similarity_score")
    private Double similarityScore; // Similarity score to topic centroid
    
    @Column(name = "clustering_method", length = 50)
    @Builder.Default
    private String clusteringMethod = "hdbscan";
    
    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
    
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}