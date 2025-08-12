package com.newsagent.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.newsagent.api.config.RssProperties;
import com.newsagent.api.dto.RssItem;
import com.newsagent.api.entity.News;
import com.newsagent.api.entity.NewsScore;
import com.newsagent.api.repository.NewsRepository;
import com.newsagent.api.repository.NewsScoreRepository;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewsIngestService {
    
    private final RssClient rssClient;
    private final ContentNormalizer contentNormalizer;
    private final ImportanceScorer importanceScorer;
    private final NewsRepository newsRepository;
    private final NewsScoreRepository newsScoreRepository;
    private final RssProperties rssProperties;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    
    private Counter itemsProcessedCounter;
    private Counter itemsSkippedCounter;
    private Counter itemsSavedCounter;
    private Counter errorsCounter;
    
    @PostConstruct
    public void initMetrics() {
        itemsProcessedCounter = Counter.builder("news.ingest.items.processed")
            .description("Total number of RSS items processed")
            .register(meterRegistry);
            
        itemsSkippedCounter = Counter.builder("news.ingest.items.skipped")
            .description("Number of RSS items skipped (duplicates)")
            .register(meterRegistry);
            
        itemsSavedCounter = Counter.builder("news.ingest.items.saved")
            .description("Number of news items successfully saved")
            .register(meterRegistry);
            
        errorsCounter = Counter.builder("news.ingest.errors")
            .description("Number of ingestion errors")
            .register(meterRegistry);
    }
    
    @Timed(value = "news.ingest.time", description = "Time spent ingesting news")
    @Transactional
    public IngestResult ingestAllSources() {
        log.info("Starting news ingestion from all sources");
        
        IngestResult result = IngestResult.builder()
            .startTime(OffsetDateTime.now())
            .build();
        
        try {
            List<RssItem> allItems = rssClient.fetchAllSources();
            result.setItemsFetched(allItems.size());
            
            log.info("Fetched {} items from all RSS sources", allItems.size());
            
            int processed = 0;
            int skipped = 0;
            int saved = 0;
            
            for (RssItem item : allItems) {
                try {
                    itemsProcessedCounter.increment();
                    processed++;
                    
                    IngestItemResult itemResult = ingestSingleItem(item);
                    
                    if (itemResult.isSkipped()) {
                        skipped++;
                        itemsSkippedCounter.increment();
                    } else if (itemResult.isSaved()) {
                        saved++;
                        itemsSavedCounter.increment();
                    }
                    
                } catch (Exception e) {
                    log.error("Failed to process RSS item: {}", item.getTitle(), e);
                    errorsCounter.increment();
                    result.incrementErrors();
                }
            }
            
            result.setItemsProcessed(processed);
            result.setItemsSkipped(skipped);
            result.setItemsSaved(saved);
            result.setEndTime(OffsetDateTime.now());
            
            log.info("Ingestion completed: {} fetched, {} processed, {} saved, {} skipped, {} errors", 
                result.getItemsFetched(), result.getItemsProcessed(), result.getItemsSaved(), 
                result.getItemsSkipped(), result.getErrors());
            
            return result;
            
        } catch (Exception e) {
            log.error("Failed to complete news ingestion", e);
            errorsCounter.increment();
            result.incrementErrors();
            result.setEndTime(OffsetDateTime.now());
            return result;
        }
    }
    
    @Transactional
    public IngestItemResult ingestSingleItem(RssItem item) {
        // 1. Normalize content
        String normalizedTitle = contentNormalizer.normalizeTitle(item.getTitle());
        String normalizedBody = contentNormalizer.extractBestContent(item.getDescription(), item.getContent());
        
        // 2. Generate deduplication key
        String dedupKey = contentNormalizer.generateDedupKey(
            normalizedTitle, 
            item.getSource(), 
            item.getPublishedAt()
        );
        
        // 3. Check for duplicates
        if (newsRepository.existsByDedupKey(dedupKey)) {
            log.debug("Skipping duplicate news item: {}", normalizedTitle);
            return IngestItemResult.builder()
                .skipped(true)
                .reason("Duplicate dedup_key: " + dedupKey)
                .build();
        }
        
        // 4. Quality checks
        if (contentNormalizer.isContentTooShort(normalizedBody)) {
            log.debug("Skipping news item with too short content: {}", normalizedTitle);
            return IngestItemResult.builder()
                .skipped(true)
                .reason("Content too short")
                .build();
        }
        
        if (contentNormalizer.isContentSuspicious(normalizedBody)) {
            log.debug("Skipping suspicious news item: {}", normalizedTitle);
            return IngestItemResult.builder()
                .skipped(true)
                .reason("Suspicious content")
                .build();
        }
        
        // 5. Create and save News entity
        News news = News.builder()
            .source(item.getSource())
            .url(item.getLink())
            .publishedAt(item.getPublishedAt())
            .title(normalizedTitle)
            .body(normalizedBody)
            .dedupKey(dedupKey)
            .lang("ko")
            .createdAt(OffsetDateTime.now())
            .build();
        
        news = newsRepository.save(news);
        
        // 6. Calculate importance score
        RssProperties.RssSource sourceConfig = findSourceConfig(item.getSource());
        ImportanceScorer.ScoreResult scoreResult = importanceScorer.calculateImportance(news, sourceConfig);
        
        // 7. Save score
        // Convert reasonJson to JSON string
        String reasonJsonString = null;
        try {
            reasonJsonString = objectMapper.writeValueAsString(scoreResult.getReasonJson());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize reason JSON", e);
            reasonJsonString = "{}";
        }
        
        NewsScore newsScore = NewsScore.builder()
            .news(news)  // @MapsId를 사용할 때는 news 객체만 설정
            .importance(scoreResult.getImportance())
            .reasonJson(reasonJsonString)
            .rankScore(scoreResult.getRankScore())
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
        
        newsScoreRepository.save(newsScore);
        
        log.debug("Saved news item: {} (importance: {})", normalizedTitle, scoreResult.getImportance());
        
        return IngestItemResult.builder()
            .saved(true)
            .newsId(news.getId())
            .importance(scoreResult.getImportance())
            .build();
    }
    
    private RssProperties.RssSource findSourceConfig(String sourceName) {
        return rssProperties.getSources().stream()
            .filter(source -> source.getName().equals(sourceName))
            .findFirst()
            .orElse(null);
    }
    
    @lombok.Builder
    @lombok.Data
    public static class IngestResult {
        private OffsetDateTime startTime;
        private OffsetDateTime endTime;
        @lombok.Builder.Default
        private int itemsFetched = 0;
        @lombok.Builder.Default
        private int itemsProcessed = 0;
        @lombok.Builder.Default
        private int itemsSaved = 0;
        @lombok.Builder.Default
        private int itemsSkipped = 0;
        @lombok.Builder.Default
        private int errors = 0;
        
        public void incrementErrors() {
            this.errors++;
        }
        
        public long getDurationMillis() {
            if (startTime != null && endTime != null) {
                return java.time.Duration.between(startTime, endTime).toMillis();
            }
            return 0;
        }
    }
    
    @lombok.Builder
    @lombok.Data
    public static class IngestItemResult {
        @lombok.Builder.Default
        private boolean saved = false;
        @lombok.Builder.Default
        private boolean skipped = false;
        private String reason;
        private Long newsId;
        private Double importance;
    }
}