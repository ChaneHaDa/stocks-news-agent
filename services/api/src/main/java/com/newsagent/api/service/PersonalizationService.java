package com.newsagent.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.newsagent.api.entity.ClickLog;
import com.newsagent.api.entity.News;
import com.newsagent.api.entity.UserPreference;
import com.newsagent.api.repository.ClickLogRepository;
import com.newsagent.api.repository.UserPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PersonalizationService {
    
    private final UserPreferenceRepository userPreferenceRepository;
    private final ClickLogRepository clickLogRepository;
    private final TickerMatcher tickerMatcher;
    private final ObjectMapper objectMapper;
    
    // Personalization parameters
    private static final int CLICK_HISTORY_DAYS = 7;  // Consider clicks from last 7 days
    private static final double MIN_CLICK_WEIGHT = 0.1; // Minimum weight for clicked items
    private static final double MAX_CLICK_WEIGHT = 0.3; // Maximum weight boost for clicked items
    
    /**
     * Get or create user preferences
     */
    @Transactional
    public UserPreference getUserPreferences(String userId) {
        return userPreferenceRepository.findByUserId(userId)
            .orElseGet(() -> createDefaultPreferences(userId));
    }
    
    /**
     * Create default user preferences
     */
    private UserPreference createDefaultPreferences(String userId) {
        UserPreference preferences = UserPreference.builder()
            .userId(userId)
            .interestedTickers("[]") // Empty JSON array
            .interestedKeywords("[]") // Empty JSON array
            .diversityWeight(0.7)
            .personalizationEnabled(false)
            .isActive(true)
            .createdAt(OffsetDateTime.now())
            .build();
        
        return userPreferenceRepository.save(preferences);
    }
    
    /**
     * Update user preferences
     */
    @Transactional
    public UserPreference updateUserPreferences(String userId, 
                                              List<String> interestedTickers,
                                              List<String> interestedKeywords,
                                              Double diversityWeight,
                                              Boolean personalizationEnabled) {
        
        UserPreference preferences = getUserPreferences(userId);
        
        try {
            if (interestedTickers != null) {
                preferences.setInterestedTickers(objectMapper.writeValueAsString(interestedTickers));
            }
            
            if (interestedKeywords != null) {
                preferences.setInterestedKeywords(objectMapper.writeValueAsString(interestedKeywords));
            }
            
            if (diversityWeight != null) {
                preferences.setDiversityWeight(diversityWeight);
            }
            
            if (personalizationEnabled != null) {
                preferences.setPersonalizationEnabled(personalizationEnabled);
            }
            
            preferences.setUpdatedAt(OffsetDateTime.now());
            
            return userPreferenceRepository.save(preferences);
            
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize user preferences for user: {}", userId, e);
            throw new RuntimeException("Failed to update user preferences", e);
        }
    }
    
    /**
     * Record a click event
     */
    @Transactional
    public void recordClick(String userId, Long newsId, String sessionId, 
                          String userAgent, String ipAddress, 
                          Integer rankPosition, Double importanceScore) {
        
        try {
            ClickLog clickLog = ClickLog.builder()
                .userId(userId)
                .news(News.builder().id(newsId).build()) // Just set the ID for foreign key
                .clickedAt(OffsetDateTime.now())
                .sessionId(sessionId)
                .userAgent(userAgent)
                .ipAddress(ipAddress)
                .rankPosition(rankPosition)
                .importanceScore(importanceScore)
                .build();
            
            clickLogRepository.save(clickLog);
            
            log.debug("Recorded click for user {} on news {} at position {}", 
                userId, newsId, rankPosition);
            
        } catch (Exception e) {
            log.error("Failed to record click for user {} on news {}", userId, newsId, e);
        }
    }
    
    /**
     * Calculate personalized relevance score for a news article
     */
    public double calculatePersonalizedRelevance(News news, String userId) {
        UserPreference preferences = getUserPreferences(userId);
        
        if (!preferences.getPersonalizationEnabled()) {
            return 0.0; // No personalization
        }
        
        double relevanceScore = 0.0;
        
        // Interest-based relevance
        relevanceScore += calculateInterestRelevance(news, preferences);
        
        // Click history relevance
        relevanceScore += calculateClickHistoryRelevance(news, userId);
        
        // Topic relevance based on user's click patterns
        relevanceScore += calculateTopicRelevance(news, userId);
        
        return Math.min(1.0, Math.max(0.0, relevanceScore));
    }
    
    /**
     * Calculate relevance based on user's stated interests
     */
    private double calculateInterestRelevance(News news, UserPreference preferences) {
        double relevance = 0.0;
        
        try {
            // Check ticker interests
            List<String> interestedTickers = objectMapper.readValue(
                preferences.getInterestedTickers(), 
                new TypeReference<List<String>>() {}
            );
            
            if (!interestedTickers.isEmpty()) {
                Set<String> newsTickerSet = tickerMatcher.findTickers(news.getBody());
                
                for (String interestedTicker : interestedTickers) {
                    if (newsTickerSet.contains(interestedTicker)) {
                        relevance += 0.3; // High boost for ticker match
                        break; // One match is enough
                    }
                }
            }
            
            // Check keyword interests
            List<String> interestedKeywords = objectMapper.readValue(
                preferences.getInterestedKeywords(),
                new TypeReference<List<String>>() {}
            );
            
            if (!interestedKeywords.isEmpty()) {
                String newsText = (news.getTitle() + " " + (news.getBody() != null ? news.getBody() : "")).toLowerCase();
                
                for (String keyword : interestedKeywords) {
                    if (newsText.contains(keyword.toLowerCase())) {
                        relevance += 0.1; // Moderate boost for keyword match
                    }
                }
            }
            
        } catch (Exception e) {
            log.warn("Failed to parse user interests for user: {}", preferences.getUserId(), e);
        }
        
        return relevance;
    }
    
    /**
     * Calculate relevance based on user's click history
     */
    private double calculateClickHistoryRelevance(News news, String userId) {
        OffsetDateTime since = OffsetDateTime.now().minusDays(CLICK_HISTORY_DAYS);
        List<ClickLog> recentClicks = clickLogRepository.findByUserIdAndClickedAtAfter(userId, since);
        
        if (recentClicks.isEmpty()) {
            return 0.0;
        }
        
        // Find similar news based on tickers
        Set<String> newsTickerSet = tickerMatcher.findTickers(news.getBody());
        
        long relevantClicks = recentClicks.stream()
            .filter(click -> {
                try {
                    News clickedNews = click.getNews();
                    if (clickedNews != null && clickedNews.getBody() != null) {
                        Set<String> clickedTickerSet = tickerMatcher.findTickers(clickedNews.getBody());
                        // Check if there's overlap in tickers
                        return !Collections.disjoint(newsTickerSet, clickedTickerSet);
                    }
                } catch (Exception e) {
                    log.debug("Failed to analyze clicked news: {}", click.getNews().getId(), e);
                }
                return false;
            })
            .count();
        
        // Weight based on proportion of relevant clicks
        double clickRelevance = (double) relevantClicks / recentClicks.size();
        return Math.min(MAX_CLICK_WEIGHT, clickRelevance * MAX_CLICK_WEIGHT);
    }
    
    /**
     * Calculate relevance based on topic similarity to user's click patterns
     */
    private double calculateTopicRelevance(News news, String userId) {
        // TODO: Implement topic relevance after NewsTopic entity is properly set up
        return 0.0;
    }
    
    /**
     * Apply personalized re-ranking to news list
     */
    public List<News> applyPersonalizedRanking(List<News> newsList, String userId) {
        UserPreference preferences = getUserPreferences(userId);
        
        if (!preferences.getPersonalizationEnabled()) {
            return newsList; // No personalization
        }
        
        log.debug("Applying personalized ranking for user: {}", userId);
        
        // Calculate personalized scores
        List<PersonalizedNewsItem> personalizedItems = newsList.stream()
            .map(news -> {
                double originalScore = news.getNewsScore() != null ? 
                    news.getNewsScore().getRankScore() : 0.0;
                
                double personalizedRelevance = calculatePersonalizedRelevance(news, userId);
                
                // Enhanced ranking formula with personalization
                // rank = 0.45*importance + 0.20*recency + 0.25*user_relevance + 0.10*novelty
                double importance = news.getNewsScore() != null ? 
                    (news.getNewsScore().getImportanceP() != null ? 
                        news.getNewsScore().getImportanceP() : 
                        news.getNewsScore().getImportance()) : 0.0;
                
                double recency = calculateRecencyScore(news);
                double novelty = 0.1; // Simplified novelty score
                
                double personalizedScore = 0.45 * importance + 
                                         0.20 * recency + 
                                         0.25 * personalizedRelevance + 
                                         0.10 * novelty;
                
                return new PersonalizedNewsItem(news, personalizedScore, personalizedRelevance);
            })
            .collect(Collectors.toList());
        
        // Sort by personalized score
        personalizedItems.sort((a, b) -> 
            Double.compare(b.getPersonalizedScore(), a.getPersonalizedScore()));
        
        List<News> rankedNews = personalizedItems.stream()
            .map(PersonalizedNewsItem::getNews)
            .collect(Collectors.toList());
        
        log.debug("Personalized ranking completed for user: {} ({} articles)", 
            userId, rankedNews.size());
        
        return rankedNews;
    }
    
    /**
     * Calculate recency score based on publication time
     */
    private double calculateRecencyScore(News news) {
        if (news.getPublishedAt() == null) {
            return 0.0;
        }
        
        long hoursAgo = java.time.Duration.between(news.getPublishedAt(), OffsetDateTime.now()).toHours();
        
        // Decay function: full score for < 1 hour, linear decay to 0 at 48 hours
        if (hoursAgo <= 1) {
            return 1.0;
        } else if (hoursAgo >= 48) {
            return 0.0;
        } else {
            return 1.0 - ((double) hoursAgo - 1) / 47; // Linear decay
        }
    }
    
    /**
     * Helper class for personalized ranking
     */
    private static class PersonalizedNewsItem {
        private final News news;
        private final double personalizedScore;
        private final double personalizedRelevance;
        
        public PersonalizedNewsItem(News news, double personalizedScore, double personalizedRelevance) {
            this.news = news;
            this.personalizedScore = personalizedScore;
            this.personalizedRelevance = personalizedRelevance;
        }
        
        public News getNews() { return news; }
        public double getPersonalizedScore() { return personalizedScore; }
        public double getPersonalizedRelevance() { return personalizedRelevance; }
    }
}