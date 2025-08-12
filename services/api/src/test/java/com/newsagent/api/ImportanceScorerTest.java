package com.newsagent.api;

import com.newsagent.api.config.RssProperties;
import com.newsagent.api.config.ScoringProperties;
import com.newsagent.api.entity.News;
import com.newsagent.api.service.ImportanceScorer;
import com.newsagent.api.service.TickerMatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ImportanceScorerTest {

    @Mock
    private TickerMatcher tickerMatcher;

    private ImportanceScorer importanceScorer;
    private ScoringProperties scoringProperties;

    @BeforeEach
    void setUp() {
        // Setup scoring properties
        scoringProperties = new ScoringProperties();
        
        ScoringProperties.Freshness freshness = new ScoringProperties.Freshness();
        freshness.setHours3(1.0);
        freshness.setHours24(0.5);
        freshness.setHours72(0.2);
        scoringProperties.setFreshness(freshness);
        
        ScoringProperties.Keywords keywords = new ScoringProperties.Keywords();
        keywords.setHighImpact(List.of("실적", "배당", "IPO"));
        keywords.setMediumImpact(List.of("투자", "수익"));
        scoringProperties.setKeywords(keywords);
        
        importanceScorer = new ImportanceScorer(scoringProperties, tickerMatcher);
    }

    @Test
    void testCalculateImportance_HighScore() {
        // Mock ticker matching
        when(tickerMatcher.calculateTickerMatchStrength(anyString())).thenReturn(0.8);
        when(tickerMatcher.findTickers(anyString())).thenReturn(Set.of("005930"));

        News news = News.builder()
            .title("삼성전자 실적 발표")
            .body("삼성전자가 분기 실적을 발표했습니다. 배당도 증가했습니다.")
            .publishedAt(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1)) // Fresh news
            .build();

        RssProperties.RssSource source = new RssProperties.RssSource();
        source.setWeight(1.0);

        ImportanceScorer.ScoreResult result = importanceScorer.calculateImportance(news, source);

        assertNotNull(result);
        assertTrue(result.getImportance() > 2.0); // Should be high due to source weight + tickers + keywords + freshness
        assertNotNull(result.getReasonJson());
        assertNotNull(result.getRankScore());
        
        // Check reason breakdown
        assertEquals(1.0, result.getReasonJson().get("source_weight"));
        assertEquals(0.8, result.getReasonJson().get("tickers_hit"));
        assertTrue((Double) result.getReasonJson().get("freshness") > 0);
    }

    @Test
    void testCalculateImportance_LowScore() {
        // Mock no ticker matching
        when(tickerMatcher.calculateTickerMatchStrength(anyString())).thenReturn(0.0);
        when(tickerMatcher.findTickers(anyString())).thenReturn(Set.of());

        News news = News.builder()
            .title("일반 뉴스")
            .body("특별한 내용이 없는 일반적인 뉴스입니다.")
            .publishedAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(5)) // Old news
            .build();

        RssProperties.RssSource source = new RssProperties.RssSource();
        source.setWeight(0.5);

        ImportanceScorer.ScoreResult result = importanceScorer.calculateImportance(news, source);

        assertNotNull(result);
        assertTrue(result.getImportance() < 1.0); // Should be low
        assertEquals(0.5, result.getReasonJson().get("source_weight"));
        assertEquals(0.0, result.getReasonJson().get("tickers_hit"));
        assertEquals(0.0, result.getReasonJson().get("freshness"));
    }

    @Test
    void testCalculateImportance_WithKeywords() {
        when(tickerMatcher.calculateTickerMatchStrength(anyString())).thenReturn(0.0);
        when(tickerMatcher.findTickers(anyString())).thenReturn(Set.of());

        News news = News.builder()
            .title("투자 관련 뉴스")
            .body("새로운 투자 기회가 있습니다. 수익성이 좋은 실적을 보이고 있습니다.")
            .publishedAt(OffsetDateTime.now(ZoneOffset.UTC))
            .build();

        RssProperties.RssSource source = new RssProperties.RssSource();
        source.setWeight(0.8);

        ImportanceScorer.ScoreResult result = importanceScorer.calculateImportance(news, source);

        // Should have keyword score (실적 = high impact 0.3, 투자/수익 = medium impact 0.2 each)
        Double keywordsHit = (Double) result.getReasonJson().get("keywords_hit");
        assertTrue(keywordsHit > 0.5); // 0.3 + 0.2 + 0.2 = 0.7
    }

    @Test
    void testCalculateImportance_QualityPenalty() {
        when(tickerMatcher.calculateTickerMatchStrength(anyString())).thenReturn(0.0);
        when(tickerMatcher.findTickers(anyString())).thenReturn(Set.of());

        News news = News.builder()
            .title("짧은 제목") // Too short
            .body("너무 짧은 내용") // Too short
            .publishedAt(OffsetDateTime.now(ZoneOffset.UTC))
            .build();

        RssProperties.RssSource source = new RssProperties.RssSource();
        source.setWeight(1.0);

        ImportanceScorer.ScoreResult result = importanceScorer.calculateImportance(news, source);

        // Should have quality penalty
        assertTrue(result.getReasonJson().containsKey("quality_penalty"));
        Double penalty = (Double) result.getReasonJson().get("quality_penalty");
        assertTrue(penalty < 0);
    }

    @Test
    void testCalculateImportance_ScoreBounds() {
        // Test that score is always between 0 and 10
        when(tickerMatcher.calculateTickerMatchStrength(anyString())).thenReturn(10.0); // Extreme value
        when(tickerMatcher.findTickers(anyString())).thenReturn(Set.of("005930"));

        News news = News.builder()
            .title("실적 배당 IPO 투자 수익".repeat(10)) // Many keywords
            .body("실적 배당 IPO 투자 수익".repeat(100))
            .publishedAt(OffsetDateTime.now(ZoneOffset.UTC))
            .build();

        RssProperties.RssSource source = new RssProperties.RssSource();
        source.setWeight(10.0); // Extreme value

        ImportanceScorer.ScoreResult result = importanceScorer.calculateImportance(news, source);

        assertTrue(result.getImportance() >= 0.0);
        assertTrue(result.getImportance() <= 10.0);
    }

    @Test
    void testCalculateImportance_NullSource() {
        when(tickerMatcher.calculateTickerMatchStrength(anyString())).thenReturn(0.0);
        when(tickerMatcher.findTickers(anyString())).thenReturn(Set.of());

        News news = News.builder()
            .title("테스트 뉴스")
            .body("테스트 내용")
            .publishedAt(OffsetDateTime.now(ZoneOffset.UTC))
            .build();

        ImportanceScorer.ScoreResult result = importanceScorer.calculateImportance(news, null);

        assertNotNull(result);
        assertEquals(0.5, result.getReasonJson().get("source_weight")); // Default when source is null
    }
}