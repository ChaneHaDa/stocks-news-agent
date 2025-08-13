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
    private final MlClient mlClient;
    
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
        // For top N items (e.g., 30), call ML service for summaries
        int summaryLimit = Math.min(30, newsList.size());
        
        List<NewsItem> result = new ArrayList<>();
        
        for (int i = 0; i < newsList.size(); i++) {
            News news = newsList.get(i);
            boolean generateSummary = i < summaryLimit; // Only for top items
            NewsItem item = convertToNewsItem(news, generateSummary);
            result.add(item);
        }
        
        return result;
    }
    
    private NewsItem convertToNewsItem(News news) {
        return convertToNewsItem(news, false); // Default: no ML summary
    }
    
    private NewsItem convertToNewsItem(News news, boolean generateMlSummary) {
        Set<String> tickers = tickerMatcher.findTickers(news.getBody());
        List<String> tickerList = new ArrayList<>(tickers);
        
        // Get or calculate importance score via ML
        Double importance = getImportanceScore(news);
        
        // Get or generate summary
        String summary = getSummary(news, tickerList, generateMlSummary);
        
        ImportanceReason reason = extractImportanceReason(news);
        
        return new NewsItem(
            String.valueOf(news.getId()),
            news.getSource(),
            news.getTitle(),
            news.getUrl(),
            news.getPublishedAt() != null ? news.getPublishedAt().toInstant() : null,
            tickerList,
            summary,
            importance,
            reason
        );
    }
    
    private Double getImportanceScore(News news) {
        // First check if we already have ML-generated score in DB
        if (news.getNewsScore() != null && news.getNewsScore().getImportanceP() != null) {
            log.debug("Using cached ML importance score for news: {}", news.getId());
            return news.getNewsScore().getImportanceP();
        }
        
        // Try to get ML importance score
        try {
            Optional<Double> mlImportance = mlClient.getImportanceScore(news);
            if (mlImportance.isPresent()) {
                Double score = mlImportance.get();
                log.debug("Got ML importance score {} for news: {}", score, news.getId());
                
                // Save to DB for future use (in a real implementation, you'd do this async)
                updateImportanceScore(news, score);
                
                return score;
            }
        } catch (Exception e) {
            log.warn("Failed to get ML importance score for news: {}, using fallback", news.getId(), e);
        }
        
        // Fallback to existing rule-based score
        return news.getNewsScore() != null ? news.getNewsScore().getImportance() : 0.5;
    }
    
    private String getSummary(News news, List<String> tickers, boolean generateMlSummary) {
        // First check if we already have ML-generated summary in DB
        if (news.getNewsScore() != null && news.getNewsScore().getSummary() != null) {
            log.debug("Using cached ML summary for news: {}", news.getId());
            return news.getNewsScore().getSummary();
        }
        
        // Generate ML summary only for top articles
        if (generateMlSummary) {
            try {
                Optional<String> mlSummary = mlClient.getSummary(news, tickers);
                if (mlSummary.isPresent()) {
                    String summary = mlSummary.get();
                    log.debug("Got ML summary for news: {}", news.getId());
                    
                    // Save to DB for future use (in a real implementation, you'd do this async)
                    updateSummary(news, summary);
                    
                    return summary;
                }
            } catch (Exception e) {
                log.warn("Failed to get ML summary for news: {}, using fallback", news.getId(), e);
            }
        }
        
        // Fallback to simple extractive summary
        return createSummary(news.getBody());
    }
    
    private void updateImportanceScore(News news, Double score) {
        // In a real implementation, this would be done asynchronously
        // For now, just log that we would update
        log.debug("Would update importance score {} for news: {}", score, news.getId());
        // TODO: Async update to database
    }
    
    private void updateSummary(News news, String summary) {
        // In a real implementation, this would be done asynchronously
        // For now, just log that we would update
        log.debug("Would update summary for news: {}", news.getId());
        // TODO: Async update to database
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