package com.newsagent.api.dto.ml;

import com.fasterxml.jackson.annotation.JsonProperty;
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
        
        @JsonProperty("model_version")
        private String modelVersion;
        
        @JsonProperty("processed_at")
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
        
        @JsonProperty("policy_flags")
        private List<String> policyFlags;
        
        @JsonProperty("model_version")
        private String modelVersion;
        
        @JsonProperty("method_used")
        private String methodUsed;
        
        @JsonProperty("generated_at")
        private OffsetDateTime generatedAt;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmbedResponse {
        private List<EmbedResult> results;
        
        @JsonProperty("model_version")
        private String modelVersion;
        
        private Integer dimension;
        
        @JsonProperty("processed_at")
        private OffsetDateTime processedAt;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportanceResult {
        private String id;
        
        @JsonProperty("importance_p")
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
        
        @JsonProperty("last_loaded")
        private OffsetDateTime lastLoaded;
    }
}