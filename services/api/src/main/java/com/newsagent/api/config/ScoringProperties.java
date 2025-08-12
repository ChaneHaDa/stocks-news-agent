package com.newsagent.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "app.scoring")
public class ScoringProperties {
    
    private Freshness freshness;
    private Keywords keywords;
    private Tickers tickers;
    
    @Data
    public static class Freshness {
        private Double hours3 = 1.0;
        private Double hours24 = 0.5;
        private Double hours72 = 0.2;
    }
    
    @Data
    public static class Keywords {
        private List<String> highImpact;
        private List<String> mediumImpact;
    }
    
    @Data
    public static class Tickers {
        private String pattern = "\\d{6}";
        private String aliasesFile = "classpath:tickers/aliases.yml";
    }
}