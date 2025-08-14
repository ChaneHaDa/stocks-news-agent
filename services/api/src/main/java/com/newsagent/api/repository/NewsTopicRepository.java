package com.newsagent.api.repository;

import com.newsagent.api.entity.NewsTopic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NewsTopicRepository extends JpaRepository<NewsTopic, Long> {
    
    Optional<NewsTopic> findByNewsId(Long newsId);
    
    @Query("SELECT nt FROM NewsTopic nt WHERE nt.topicId = :topicId")
    List<NewsTopic> findByTopicId(@Param("topicId") String topicId);
    
    @Query("SELECT nt FROM NewsTopic nt WHERE nt.groupId = :groupId")
    List<NewsTopic> findByGroupId(@Param("groupId") String groupId);
    
    @Query("SELECT DISTINCT nt.topicId FROM NewsTopic nt")
    List<String> findDistinctTopicIds();
    
    @Query("SELECT nt FROM NewsTopic nt WHERE nt.news.id IN :newsIds")
    List<NewsTopic> findByNewsIdIn(@Param("newsIds") List<Long> newsIds);
    
    boolean existsByNewsId(Long newsId);
}