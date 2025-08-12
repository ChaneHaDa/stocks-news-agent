package com.newsagent.api.controller;

import com.newsagent.api.dto.NewsResponse;
import com.newsagent.api.model.NewsItem;
import com.newsagent.api.service.NewsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/news")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "News", description = "News API")
public class NewsController {

    private final NewsService newsService;

    @GetMapping("/top")
    @Operation(summary = "Get top news articles", description = "주요 뉴스 목록을 중요도순으로 반환합니다.")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved news articles")
    public NewsResponse getTopNews(
            @Parameter(description = "Number of articles to return", example = "20")
            @RequestParam(defaultValue = "20") int n,
            
            @Parameter(description = "Comma-separated list of stock tickers to filter", example = "005930,035720")
            @RequestParam(required = false) String tickers,
            
            @Parameter(description = "Language preference", example = "ko")
            @RequestParam(defaultValue = "ko") String lang,
            
            @Parameter(description = "Number of hours to look back for articles", example = "24")
            @RequestParam(defaultValue = "168") int hours) {

        log.debug("Getting top {} news articles, tickers: {}, lang: {}, hours: {}", n, tickers, lang, hours);

        // Parse ticker filters
        Set<String> tickerFilters = null;
        if (tickers != null && !tickers.trim().isEmpty()) {
            tickerFilters = Arrays.stream(tickers.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        }

        // Calculate since timestamp
        OffsetDateTime since = OffsetDateTime.now().minus(hours, ChronoUnit.HOURS);

        List<NewsItem> items = newsService.getTopNews(n, tickerFilters, lang, since);

        log.info("Returning {} news articles", items.size());
        return new NewsResponse(items, null);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get news article by ID", description = "특정 뉴스 기사의 상세 정보를 반환합니다.")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved news article")
    @ApiResponse(responseCode = "404", description = "News article not found")
    public ResponseEntity<NewsItem> getNewsById(
            @Parameter(description = "News article ID")
            @PathVariable Long id) {

        log.debug("Getting news article with ID: {}", id);

        Optional<NewsItem> newsItem = newsService.getNewsById(id);
        
        if (newsItem.isPresent()) {
            return ResponseEntity.ok(newsItem.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}