package com.newsagent.api.repository;

import com.newsagent.api.entity.UserPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserPreferenceRepository extends JpaRepository<UserPreference, Long> {
    
    Optional<UserPreference> findByUserId(String userId);
    
    @Query("SELECT up FROM UserPreference up WHERE up.personalizationEnabled = true AND up.isActive = true")
    List<UserPreference> findActivePersonalizedUsers();
    
    @Query("SELECT up FROM UserPreference up WHERE up.isActive = true")
    List<UserPreference> findActiveUsers();
    
    boolean existsByUserId(String userId);
}