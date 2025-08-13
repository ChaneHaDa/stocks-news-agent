package com.newsagent.api.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@RequiredArgsConstructor
public class RestTemplateConfig {
    
    private final MlServiceConfig mlServiceConfig;
    
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplateBuilder()
            .setConnectTimeout(mlServiceConfig.getConnectTimeout())
            .setReadTimeout(mlServiceConfig.getReadTimeout())
            .build();
    }
}