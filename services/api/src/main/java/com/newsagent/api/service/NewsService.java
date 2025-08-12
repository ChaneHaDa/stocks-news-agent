package com.newsagent.api.service;

import com.newsagent.api.entity.News;
import com.newsagent.api.model.ImportanceReason;
import com.newsagent.api.model.NewsItem;
import com.newsagent.api.repository.NewsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewsService {
    
    private final NewsRepository newsRepository;
    private final TickerMatcher tickerMatcher;
    
    public List<NewsItem> getTopNews(int limit, Set<String> tickerFilters, String lang, OffsetDateTime since) {
        Pageable pageable = PageRequest.of(0, limit);
        
        Page<News> newsPage;
        
        if (tickerFilters != null && !tickerFilters.isEmpty()) {
            // Filter by tickers - find news that mention any of the requested tickers
            newsPage = newsRepository.findNewsWithFilters(null, null, since, pageable);
            
            // Additional filtering for tickers in application layer
            List<News> filteredNews = newsPage.getContent().stream()
                .filter(news -> {
                    Set<String> foundTickers = tickerMatcher.findTickers(news.getBody());
                    return foundTickers.stream().anyMatch(tickerFilters::contains);
                })
                .limit(limit)
                .collect(Collectors.toList());
            
            return convertToNewsItems(filteredNews);
        } else {
            // No ticker filter, get top news by importance
            newsPage = newsRepository.findNewsWithFilters(null, null, since, pageable);
            return convertToNewsItems(newsPage.getContent());
        }
    }
    
    public Optional<NewsItem> getNewsById(Long id) {
        return newsRepository.findById(id)
            .map(this::convertToNewsItem);
    }
    
    private List<NewsItem> convertToNewsItems(List<News> newsList) {
        return newsList.stream()
            .map(this::convertToNewsItem)
            .collect(Collectors.toList());
    }
    
    private NewsItem convertToNewsItem(News news) {
        Set<String> tickers = tickerMatcher.findTickers(news.getBody());
        
        ImportanceReason reason = extractImportanceReason(news);
        
        // Create summary from body (first 240 characters)
        String summary = createSummary(news.getBody());
        
        Double importance = news.getNewsScore() != null ? 
            news.getNewsScore().getImportance() : 0.0;
        
        return new NewsItem(
            String.valueOf(news.getId()),
            news.getSource(),
            news.getTitle(),
            news.getUrl(),
            news.getPublishedAt() != null ? news.getPublishedAt().toInstant() : null,
            new ArrayList<>(tickers),
            summary,
            importance,
            reason
        );
    }
    
    private ImportanceReason extractImportanceReason(News news) {
        if (news.getNewsScore() == null || news.getNewsScore().getReasonJson() == null) {
            return new ImportanceReason(0.0, 0.0, 0.0, 0.0);
        }
        
        try {
            String reasonJsonString = news.getNewsScore().getReasonJson();
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> reasonJson = mapper.readValue(reasonJsonString, Map.class);
            
            double sourceWeight = getDoubleValue(reasonJson, "source_weight", 0.0);
            double tickersHit = getDoubleValue(reasonJson, "tickers_hit", 0.0);
            double keywordsHit = getDoubleValue(reasonJson, "keywords_hit", 0.0);
            double freshness = getDoubleValue(reasonJson, "freshness", 0.0);
            
            return new ImportanceReason(sourceWeight, tickersHit, keywordsHit, freshness);
        } catch (Exception e) {
            log.warn("Failed to parse reason JSON: {}", news.getNewsScore().getReasonJson(), e);
            return new ImportanceReason(0.0, 0.0, 0.0, 0.0);
        }
    }
    
    private double getDoubleValue(Map<String, Object> map, String key, double defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }
    
    private String createSummary(String body) {
        if (body == null || body.trim().isEmpty()) {
            return "";
        }
        
        String trimmed = body.trim();
        if (trimmed.length() <= 240) {
            return trimmed;
        }
        
        // Try to cut at sentence boundary
        String truncated = trimmed.substring(0, 240);
        int lastPeriod = truncated.lastIndexOf('.');
        int lastExclamation = truncated.lastIndexOf('!');
        int lastQuestion = truncated.lastIndexOf('?');
        
        int lastSentenceEnd = Math.max(Math.max(lastPeriod, lastExclamation), lastQuestion);
        
        if (lastSentenceEnd > 200) {
            return truncated.substring(0, lastSentenceEnd + 1);
        } else {
            // Cut at word boundary
            int lastSpace = truncated.lastIndexOf(' ');
            if (lastSpace > 200) {
                return truncated.substring(0, lastSpace) + "...";
            } else {
                return truncated + "...";
            }
        }
    }
}