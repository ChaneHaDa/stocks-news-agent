package com.newsagent.api.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;

@Entity
@Table(name = "news_score", indexes = {
    @Index(name = "idx_news_score_importance", columnList = "importance"),
    @Index(name = "idx_news_score_rank", columnList = "rank_score")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewsScore {
    
    @Id
    @Column(name = "news_id")
    private Long newsId;
    
    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "news_id")
    private News news;
    
    @Column(nullable = false)
    private Double importance;
    
    @Column(name = "reason_json", length = 2000)
    private String reasonJson;
    
    @Column(name = "rank_score")
    private Double rankScore;
    
    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
    
    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();
    
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}