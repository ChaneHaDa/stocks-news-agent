package com.newsagent.api.repository;

import com.newsagent.api.entity.AnonymousUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AnonymousUserRepository extends JpaRepository<AnonymousUser, Long> {
    
    /**
     * Find anonymous user by anon_id
     */
    Optional<AnonymousUser> findByAnonId(String anonId);
    
    /**
     * Check if anon_id exists
     */
    boolean existsByAnonId(String anonId);
    
    /**
     * Find active users seen after given time
     */
    @Query("SELECT au FROM AnonymousUser au WHERE au.isActive = true AND au.lastSeenAt >= :since")
    List<AnonymousUser> findActiveUsersSince(@Param("since") OffsetDateTime since);
    
    /**
     * Count total active anonymous users
     */
    @Query("SELECT COUNT(au) FROM AnonymousUser au WHERE au.isActive = true")
    long countActiveUsers();
    
    /**
     * Find users by country for analytics
     */
    @Query("SELECT au.countryCode, COUNT(au) FROM AnonymousUser au WHERE au.isActive = true GROUP BY au.countryCode")
    List<Object[]> countUsersByCountry();
    
    /**
     * Find inactive users for cleanup (older than 365 days)
     */
    @Query("SELECT au FROM AnonymousUser au WHERE au.lastSeenAt < :cutoff")
    List<AnonymousUser> findInactiveUsersBefore(@Param("cutoff") OffsetDateTime cutoff);
}