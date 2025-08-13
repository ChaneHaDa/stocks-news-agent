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
    private final DiversityService diversityService;
    
    public List<NewsItem> getTopNews(int limit, Set<String> tickerFilters, String lang, OffsetDateTime since) {
        return getTopNews(limit, tickerFilters, lang, since, "rank", true);
    }
    
    public List<NewsItem> getTopNews(int limit, Set<String> tickerFilters, String lang, OffsetDateTime since, String sort, boolean applyDiversity) {
        // Fetch more items initially for diversity filtering
        int fetchLimit = applyDiversity ? Math.min(limit * 3, 100) : limit;
        Pageable pageable = PageRequest.of(0, fetchLimit);
        
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
                .collect(Collectors.toList());
            
            // Apply diversity if requested
            if (applyDiversity && filteredNews.size() > limit) {
                filteredNews = applyDiversityFiltering(filteredNews, limit);
            } else if (filteredNews.size() > limit) {
                filteredNews = filteredNews.subList(0, limit);
            }
            
            return convertToNewsItems(filteredNews);
        } else {
            // Sort based on parameter
            if ("time".equals(sort)) {
                newsPage = newsRepository.findByOrderByPublishedAtDesc(pageable);
            } else {
                // Default to rank-based sorting
                newsPage = newsRepository.findNewsWithFilters(null, null, since, pageable);
            }
            
            List<News> newsList = newsPage.getContent();
            
            // Apply diversity if requested
            if (applyDiversity && newsList.size() > limit) {
                newsList = applyDiversityFiltering(newsList, limit);
            } else if (newsList.size() > limit) {
                newsList = newsList.subList(0, limit);
            }
            
            return convertToNewsItems(newsList);
        }
    }
    
    private List<News> applyDiversityFiltering(List<News> newsList, int targetSize) {
        // Apply MMR with lambda=0.7 (70% relevance, 30% diversity)
        List<News> diverseNews = diversityService.applyMMR(newsList, targetSize, 0.7);
        
        // Ensure we don't have more than 2 items from the same topic cluster
        Map<Integer, List<News>> clusters = diversityService.clusterByTopic(diverseNews, 0.6);
        
        List<News> finalList = new ArrayList<>();
        for (List<News> cluster : clusters.values()) {
            // Take at most 2 items from each cluster
            int itemsToTake = Math.min(2, cluster.size());
            finalList.addAll(cluster.subList(0, itemsToTake));
            
            if (finalList.size() >= targetSize) {
                break;
            }
        }
        
        // If we still need more items and have space, add remaining items
        if (finalList.size() < targetSize) {
            for (News news : diverseNews) {
                if (!finalList.contains(news) && finalList.size() < targetSize) {
                    finalList.add(news);
                }
            }
        }
        
        return finalList.subList(0, Math.min(targetSize, finalList.size()));
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