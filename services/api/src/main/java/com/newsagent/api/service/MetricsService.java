package com.newsagent.api.service;

import com.newsagent.api.repository.NewsRepository;
import com.newsagent.api.repository.NewsScoreRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class MetricsService {
    
    private final MeterRegistry meterRegistry;
    private final NewsRepository newsRepository;
    private final NewsScoreRepository newsScoreRepository;
    
    private final AtomicLong totalNewsCount = new AtomicLong(0);
    private final AtomicLong newsCountLast24h = new AtomicLong(0);
    private final AtomicLong highImportanceNewsCount = new AtomicLong(0);
    
    @PostConstruct
    public void initGauges() {
        Gauge.builder("news.total.count", totalNewsCount, AtomicLong::get)
            .description("Total number of news articles")
            .register(meterRegistry);
            
        Gauge.builder("news.count.24h", newsCountLast24h, AtomicLong::get)
            .description("Number of news articles in last 24 hours")
            .register(meterRegistry);
            
        Gauge.builder("news.high_importance.count", highImportanceNewsCount, AtomicLong::get)
            .description("Number of high importance news articles (importance >= 0.7)")
            .register(meterRegistry);
    }
    
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void updateMetrics() {
        try {
            updateNewsMetrics();
        } catch (Exception e) {
            log.error("Failed to update metrics", e);
        }
    }
    
    private void updateNewsMetrics() {
        // Total news count
        long total = newsRepository.count();
        totalNewsCount.set(total);
        
        // News count in last 24 hours
        OffsetDateTime yesterday = OffsetDateTime.now().minus(24, ChronoUnit.HOURS);
        long last24h = newsRepository.countNewsSince(yesterday);
        newsCountLast24h.set(last24h);
        
        // High importance news count
        long highImportance = newsScoreRepository.countHighImportanceNewsSince(0.7, yesterday);
        highImportanceNewsCount.set(highImportance);
        
        // Source breakdown metrics
        updateSourceMetrics();
        
        log.debug("Updated metrics: total={}, last24h={}, highImportance={}", 
            total, last24h, highImportance);
    }
    
    private void updateSourceMetrics() {
        OffsetDateTime yesterday = OffsetDateTime.now().minus(24, ChronoUnit.HOURS);
        List<Object[]> sourceStats = newsRepository.countNewsBySourceSince(yesterday);
        
        for (Object[] stat : sourceStats) {
            String source = (String) stat[0];
            Long count = (Long) stat[1];
            
            Gauge.builder("news.source.count", count, Number::longValue)
                .description("Number of news articles by source in last 24 hours")
                .tag("source", source)
                .register(meterRegistry);
        }
    }
    
    public void recordIngestDuration(long durationMs) {
        meterRegistry.timer("news.ingest.duration")
            .record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }
    
    public void recordAverageImportanceScore() {
        try {
            OffsetDateTime yesterday = OffsetDateTime.now().minus(24, ChronoUnit.HOURS);
            newsScoreRepository.findAverageImportanceSince(yesterday)
                .ifPresent(avgScore -> {
                    Gauge.builder("news.importance.average", avgScore, Number::doubleValue)
                        .description("Average importance score in last 24 hours")
                        .register(meterRegistry);
                });
        } catch (Exception e) {
            log.warn("Failed to calculate average importance score", e);
        }
    }
}