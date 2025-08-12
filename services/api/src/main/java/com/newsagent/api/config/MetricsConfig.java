package com.newsagent.api.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
@RequiredArgsConstructor
public class MetricsConfig {
    
    @Bean
    public MeterFilter metricsCommonTags() {
        return MeterFilter.commonTags(
            Arrays.asList(
                Tag.of("application", "news-agent-api"),
                Tag.of("version", "0.1.0")
            )
        );
    }
}