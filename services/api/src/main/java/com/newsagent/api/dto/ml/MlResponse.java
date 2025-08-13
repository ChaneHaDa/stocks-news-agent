package com.newsagent.api.dto.ml;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public class MlResponse {
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportanceResponse {
        private List<ImportanceResult> results;
        private String modelVersion;
        private OffsetDateTime processedAt;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SummarizeResponse {
        private String id;
        private String summary;
        private List<String> reasons;
        private List<String> policyFlags;
        private String modelVersion;
        private String methodUsed;
        private OffsetDateTime generatedAt;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmbedResponse {
        private List<EmbedResult> results;
        private String modelVersion;
        private Integer dimension;
        private OffsetDateTime processedAt;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportanceResult {
        private String id;
        private Double importanceP;
        private Map<String, Double> features;
        private Double confidence;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmbedResult {
        private String id;
        private List<Double> vector;
        private Double norm;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HealthResponse {
        private String status;
        private Map<String, ModelStatus> models;
        private OffsetDateTime timestamp;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelStatus {
        private Boolean loaded;
        private String version;
        private OffsetDateTime lastLoaded;
    }
}