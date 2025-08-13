package com.newsagent.api;

import com.newsagent.api.entity.News;
import com.newsagent.api.entity.NewsScore;
import com.newsagent.api.service.ContentNormalizer;
import com.newsagent.api.service.DiversityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiversityServiceTest {

    @Mock
    private ContentNormalizer contentNormalizer;

    private DiversityService diversityService;

    @BeforeEach
    void setUp() {
        diversityService = new DiversityService(contentNormalizer);
    }

    @Test
    void testApplyMMR() {
        // Setup mock data
        when(contentNormalizer.cleanHtml(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        
        List<News> newsList = createTestNewsList();
        
        // Apply MMR with lambda=0.7
        List<News> result = diversityService.applyMMR(newsList, 3, 0.7);
        
        // Should return exactly 3 items
        assertEquals(3, result.size());
        
        // First item should be the highest ranked
        assertEquals("삼성전자 주가 상승", result.get(0).getTitle());
    }

    @Test
    void testCalculateSimilarity() {
        when(contentNormalizer.cleanHtml(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        
        News news1 = createNewsItem(1L, "삼성전자 주가 상승", "삼성전자가 오늘 크게 상승했습니다", 9.0);
        News news2 = createNewsItem(2L, "삼성전자 급등", "삼성전자가 급등하며 시장을 이끌었습니다", 8.5);
        News news3 = createNewsItem(3L, "LG화학 실적 발표", "LG화학이 좋은 실적을 발표했습니다", 7.0);
        
        // Similar news should have high similarity
        double similarity1 = diversityService.calculateSimilarity(news1, news2);
        assertTrue(similarity1 > 0.3, "Similar news should have high similarity: " + similarity1);
        
        // Different news should have low similarity
        double similarity2 = diversityService.calculateSimilarity(news1, news3);
        assertTrue(similarity2 < 0.3, "Different news should have low similarity: " + similarity2);
    }

    @Test
    void testClusterByTopic() {
        when(contentNormalizer.cleanHtml(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        
        List<News> newsList = createTestNewsList();
        
        Map<Integer, List<News>> clusters = diversityService.clusterByTopic(newsList, 0.3);
        
        // Should create clusters
        assertFalse(clusters.isEmpty());
        
        // Each cluster should have at least one item
        clusters.values().forEach(cluster -> assertFalse(cluster.isEmpty()));
    }

    private List<News> createTestNewsList() {
        List<News> newsList = new ArrayList<>();
        
        newsList.add(createNewsItem(1L, "삼성전자 주가 상승", "삼성전자가 오늘 크게 상승했습니다", 9.0));
        newsList.add(createNewsItem(2L, "삼성전자 급등", "삼성전자가 급등하며 시장을 이끌었습니다", 8.5));
        newsList.add(createNewsItem(3L, "LG화학 실적 발표", "LG화학이 좋은 실적을 발표했습니다", 7.0));
        newsList.add(createNewsItem(4L, "SK하이닉스 투자", "SK하이닉스에 대한 투자가 늘어났습니다", 6.5));
        newsList.add(createNewsItem(5L, "NAVER 새로운 서비스", "NAVER가 새로운 서비스를 출시했습니다", 6.0));
        
        return newsList;
    }

    private News createNewsItem(Long id, String title, String body, Double rankScore) {
        News news = News.builder()
            .id(id)
            .title(title)
            .body(body)
            .source("테스트소스")
            .url("https://test.com/" + id)
            .publishedAt(OffsetDateTime.now())
            .build();
        
        NewsScore score = NewsScore.builder()
            .newsId(id)
            .importance(rankScore)
            .rankScore(rankScore)
            .reasonJson("{\"test\": true}")
            .build();
        
        news.setNewsScore(score);
        score.setNews(news);
        
        return news;
    }
}