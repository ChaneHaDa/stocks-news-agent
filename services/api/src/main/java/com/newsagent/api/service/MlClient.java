package com.newsagent.api.service;

import com.newsagent.api.config.MlServiceConfig;
import com.newsagent.api.dto.ml.MlRequest;
import com.newsagent.api.dto.ml.MlResponse;
import com.newsagent.api.entity.News;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.HttpServerErrorException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MlClient {
    
    private final RestTemplate restTemplate;
    private final MlServiceConfig config;
    
    @CircuitBreaker(name = "ml-service", fallbackMethod = "importanceFallback")
    @Retry(name = "ml-service")
    @Cacheable(value = "importance-scores", key = "#news.id", condition = "#result != null")
    public Optional<Double> getImportanceScore(News news) {
        if (!config.isEnableImportance()) {
            return Optional.empty();
        }
        
        try {
            log.debug("Getting importance score for news: {}", news.getId());
            
            MlRequest.ImportanceRequest request = MlRequest.ImportanceRequest.builder()
                .items(List.of(MlRequest.NewsArticle.builder()
                    .id(String.valueOf(news.getId()))
                    .title(news.getTitle())
                    .body(news.getBody())
                    .source(news.getSource())
                    .publishedAt(news.getPublishedAt())
                    .build()))
                .build();
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<MlRequest.ImportanceRequest> entity = new HttpEntity<>(request, headers);
            
            MlResponse.ImportanceResponse response = restTemplate.postForObject(
                config.getBaseUrl() + "/v1/importance:score",
                entity,
                MlResponse.ImportanceResponse.class
            );
            
            if (response != null && !response.getResults().isEmpty()) {
                MlResponse.ImportanceResult result = response.getResults().get(0);
                log.debug("Received importance score: {} for news: {}", 
                         result.getImportanceP(), news.getId());
                return Optional.of(result.getImportanceP());
            }
            
            return Optional.empty();
            
        } catch (ResourceAccessException e) {
            log.warn("ML service timeout for importance scoring: {}", e.getMessage());
            throw e; // Will trigger circuit breaker
        } catch (HttpServerErrorException e) {
            log.error("ML service error for importance scoring: {}", e.getMessage());
            throw e; // Will trigger circuit breaker
        } catch (Exception e) {
            log.error("Unexpected error getting importance score", e);
            return Optional.empty();
        }
    }
    
    @CircuitBreaker(name = "ml-service", fallbackMethod = "summarizeFallback")
    @Retry(name = "ml-service")
    @Cacheable(value = "summaries", key = "#news.id", condition = "#result != null")
    public Optional<String> getSummary(News news, List<String> tickers) {
        if (!config.isEnableSummarize()) {
            return Optional.empty();
        }
        
        try {
            log.debug("Getting summary for news: {}", news.getId());
            
            MlRequest.SummarizeRequest request = MlRequest.SummarizeRequest.builder()
                .id(String.valueOf(news.getId()))
                .title(news.getTitle())
                .body(news.getBody())
                .tickers(tickers)
                .options(Map.of("style", "extractive", "max_length", 240))
                .build();
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<MlRequest.SummarizeRequest> entity = new HttpEntity<>(request, headers);
            
            MlResponse.SummarizeResponse response = restTemplate.postForObject(
                config.getBaseUrl() + "/v1/summarize",
                entity,
                MlResponse.SummarizeResponse.class
            );
            
            if (response != null && response.getSummary() != null) {
                log.debug("Received summary for news: {}", news.getId());
                return Optional.of(response.getSummary());
            }
            
            return Optional.empty();
            
        } catch (ResourceAccessException e) {
            log.warn("ML service timeout for summarization: {}", e.getMessage());
            throw e; // Will trigger circuit breaker
        } catch (HttpServerErrorException e) {
            log.error("ML service error for summarization: {}", e.getMessage());
            throw e; // Will trigger circuit breaker
        } catch (Exception e) {
            log.error("Unexpected error getting summary", e);
            return Optional.empty();
        }
    }
    
    @CircuitBreaker(name = "ml-service", fallbackMethod = "embedFallback")
    @Retry(name = "ml-service")
    public Optional<List<Double>> getEmbedding(String text) {
        if (!config.isEnableEmbed()) {
            return Optional.empty();
        }
        
        try {
            log.debug("Getting embedding for text length: {}", text.length());
            
            MlRequest.EmbedRequest request = MlRequest.EmbedRequest.builder()
                .items(List.of(MlRequest.TextItem.builder()
                    .id("temp")
                    .text(text)
                    .build()))
                .build();
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<MlRequest.EmbedRequest> entity = new HttpEntity<>(request, headers);
            
            MlResponse.EmbedResponse response = restTemplate.postForObject(
                config.getBaseUrl() + "/v1/embed",
                entity,
                MlResponse.EmbedResponse.class
            );
            
            if (response != null && !response.getResults().isEmpty()) {
                MlResponse.EmbedResult result = response.getResults().get(0);
                log.debug("Received embedding with dimension: {}", response.getDimension());
                return Optional.of(result.getVector());
            }
            
            return Optional.empty();
            
        } catch (ResourceAccessException e) {
            log.warn("ML service timeout for embedding: {}", e.getMessage());
            throw e; // Will trigger circuit breaker
        } catch (HttpServerErrorException e) {
            log.error("ML service error for embedding: {}", e.getMessage());
            throw e; // Will trigger circuit breaker
        } catch (Exception e) {
            log.error("Unexpected error getting embedding", e);
            return Optional.empty();
        }
    }
    
    public boolean isHealthy() {
        try {
            MlResponse.HealthResponse response = restTemplate.getForObject(
                config.getBaseUrl() + "/admin/health",
                MlResponse.HealthResponse.class
            );
            
            return response != null && "healthy".equals(response.getStatus());
            
        } catch (Exception e) {
            log.warn("ML service health check failed: {}", e.getMessage());
            return false;
        }
    }
    
    // Fallback methods
    private Optional<Double> importanceFallback(News news, Exception ex) {
        log.warn("Using fallback for importance scoring, news: {}, error: {}", 
                news.getId(), ex.getMessage());
        
        // Simple rule-based fallback
        double fallbackScore = calculateRuleBasedImportanceScore(news);
        return Optional.of(fallbackScore);
    }
    
    private Optional<String> summarizeFallback(News news, List<String> tickers, Exception ex) {
        log.warn("Using fallback for summarization, news: {}, error: {}", 
                news.getId(), ex.getMessage());
        
        // Simple extractive summarization
        return Optional.of(createSimpleSummary(news.getTitle(), news.getBody()));
    }
    
    private Optional<List<Double>> embedFallback(String text, Exception ex) {
        log.warn("Using fallback for embedding, error: {}", ex.getMessage());
        
        // No reliable fallback for embeddings
        return Optional.empty();
    }
    
    private double calculateRuleBasedImportanceScore(News news) {
        double score = 0.5; // Base score
        
        // Simple heuristics
        String title = news.getTitle().toLowerCase();
        String body = news.getBody().toLowerCase();
        
        // Check for important keywords
        String[] importantKeywords = {"긴급", "속보", "주가", "실적", "발표", "인수합병"};
        for (String keyword : importantKeywords) {
            if (title.contains(keyword) || body.contains(keyword)) {
                score += 0.1;
            }
        }
        
        // Freshness bonus
        if (news.getPublishedAt() != null) {
            long hoursAgo = java.time.Duration.between(news.getPublishedAt(), OffsetDateTime.now()).toHours();
            if (hoursAgo <= 1) {
                score += 0.2;
            } else if (hoursAgo <= 6) {
                score += 0.1;
            }
        }
        
        return Math.min(1.0, Math.max(0.0, score));
    }
    
    private String createSimpleSummary(String title, String body) {
        // Simple extractive approach
        if (body == null || body.length() <= 240) {
            return title + (body != null ? ". " + body : "");
        }
        
        // Take first sentence or two
        String[] sentences = body.split("\\. ");
        StringBuilder summary = new StringBuilder(title).append(". ");
        
        for (String sentence : sentences) {
            if (summary.length() + sentence.length() + 2 <= 240) {
                summary.append(sentence).append(". ");
            } else {
                break;
            }
        }
        
        return summary.toString().trim();
    }
}