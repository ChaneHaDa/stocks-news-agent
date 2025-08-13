package com.newsagent.api;

import com.newsagent.api.entity.News;
import com.newsagent.api.service.MlClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class MlClientIntegrationTest {

    @Autowired
    private MlClient mlClient;

    @Test
    void testImportanceScoreFallback() {
        // Create test news
        News news = News.builder()
            .id(1L)
            .title("테스트 뉴스 제목")
            .body("이것은 테스트용 뉴스 내용입니다. 주가와 실적에 대한 중요한 정보가 포함되어 있습니다.")
            .source("테스트 소스")
            .publishedAt(OffsetDateTime.now())
            .build();

        // Test fallback method (ML service not available)
        Optional<Double> score = mlClient.getImportanceScore(news);
        
        // Should return fallback score
        assertTrue(score.isPresent());
        assertTrue(score.get() >= 0.0 && score.get() <= 1.0);
    }

    @Test
    void testSummaryFallback() {
        // Create test news
        News news = News.builder()
            .id(2L)
            .title("긴급 뉴스")
            .body("이것은 매우 긴 뉴스 내용입니다. 삼성전자의 주가가 크게 상승했습니다. " +
                  "실적 발표 이후 투자자들의 관심이 높아지고 있습니다. " +
                  "앞으로의 전망도 밝을 것으로 예상됩니다.")
            .source("경제신문")
            .publishedAt(OffsetDateTime.now())
            .build();

        List<String> tickers = List.of("005930");

        // Test fallback method (ML service not available)
        Optional<String> summary = mlClient.getSummary(news, tickers);
        
        // Should return fallback summary
        assertTrue(summary.isPresent());
        assertFalse(summary.get().isEmpty());
        assertTrue(summary.get().length() <= 240);
    }

    @Test
    void testHealthCheck() {
        // Test health check (should return false when ML service is not running)
        boolean isHealthy = mlClient.isHealthy();
        
        // In test environment, ML service is not running, so should be false
        assertFalse(isHealthy);
    }
}