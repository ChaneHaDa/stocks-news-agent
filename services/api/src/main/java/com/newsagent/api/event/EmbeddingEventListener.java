package com.newsagent.api.event;

import com.newsagent.api.entity.News;
import com.newsagent.api.entity.NewsEmbedding;
import com.newsagent.api.service.EmbeddingService;
import com.newsagent.api.service.FeatureFlagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * F1: Real-time embedding pipeline event listener
 * Automatically generates embeddings when news articles are saved
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmbeddingEventListener {
    
    private final EmbeddingService embeddingService;
    private final FeatureFlagService featureFlagService;
    private final ApplicationEventPublisher eventPublisher;
    
    /**
     * Handle new news articles by generating embeddings asynchronously
     */
    @EventListener
    @Async
    public void handleNewsSaved(NewsEvent.NewsSaved event) {
        try {
            // Check if real-time embedding generation is enabled
            if (!featureFlagService.isEnabled("feature.realtime_embedding.enabled", true)) {
                log.debug("Real-time embedding generation is disabled");
                return;
            }
            
            News news = event.getNews();
            
            // Only process new articles or significant updates
            if (!event.isNewArticle()) {
                log.debug("Skipping embedding generation for existing news ID: {}", news.getId());
                return;
            }
            
            log.debug("Processing embedding generation for news ID: {}", news.getId());
            
            // Generate embedding
            Optional<NewsEmbedding> embedding = embeddingService.generateEmbedding(news);
            
            if (embedding.isPresent()) {
                log.info("Generated embedding for news ID: {} (dimension: {})", 
                    news.getId(), embedding.get().getDimension());
                
                // Publish embedding generated event
                eventPublisher.publishEvent(new NewsEvent.EmbeddingGenerated(
                    this,
                    news.getId(),
                    embedding.get().getModelVersion(),
                    embedding.get().getDimension()
                ));
            } else {
                log.warn("Failed to generate embedding for news ID: {}", news.getId());
            }
            
        } catch (Exception e) {
            log.error("Error processing embedding generation for news ID: {}", 
                event.getNews().getId(), e);
        }
    }
    
    /**
     * Handle news updates that might require embedding regeneration
     */
    @EventListener
    @Async
    public void handleNewsUpdated(NewsEvent.NewsUpdated event) {
        try {
            // Check if embedding regeneration is enabled for updates
            if (!featureFlagService.isEnabled("feature.embedding_regeneration.enabled", false)) {
                log.debug("Embedding regeneration on updates is disabled");
                return;
            }
            
            News news = event.getNews();
            String updateType = event.getUpdateType();
            
            // Only regenerate embedding for content changes
            if (!"content".equals(updateType)) {
                log.debug("Skipping embedding regeneration for non-content update: {}", updateType);
                return;
            }
            
            log.debug("Processing embedding regeneration for updated news ID: {}", news.getId());
            
            // Force regeneration by generating new embedding
            Optional<NewsEmbedding> embedding = embeddingService.generateEmbedding(news);
            
            if (embedding.isPresent()) {
                log.info("Regenerated embedding for updated news ID: {}", news.getId());
                
                // Publish embedding generated event
                eventPublisher.publishEvent(new NewsEvent.EmbeddingGenerated(
                    this,
                    news.getId(),
                    embedding.get().getModelVersion(),
                    embedding.get().getDimension()
                ));
            } else {
                log.warn("Failed to regenerate embedding for updated news ID: {}", news.getId());
            }
            
        } catch (Exception e) {
            log.error("Error processing embedding regeneration for news ID: {}", 
                event.getNews().getId(), e);
        }
    }
    
    /**
     * Handle embedding generation completion (for metrics/monitoring)
     */
    @EventListener
    public void handleEmbeddingGenerated(NewsEvent.EmbeddingGenerated event) {
        try {
            log.debug("Embedding generated event received for news ID: {} (model: {}, dimension: {})",
                event.getNewsId(), event.getModelVersion(), event.getDimension());
            
            // Here you could add metrics collection, cache warming, etc.
            // For now, just log the successful generation
            
        } catch (Exception e) {
            log.error("Error handling embedding generated event for news ID: {}", 
                event.getNewsId(), e);
        }
    }
}