package com.newsagent.api.controller;

import com.newsagent.api.dto.NewsResponse;
import com.newsagent.api.model.NewsItem;
import com.newsagent.api.service.NewsService;
import com.newsagent.api.service.PersonalizationService;
import com.newsagent.api.service.ExperimentalNewsService;
import com.newsagent.api.service.AnonymousUserService;
import com.newsagent.api.entity.AnonymousUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
    private final PersonalizationService personalizationService;
    private final ExperimentalNewsService experimentalNewsService;
    private final AnonymousUserService anonymousUserService;

    @GetMapping("/top")
    @Operation(summary = "Get top news articles", description = "주요 뉴스 목록을 중요도순 또는 최신순으로 반환합니다. MMR 다양성 필터링 포함.")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved news articles")
    public NewsResponse getTopNews(
            @Parameter(description = "Number of articles to return (max 100)", example = "20")
            @RequestParam(defaultValue = "20") int n,
            
            @Parameter(description = "Comma-separated list of stock tickers to filter", example = "005930,035720")
            @RequestParam(required = false) String tickers,
            
            @Parameter(description = "Language preference", example = "ko")
            @RequestParam(defaultValue = "ko") String lang,
            
            @Parameter(description = "Number of hours to look back for articles", example = "24")
            @RequestParam(defaultValue = "168") int hours,
            
            @Parameter(description = "Sort order: 'rank' for importance ranking, 'time' for latest first", example = "rank")
            @RequestParam(defaultValue = "rank") String sort,
            
            @Parameter(description = "Apply diversity filtering to reduce duplicate topics", example = "true")
            @RequestParam(defaultValue = "true") boolean diversity,
            
            @Parameter(description = "Enable personalized ranking (requires user ID)", example = "false")
            @RequestParam(defaultValue = "false") boolean personalized,
            
            @Parameter(description = "User ID for personalization (optional)", example = "user123")
            @RequestParam(required = false) String userId) {

        log.debug("Getting top {} news articles, tickers: {}, lang: {}, hours: {}, sort: {}, diversity: {}, personalized: {}, userId: {}", 
                  n, tickers, lang, hours, sort, diversity, personalized, userId);

        // Validate parameters
        if (n > 100) {
            n = 100; // Cap at 100 items
        }
        if (n < 1) {
            n = 1;
        }
        
        if (!sort.equals("rank") && !sort.equals("time")) {
            sort = "rank"; // Default to rank
        }

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

        List<NewsItem> items = newsService.getTopNews(n, tickerFilters, lang, since, sort, diversity, personalized, userId);

        log.info("Returning {} news articles (sort: {}, diversity: {}, personalized: {})", items.size(), sort, diversity, personalized);
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
    
    @PostMapping("/{id}/click")
    @Operation(summary = "Record news article click", description = "뉴스 기사 클릭을 기록하여 개인화에 활용합니다.")
    @ApiResponse(responseCode = "200", description = "Click recorded successfully")
    public ResponseEntity<Void> recordClick(
            @Parameter(description = "News article ID")
            @PathVariable Long id,
            
            @Parameter(description = "User ID")
            @RequestParam String userId,
            
            @Parameter(description = "Session ID (optional)")
            @RequestParam(required = false) String sessionId,
            
            @Parameter(description = "Rank position when clicked (optional)")
            @RequestParam(required = false) Integer position,
            
            @Parameter(description = "Importance score when clicked (optional)")
            @RequestParam(required = false) Double importance,
            
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            @RequestHeader(value = "X-Forwarded-For", required = false) String xForwardedFor,
            @RequestHeader(value = "X-Real-IP", required = false) String xRealIP) {
        
        // Get client IP address
        String ipAddress = getClientIpAddress(xForwardedFor, xRealIP);
        
        log.debug("Recording click for user {} on news {} at position {}", userId, id, position);
        
        personalizationService.recordClick(userId, id, sessionId, userAgent, ipAddress, position, importance);
        
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/top/experimental")
    @Operation(
        summary = "Get top news with A/B testing", 
        description = "A/B 테스트가 통합된 뉴스 목록 API. 익명 사용자 ID 기반으로 실험 그룹에 배정하여 개인화 vs 일반 랭킹을 테스트합니다."
    )
    @ApiResponse(responseCode = "200", description = "Successfully retrieved experimental news articles")
    public ExperimentalNewsResponse getTopNewsExperimental(
            @Parameter(description = "Anonymous user ID (from cookie/localStorage)", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
            @RequestHeader(value = "X-Anonymous-ID", required = false) String anonId,
            
            @Parameter(description = "Number of articles to return (max 100)", example = "20")
            @RequestParam(defaultValue = "20") int n,
            
            @Parameter(description = "Comma-separated list of stock tickers to filter", example = "005930,035720")
            @RequestParam(required = false) String tickers,
            
            @Parameter(description = "Language preference", example = "ko")
            @RequestParam(defaultValue = "ko") String lang,
            
            @Parameter(description = "Number of hours to look back for articles", example = "24")
            @RequestParam(defaultValue = "168") int hours,
            
            @Parameter(description = "Sort order: 'rank' for importance ranking, 'time' for latest first", example = "rank")
            @RequestParam(defaultValue = "rank") String sort,
            
            @Parameter(description = "Apply MMR diversity filtering", example = "true")
            @RequestParam(defaultValue = "true") boolean diversity,
            
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        try {
            // Ensure user has anonymous ID  
            if (anonId == null || anonId.trim().isEmpty()) {
                AnonymousUser newUser = anonymousUserService.getOrCreateAnonymousUser(request, response);
                anonId = newUser.getAnonId();
                log.debug("Generated new anonymous ID: {}", anonId);
            } else {
                // Update activity for existing user
                anonymousUserService.findByAnonId(anonId).ifPresent(user -> {
                    user.recordActivity();
                    // Note: We don't save here as it's readonly context
                });
            }
            
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
            
            // Get experimental news response
            ExperimentalNewsService.ExperimentalNewsResponse experimentalResponse = 
                experimentalNewsService.getTopNewsWithExperiment(
                    anonId, n, tickerFilters, lang, since, sort, diversity);
            
            log.info("Returning {} news articles for user {} in experiment {} variant {} (personalized: {})", 
                experimentalResponse.getTotalResults(), anonId, experimentalResponse.getExperimentKey(), 
                experimentalResponse.getVariant(), experimentalResponse.isPersonalized());
            
            return new ExperimentalNewsResponse(
                experimentalResponse.getNewsItems(),
                experimentalResponse.getExperimentKey(),
                experimentalResponse.getVariant(),
                experimentalResponse.isPersonalized(),
                experimentalResponse.isDiversityApplied(),
                anonId,
                null
            );
            
        } catch (Exception e) {
            log.error("Error in experimental news endpoint", e);
            
            // Fallback to standard news service
            Set<String> tickerFilters = null;
            if (tickers != null && !tickers.trim().isEmpty()) {
                tickerFilters = Arrays.stream(tickers.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());
            }
            
            OffsetDateTime since = OffsetDateTime.now().minus(hours, ChronoUnit.HOURS);
            List<NewsItem> fallbackItems = newsService.getTopNews(n, tickerFilters, lang, since, sort, diversity);
            
            return new ExperimentalNewsResponse(
                fallbackItems,
                "ranking_ab",
                "control",
                false,
                diversity,
                anonId,
                null
            );
        }
    }
    
    private String getClientIpAddress(String xForwardedFor, String xRealIP) {
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs, take the first one
            return xForwardedFor.split(",")[0].trim();
        }
        if (xRealIP != null && !xRealIP.isEmpty()) {
            return xRealIP;
        }
        return "unknown";
    }
    
    /**
     * Experimental news response with A/B test metadata
     */
    public static class ExperimentalNewsResponse extends NewsResponse {
        private String experimentKey;
        private String variant;
        private boolean isPersonalized;
        private boolean diversityApplied;
        private String anonId;
        
        public ExperimentalNewsResponse(
                List<NewsItem> items,
                String experimentKey,
                String variant,
                boolean isPersonalized,
                boolean diversityApplied,
                String anonId,
                String nextCursor
        ) {
            super(items, nextCursor);
            this.experimentKey = experimentKey;
            this.variant = variant;
            this.isPersonalized = isPersonalized;
            this.diversityApplied = diversityApplied;
            this.anonId = anonId;
        }
        
        // Getters
        public String getExperimentKey() { return experimentKey; }
        public String getVariant() { return variant; }
        public boolean isPersonalized() { return isPersonalized; }
        public boolean isDiversityApplied() { return diversityApplied; }
        public String getAnonId() { return anonId; }
    }
}