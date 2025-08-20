package com.newsagent.api.repository;

import com.newsagent.api.entity.BanditDecision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BanditDecisionRepository extends JpaRepository<BanditDecision, Long> {
    
    List<BanditDecision> findByExperimentIdAndUserId(Long experimentId, String userId);
    
    List<BanditDecision> findByExperimentIdAndCreatedAtAfter(Long experimentId, LocalDateTime after);
    
    @Query("SELECT bd FROM BanditDecision bd WHERE bd.experimentId = :experimentId " +
           "AND bd.createdAt BETWEEN :startTime AND :endTime")
    List<BanditDecision> findByExperimentIdAndTimeRange(@Param("experimentId") Long experimentId,
                                                         @Param("startTime") LocalDateTime startTime,
                                                         @Param("endTime") LocalDateTime endTime);
    
    @Query("SELECT COUNT(bd) FROM BanditDecision bd WHERE bd.experimentId = :experimentId " +
           "AND bd.armId = :armId AND bd.createdAt >= :since")
    Long countByExperimentIdAndArmIdSince(@Param("experimentId") Long experimentId,
                                          @Param("armId") Long armId,
                                          @Param("since") LocalDateTime since);
    
    @Query("SELECT bd.armId, COUNT(bd) FROM BanditDecision bd WHERE bd.experimentId = :experimentId " +
           "AND bd.createdAt >= :since GROUP BY bd.armId")
    List<Object[]> countDecisionsByArmSince(@Param("experimentId") Long experimentId,
                                           @Param("since") LocalDateTime since);
}