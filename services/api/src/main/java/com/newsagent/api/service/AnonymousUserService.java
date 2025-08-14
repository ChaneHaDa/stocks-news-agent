package com.newsagent.api.service;

import com.newsagent.api.entity.AnonymousUser;
import com.newsagent.api.repository.AnonymousUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.Optional;

/**
 * Anonymous User Service for managing session-based user identification
 * Provides foundation for A/B testing and personalization without login
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnonymousUserService {
    
    private final AnonymousUserRepository anonymousUserRepository;
    
    // Cookie configuration
    private static final String ANON_ID_COOKIE = "anon_id";
    private static final int COOKIE_MAX_AGE = 365 * 24 * 60 * 60; // 365 days in seconds
    private static final String COOKIE_PATH = "/";
    
    /**
     * Get or create anonymous user from request
     * Checks cookie first, creates new user if not found
     */
    @Transactional
    public AnonymousUser getOrCreateAnonymousUser(HttpServletRequest request, HttpServletResponse response) {
        String anonId = extractAnonIdFromCookie(request);
        
        if (anonId != null) {
            Optional<AnonymousUser> existingUser = anonymousUserRepository.findByAnonId(anonId);
            if (existingUser.isPresent()) {
                AnonymousUser user = existingUser.get();
                user.recordActivity();
                anonymousUserRepository.save(user);
                log.debug("Returning user with anon_id: {}", anonId);
                return user;
            }
        }
        
        // Create new anonymous user
        AnonymousUser newUser = createNewAnonymousUser(request, response);
        log.debug("Created new anonymous user: {}", newUser.getAnonId());
        return newUser;
    }
    
    /**
     * Create new anonymous user and set cookie
     */
    private AnonymousUser createNewAnonymousUser(HttpServletRequest request, HttpServletResponse response) {
        String newAnonId = generateUniqueAnonId();
        
        AnonymousUser user = AnonymousUser.builder()
            .anonId(newAnonId)
            .firstSeenAt(OffsetDateTime.now())
            .lastSeenAt(OffsetDateTime.now())
            .sessionCount(1)
            .userAgent(extractUserAgent(request))
            .ipAddress(extractIpAddress(request))
            .countryCode("KR") // Default to Korea for now
            .isActive(true)
            .build();
        
        user = anonymousUserRepository.save(user);
        
        // Set cookie in response
        setAnonIdCookie(response, newAnonId);
        
        return user;
    }
    
    /**
     * Generate unique anonymous ID
     */
    private String generateUniqueAnonId() {
        String anonId;
        do {
            anonId = UUID.randomUUID().toString();
        } while (anonymousUserRepository.existsByAnonId(anonId));
        
        return anonId;
    }
    
    /**
     * Extract anon_id from cookie
     */
    private String extractAnonIdFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        
        for (Cookie cookie : request.getCookies()) {
            if (ANON_ID_COOKIE.equals(cookie.getName())) {
                String value = cookie.getValue();
                if (isValidAnonId(value)) {
                    return value;
                }
            }
        }
        return null;
    }
    
    /**
     * Set anon_id cookie in response
     */
    private void setAnonIdCookie(HttpServletResponse response, String anonId) {
        Cookie cookie = new Cookie(ANON_ID_COOKIE, anonId);
        cookie.setMaxAge(COOKIE_MAX_AGE);
        cookie.setPath(COOKIE_PATH);
        cookie.setHttpOnly(true); // Prevent XSS
        cookie.setSecure(false); // Set to true in production with HTTPS
        // cookie.setSameSite("Lax"); // Uncomment when available
        
        response.addCookie(cookie);
    }
    
    /**
     * Validate anon_id format (UUID)
     */
    private boolean isValidAnonId(String anonId) {
        if (anonId == null || anonId.length() != 36) {
            return false;
        }
        
        try {
            UUID.fromString(anonId);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    
    /**
     * Extract User-Agent from request
     */
    private String extractUserAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent != null && userAgent.length() > 500) {
            userAgent = userAgent.substring(0, 500);
        }
        return userAgent;
    }
    
    /**
     * Extract IP address from request (considering proxies)
     */
    private String extractIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
    
    /**
     * Find anonymous user by anon_id
     */
    @Transactional(readOnly = true)
    public Optional<AnonymousUser> findByAnonId(String anonId) {
        if (!isValidAnonId(anonId)) {
            return Optional.empty();
        }
        return anonymousUserRepository.findByAnonId(anonId);
    }
    
    /**
     * Get analytics data
     */
    @Transactional(readOnly = true)
    public AnalyticsData getAnalyticsData() {
        long totalActiveUsers = anonymousUserRepository.countActiveUsers();
        OffsetDateTime yesterday = OffsetDateTime.now().minusDays(1);
        long dailyActiveUsers = anonymousUserRepository.findActiveUsersSince(yesterday).size();
        
        return AnalyticsData.builder()
            .totalActiveUsers(totalActiveUsers)
            .dailyActiveUsers(dailyActiveUsers)
            .build();
    }
    
    @lombok.Builder
    @lombok.Data
    public static class AnalyticsData {
        private long totalActiveUsers;
        private long dailyActiveUsers;
    }
}