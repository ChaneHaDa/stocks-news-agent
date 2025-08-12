package com.newsagent.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "app.rss")
public class RssProperties {
    
    private List<RssSource> sources;
    private Collection collection;
    
    @Data
    public static class RssSource {
        private String name;
        private String url;
        private Double weight;
        private Boolean enabled = true;
    }
    
    @Data
    public static class Collection {
        private Boolean enabled = true;
        private Integer batchSize = 50;
        private Integer timeoutSeconds = 30;
        private Integer retryAttempts = 3;
        private Integer retryDelaySeconds = 5;
    }
}