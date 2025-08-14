package com.newsagent.api.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.OffsetDateTime;

/**
 * Anonymous User Entity for session-based personalization
 * Replaces hardcoded userId with proper anonymous ID system
 */
@Entity
@Table(name = "anonymous_user", indexes = {
    @Index(name = "idx_anon_user_anon_id", columnList = "anon_id", unique = true),
    @Index(name = "idx_anon_user_created", columnList = "created_at"),
    @Index(name = "idx_anon_user_active", columnList = "is_active")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnonymousUser {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "anon_id", nullable = false, unique = true, length = 36)
    private String anonId; // UUID format: "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
    
    @Column(name = "first_seen_at", nullable = false)
    private OffsetDateTime firstSeenAt;
    
    @Column(name = "last_seen_at")
    private OffsetDateTime lastSeenAt;
    
    @Column(name = "session_count")
    @Builder.Default
    private Integer sessionCount = 1;
    
    @Column(name = "user_agent", length = 500)
    private String userAgent;
    
    @Column(name = "ip_address", length = 45) // IPv6 compatible
    private String ipAddress;
    
    @Column(name = "country_code", length = 2) // ISO 3166-1 alpha-2
    private String countryCode;
    
    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;
    
    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
    
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
    
    /**
     * Update last seen and increment session count
     */
    public void recordActivity() {
        this.lastSeenAt = OffsetDateTime.now();
        this.sessionCount++;
        this.updatedAt = OffsetDateTime.now();
    }
}