package com.newsagent.api.controller;

import com.newsagent.api.dto.NewsResponse;
import com.newsagent.api.model.ImportanceReason;
import com.newsagent.api.model.NewsItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/news")
@Tag(name = "News", description = "News API")
public class NewsController {

    @GetMapping("/top")
    @Operation(summary = "Get top news articles", description = "주요 뉴스 목록을 반환합니다.")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved news articles")
    public NewsResponse getTopNews(
            @Parameter(description = "Number of articles to return", example = "20")
            @RequestParam(defaultValue = "20") int n,
            
            @Parameter(description = "Comma-separated list of stock tickers to filter", example = "005930,035720")
            @RequestParam(required = false) String tickers,
            
            @Parameter(description = "Language preference", example = "ko")
            @RequestParam(defaultValue = "ko") String lang) {

        List<NewsItem> items = Arrays.asList(
            new NewsItem(
                "mock-1",
                "Yonhap", 
                "삼성전자 2분기 실적 요약",
                "https://example.com/a",
                Instant.now(),
                Arrays.asList("005930"),
                "목업 요약: 실적 발표 핵심만 두세 문장.",
                0.83,
                new ImportanceReason(1.0, 0.5, 0.6, 1.0)
            ),
            new NewsItem(
                "mock-2",
                "MK",
                "카카오 사업 업데이트", 
                "https://example.com/b",
                Instant.now(),
                Arrays.asList("035720"),
                "목업 요약: 서비스 개편과 비용 구조 이슈.",
                0.72,
                new ImportanceReason(0.9, 0.5, 0.3, 0.5)
            ),
            new NewsItem(
                "mock-3",
                "KED",
                "반도체 업계 동향 분석",
                "https://example.com/c", 
                Instant.now(),
                Arrays.asList("000660"),
                "목업 요약: 글로벌 수요 회복세와 메모리 가격 전망.",
                0.78,
                new ImportanceReason(0.8, 0.7, 0.8, 0.9)
            ),
            new NewsItem(
                "mock-4",
                "ET News",
                "네이버 클라우드 확장 계획",
                "https://example.com/d",
                Instant.now(), 
                Arrays.asList("035420"),
                "목업 요약: AI 서비스 강화와 해외 진출 로드맵.",
                0.65,
                new ImportanceReason(0.7, 0.4, 0.5, 0.8)
            ),
            new NewsItem(
                "mock-5",
                "Money Today",
                "LG에너지솔루션 배터리 신기술",
                "https://example.com/e",
                Instant.now(),
                Arrays.asList("373220"), 
                "목업 요약: 차세대 배터리 기술과 양산 일정 공개.",
                0.91,
                new ImportanceReason(0.6, 0.9, 0.7, 1.0)
            )
        );

        List<NewsItem> limitedItems = items.stream()
                .limit(n)
                .toList();

        return new NewsResponse(limitedItems, null);
    }
}