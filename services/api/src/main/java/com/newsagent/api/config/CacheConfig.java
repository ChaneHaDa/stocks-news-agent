package com.newsagent.api.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        
        // Importance scores cache - 5 minutes TTL
        cacheManager.registerCustomCache("importance-scores", 
            Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(Duration.ofMinutes(5))
                .recordStats()
                .build());
        
        // Summaries cache - 24 hours TTL  
        cacheManager.registerCustomCache("summaries",
            Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(Duration.ofHours(24))
                .recordStats()
                .build());
        
        return cacheManager;
    }
}