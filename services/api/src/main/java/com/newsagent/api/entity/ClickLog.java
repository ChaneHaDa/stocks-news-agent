package com.newsagent.api.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.OffsetDateTime;

@Entity
@Table(name = "click_log", indexes = {
    @Index(name = "idx_click_log_user_id", columnList = "user_id"),
    @Index(name = "idx_click_log_news_id", columnList = "news_id"),
    @Index(name = "idx_click_log_clicked_at", columnList = "clicked_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClickLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;
    
    @ManyToOne
    @JoinColumn(name = "news_id", nullable = false)
    private News news;
    
    @Column(name = "clicked_at", nullable = false)
    @Builder.Default
    private OffsetDateTime clickedAt = OffsetDateTime.now();
    
    @Column(name = "session_id", length = 100)
    private String sessionId;
    
    @Column(name = "user_agent", length = 500)
    private String userAgent;
    
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    
    @Column(name = "rank_position")
    private Integer rankPosition; // Position in the ranking when clicked
    
    @Column(name = "importance_score")
    private Double importanceScore; // Importance score at click time
}