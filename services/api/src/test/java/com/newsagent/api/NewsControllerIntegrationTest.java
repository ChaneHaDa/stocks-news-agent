package com.newsagent.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.newsagent.api.entity.News;
import com.newsagent.api.entity.NewsScore;
import com.newsagent.api.repository.NewsRepository;
import com.newsagent.api.repository.NewsScoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test")
@Transactional
public class NewsControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NewsRepository newsRepository;

    @Autowired
    private NewsScoreRepository newsScoreRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Clean up before each test
        newsScoreRepository.deleteAll();
        newsRepository.deleteAll();

        // Create test data
        createTestNews();
    }

    private void createTestNews() {
        // Create news with different importance scores
        News news1 = News.builder()
            .source("Test Source 1")
            .url("http://test.com/news1")
            .title("삼성전자 실적 발표")
            .body("삼성전자가 2분기 실적을 발표했습니다. 005930 종목코드로 거래되는 삼성전자의 실적이 예상을 상회했습니다.")
            .dedupKey("test-key-1")
            .publishedAt(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1))
            .lang("ko")
            .build();

        News news2 = News.builder()
            .source("Test Source 2")
            .url("http://test.com/news2")
            .title("카카오 사업 확장")
            .body("카카오가 새로운 사업을 확장한다고 발표했습니다. 035720 카카오 주식이 주목받고 있습니다.")
            .dedupKey("test-key-2")
            .publishedAt(OffsetDateTime.now(ZoneOffset.UTC).minusHours(2))
            .lang("ko")
            .build();

        News news3 = News.builder()
            .source("Test Source 1")
            .url("http://test.com/news3")
            .title("일반 시장 뉴스")
            .body("일반적인 시장 동향에 대한 뉴스입니다. 특별한 종목이 언급되지 않았습니다.")
            .dedupKey("test-key-3")
            .publishedAt(OffsetDateTime.now(ZoneOffset.UTC).minusHours(3))
            .lang("ko")
            .build();

        news1 = newsRepository.save(news1);
        news2 = newsRepository.save(news2);
        news3 = newsRepository.save(news3);

        // Create scores
        Map<String, Object> reason1 = Map.of(
            "source_weight", 1.0,
            "tickers_hit", 0.8,
            "keywords_hit", 0.6,
            "freshness", 1.0
        );

        Map<String, Object> reason2 = Map.of(
            "source_weight", 0.8,
            "tickers_hit", 0.5,
            "keywords_hit", 0.3,
            "freshness", 0.5
        );

        Map<String, Object> reason3 = Map.of(
            "source_weight", 1.0,
            "tickers_hit", 0.0,
            "keywords_hit", 0.0,
            "freshness", 0.2
        );

        NewsScore score1 = NewsScore.builder()
            .newsId(news1.getId())
            .news(news1)
            .importance(0.85)
            .reasonJson(reason1)
            .rankScore(0.80)
            .build();

        NewsScore score2 = NewsScore.builder()
            .newsId(news2.getId())
            .news(news2)
            .importance(0.65)
            .reasonJson(reason2)
            .rankScore(0.55)
            .build();

        NewsScore score3 = NewsScore.builder()
            .newsId(news3.getId())
            .news(news3)
            .importance(0.25)
            .reasonJson(reason3)
            .rankScore(0.15)
            .build();

        newsScoreRepository.save(score1);
        newsScoreRepository.save(score2);
        newsScoreRepository.save(score3);
    }

    @Test
    void testGetTopNews() throws Exception {
        mockMvc.perform(get("/news/top")
                .param("n", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.items[0].importance", greaterThan(0.8)))
                .andExpect(jsonPath("$.items[0].title", containsString("삼성전자")))
                .andExpect(jsonPath("$.items[0].tickers", hasItem("005930")));
    }

    @Test
    void testGetTopNewsWithTickerFilter() throws Exception {
        mockMvc.perform(get("/news/top")
                .param("n", "10")
                .param("tickers", "005930"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].title", containsString("삼성전자")));
    }

    @Test
    void testGetTopNewsWithMultipleTickerFilter() throws Exception {
        mockMvc.perform(get("/news/top")
                .param("n", "10")
                .param("tickers", "005930,035720"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)));
    }

    @Test
    void testGetNewsById() throws Exception {
        News news = newsRepository.findAll().get(0);
        
        mockMvc.perform(get("/news/" + news.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(news.getId().toString())))
                .andExpect(jsonPath("$.title", is(news.getTitle())))
                .andExpect(jsonPath("$.source", is(news.getSource())));
    }

    @Test
    void testGetNewsById_NotFound() throws Exception {
        mockMvc.perform(get("/news/99999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetTopNewsWithHoursFilter() throws Exception {
        mockMvc.perform(get("/news/top")
                .param("n", "10")
                .param("hours", "1"))  // Only last 1 hour
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)));  // Only the most recent news
    }
}