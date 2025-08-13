package com.newsagent.api.dto.ml;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public class MlRequest {
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportanceRequest {
        private List<NewsArticle> items;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SummarizeRequest {
        private String id;
        private String title;
        private String body;
        private List<String> tickers;
        private Map<String, Object> options;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmbedRequest {
        private List<TextItem> items;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NewsArticle {
        private String id;
        private String title;
        private String body;
        private String source;
        
        @JsonProperty("published_at")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX")
        private OffsetDateTime publishedAt;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TextItem {
        private String id;
        private String text;
    }
}