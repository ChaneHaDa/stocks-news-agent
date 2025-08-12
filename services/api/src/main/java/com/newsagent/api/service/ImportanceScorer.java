package com.newsagent.api.service;

import com.newsagent.api.config.RssProperties;
import com.newsagent.api.config.ScoringProperties;
import com.newsagent.api.entity.News;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImportanceScorer {
    
    private final ScoringProperties scoringProperties;
    private final TickerMatcher tickerMatcher;
    
    public ScoreResult calculateImportance(News news, RssProperties.RssSource sourceConfig) {
        Map<String, Object> reasonBreakdown = new HashMap<>();
        double totalScore = 0.0;
        
        // 1. Source weight
        double sourceWeight = sourceConfig != null ? sourceConfig.getWeight() : 0.5;
        totalScore += sourceWeight;
        reasonBreakdown.put("source_weight", sourceWeight);
        
        // 2. Ticker matching strength
        double tickerScore = tickerMatcher.calculateTickerMatchStrength(news.getBody());
        totalScore += tickerScore;
        reasonBreakdown.put("tickers_hit", tickerScore);
        
        // 3. Keyword matching
        double keywordScore = calculateKeywordStrength(news.getTitle(), news.getBody());
        totalScore += keywordScore;
        reasonBreakdown.put("keywords_hit", keywordScore);
        
        // 4. Freshness bonus
        double freshnessScore = calculateFreshnessBonus(news.getPublishedAt());
        totalScore += freshnessScore;
        reasonBreakdown.put("freshness", freshnessScore);
        
        // 5. Content quality penalties
        double qualityPenalty = calculateQualityPenalty(news.getTitle(), news.getBody());
        totalScore += qualityPenalty; // This will be negative
        if (qualityPenalty < 0) {
            reasonBreakdown.put("quality_penalty", qualityPenalty);
        }
        
        // Additional context
        Set<String> tickers = tickerMatcher.findTickers(news.getBody());
        reasonBreakdown.put("tickers_found", new ArrayList<>(tickers));
        reasonBreakdown.put("ticker_count", tickers.size());
        
        // Ensure score is reasonable
        double finalScore = Math.max(0.0, Math.min(10.0, Math.round(totalScore * 1000.0) / 1000.0));
        
        return ScoreResult.builder()
            .importance(finalScore)
            .reasonJson(reasonBreakdown)
            .rankScore(calculateRankScore(finalScore, news.getPublishedAt()))
            .build();
    }
    
    private double calculateKeywordStrength(String title, String body) {
        String fullText = (title + " " + body).toLowerCase();
        double score = 0.0;
        
        // High impact keywords
        List<String> highImpactKeywords = scoringProperties.getKeywords().getHighImpact();
        if (highImpactKeywords != null) {
            for (String keyword : highImpactKeywords) {
                if (fullText.contains(keyword.toLowerCase())) {
                    score += 0.3;
                }
            }
        }
        
        // Medium impact keywords
        List<String> mediumImpactKeywords = scoringProperties.getKeywords().getMediumImpact();
        if (mediumImpactKeywords != null) {
            for (String keyword : mediumImpactKeywords) {
                if (fullText.contains(keyword.toLowerCase())) {
                    score += 0.2;
                }
            }
        }
        
        return Math.min(score, 1.5); // Cap at 1.5
    }
    
    private double calculateFreshnessBonus(OffsetDateTime publishedAt) {
        if (publishedAt == null) {
            return 0.0;
        }
        
        OffsetDateTime now = OffsetDateTime.now();
        long hoursAgo = ChronoUnit.HOURS.between(publishedAt, now);
        
        if (hoursAgo <= 3) {
            return scoringProperties.getFreshness().getHours3();
        } else if (hoursAgo <= 24) {
            return scoringProperties.getFreshness().getHours24();
        } else if (hoursAgo <= 72) {
            return scoringProperties.getFreshness().getHours72();
        } else {
            return 0.0;
        }
    }
    
    private double calculateQualityPenalty(String title, String body) {
        double penalty = 0.0;
        
        // Too short content
        if (body != null && body.length() < 80) {
            penalty -= 0.3;
        }
        
        // Too short title
        if (title != null && title.length() < 20) {
            penalty -= 0.2;
        }
        
        // Suspicious patterns
        if (body != null) {
            String lowerBody = body.toLowerCase();
            
            // Too many exclamation marks or question marks
            long exclamationCount = lowerBody.chars().filter(ch -> ch == '!').count();
            long questionCount = lowerBody.chars().filter(ch -> ch == '?').count();
            
            if (exclamationCount > 3 || questionCount > 3) {
                penalty -= 0.2;
            }
            
            // Repeated phrases (spam indicator)
            if (lowerBody.matches(".*(.{10,})\\1{2,}.*")) {
                penalty -= 0.5;
            }
        }
        
        return penalty;
    }
    
    private double calculateRankScore(double importance, OffsetDateTime publishedAt) {
        // Rank score combines importance with recency for final ranking
        double baseScore = importance;
        
        if (publishedAt != null) {
            long hoursAgo = ChronoUnit.HOURS.between(publishedAt, OffsetDateTime.now());
            
            // Apply time decay
            double timeDecay = Math.max(0.1, 1.0 - (hoursAgo / 168.0)); // Decay over 1 week
            baseScore *= timeDecay;
        }
        
        return Math.round(baseScore * 1000.0) / 1000.0;
    }
    
    @lombok.Builder
    @lombok.Data
    public static class ScoreResult {
        private Double importance;
        private Map<String, Object> reasonJson;
        private Double rankScore;
    }
}