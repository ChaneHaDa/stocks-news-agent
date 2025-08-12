package com.newsagent.api.service;

import com.newsagent.api.config.ScoringProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class TickerMatcher {
    
    private final ScoringProperties scoringProperties;
    
    private Map<String, List<String>> tickerAliases = new HashMap<>();
    private Pattern tickerPattern;
    
    @PostConstruct
    public void initialize() {
        loadTickerAliases();
        tickerPattern = Pattern.compile(scoringProperties.getTickers().getPattern());
    }
    
    @SuppressWarnings("unchecked")
    private void loadTickerAliases() {
        try {
            String aliasesFile = scoringProperties.getTickers().getAliasesFile();
            if (aliasesFile.startsWith("classpath:")) {
                aliasesFile = aliasesFile.substring("classpath:".length());
            }
            
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream(aliasesFile);
            if (inputStream == null) {
                log.warn("Ticker aliases file not found: {}, using empty aliases", aliasesFile);
                return;
            }
            
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(inputStream);
            
            if (data != null && data.containsKey("tickers")) {
                Map<String, List<String>> tickers = (Map<String, List<String>>) data.get("tickers");
                this.tickerAliases = tickers;
                log.info("Loaded {} ticker aliases", tickers.size());
            }
            
        } catch (Exception e) {
            log.error("Failed to load ticker aliases", e);
        }
    }
    
    public Set<String> findTickers(String content) {
        Set<String> foundTickers = new HashSet<>();
        
        if (content == null || content.trim().isEmpty()) {
            return foundTickers;
        }
        
        // Find direct ticker matches (6-digit numbers)
        Matcher matcher = tickerPattern.matcher(content);
        while (matcher.find()) {
            String ticker = matcher.group();
            if (tickerAliases.containsKey(ticker)) {
                foundTickers.add(ticker);
            }
        }
        
        // Find alias matches
        String lowerContent = content.toLowerCase();
        for (Map.Entry<String, List<String>> entry : tickerAliases.entrySet()) {
            String ticker = entry.getKey();
            List<String> aliases = entry.getValue();
            
            for (String alias : aliases) {
                if (alias != null && lowerContent.contains(alias.toLowerCase())) {
                    foundTickers.add(ticker);
                    break; // Found one alias for this ticker, no need to check others
                }
            }
        }
        
        return foundTickers;
    }
    
    public double calculateTickerMatchStrength(String content) {
        Set<String> tickers = findTickers(content);
        
        if (tickers.isEmpty()) {
            return 0.0;
        }
        
        // Base score for having any ticker mention
        double score = 0.3;
        
        // Additional score based on number of different tickers
        if (tickers.size() == 1) {
            score += 0.5; // Single ticker focus is good
        } else if (tickers.size() <= 3) {
            score += 0.3; // Multiple tickers but still focused
        } else {
            score += 0.1; // Too many tickers might indicate general market news
        }
        
        // Bonus for major tickers (commonly traded ones)
        Set<String> majorTickers = Set.of("005930", "000660", "035720", "051910", "035420");
        long majorTickerCount = tickers.stream()
            .filter(majorTickers::contains)
            .count();
        
        if (majorTickerCount > 0) {
            score += Math.min(majorTickerCount * 0.3, 0.6);
        }
        
        return Math.min(score, 2.0); // Cap at 2.0
    }
    
    public List<String> getTickerNames(Set<String> tickers) {
        List<String> names = new ArrayList<>();
        
        for (String ticker : tickers) {
            List<String> aliases = tickerAliases.get(ticker);
            if (aliases != null && !aliases.isEmpty()) {
                names.add(aliases.get(0)); // Use the first alias as the primary name
            }
        }
        
        return names;
    }
}