package com.newsagent.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "ml.service")
@Data
public class MlServiceConfig {
    
    private String baseUrl = "http://localhost:8001";
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(30);
    private int maxRetries = 3;
    private Duration retryDelay = Duration.ofMillis(500);
    
    // Circuit breaker settings
    private int failureRateThreshold = 50;
    private Duration waitDurationInOpenState = Duration.ofMinutes(1);
    private int slidingWindowSize = 100;
    private int minimumNumberOfCalls = 10;
    
    // Feature flags
    private boolean enableImportance = true;
    private boolean enableSummarize = true;
    private boolean enableEmbed = true;
    
    // Cache settings
    private Duration importanceCacheTtl = Duration.ofMinutes(5);
    private Duration summaryCacheTtl = Duration.ofHours(24);
    private int maxCacheSize = 1000;
}