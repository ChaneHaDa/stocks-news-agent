package com.newsagent.api.event;

import com.newsagent.api.entity.News;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEvent;

/**
 * News-related application events for F1 real-time embedding pipeline
 */
public class NewsEvent {
    
    /**
     * Event fired when a new news article is saved
     */
    @Getter
    public static class NewsSaved extends ApplicationEvent {
        private final News news;
        private final boolean isNewArticle;
        
        public NewsSaved(Object source, News news, boolean isNewArticle) {
            super(source);
            this.news = news;
            this.isNewArticle = isNewArticle;
        }
    }
    
    /**
     * Event fired when news article is updated
     */
    @Getter
    public static class NewsUpdated extends ApplicationEvent {
        private final News news;
        private final String updateType; // "content", "score", "metadata"
        
        public NewsUpdated(Object source, News news, String updateType) {
            super(source);
            this.news = news;
            this.updateType = updateType;
        }
    }
    
    /**
     * Event fired when embedding is generated
     */
    @Getter
    public static class EmbeddingGenerated extends ApplicationEvent {
        private final Long newsId;
        private final String modelVersion;
        private final int dimension;
        
        public EmbeddingGenerated(Object source, Long newsId, String modelVersion, int dimension) {
            super(source);
            this.newsId = newsId;
            this.modelVersion = modelVersion;
            this.dimension = dimension;
        }
    }
}