package com.newsagent.api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(value = "scheduling.rss-collection.enabled", havingValue = "true", matchIfMissing = true)
public class NewsScheduler {
    
    private final NewsIngestService newsIngestService;
    
    @Scheduled(cron = "${scheduling.rss-collection.cron:0 */10 * * * *}")
    public void collectNews() {
        log.info("Starting scheduled news collection");
        
        try {
            NewsIngestService.IngestResult result = newsIngestService.ingestAllSources();
            
            log.info("Scheduled news collection completed in {}ms: {} fetched, {} saved, {} skipped, {} errors",
                result.getDurationMillis(),
                result.getItemsFetched(),
                result.getItemsSaved(), 
                result.getItemsSkipped(),
                result.getErrors());
                
        } catch (Exception e) {
            log.error("Scheduled news collection failed", e);
        }
    }
}