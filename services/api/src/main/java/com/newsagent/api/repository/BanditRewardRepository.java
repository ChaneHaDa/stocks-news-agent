package com.newsagent.api.repository;

import com.newsagent.api.entity.BanditReward;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BanditRewardRepository extends JpaRepository<BanditReward, Long> {
    
    List<BanditReward> findByDecisionId(Long decisionId);
    
    List<BanditReward> findByRewardTypeAndCollectedAtAfter(String rewardType, LocalDateTime after);
    
    @Query("SELECT br FROM BanditReward br " +
           "JOIN br.decision bd " +
           "WHERE bd.experimentId = :experimentId AND br.collectedAt >= :since")
    List<BanditReward> findByExperimentIdSince(@Param("experimentId") Long experimentId,
                                               @Param("since") LocalDateTime since);
    
    @Query("SELECT bd.armId, SUM(br.rewardValue) FROM BanditReward br " +
           "JOIN br.decision bd " +
           "WHERE bd.experimentId = :experimentId AND br.collectedAt >= :since " +
           "GROUP BY bd.armId")
    List<Object[]> sumRewardsByArmSince(@Param("experimentId") Long experimentId,
                                       @Param("since") LocalDateTime since);
    
    @Query("SELECT bd.armId, AVG(br.rewardValue) FROM BanditReward br " +
           "JOIN br.decision bd " +
           "WHERE bd.experimentId = :experimentId AND br.rewardType = :rewardType " +
           "AND br.collectedAt >= :since " +
           "GROUP BY bd.armId")
    List<Object[]> avgRewardsByArmAndTypeSince(@Param("experimentId") Long experimentId,
                                              @Param("rewardType") String rewardType,
                                              @Param("since") LocalDateTime since);
}