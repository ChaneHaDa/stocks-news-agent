package com.newsagent.api.service;

import com.newsagent.api.config.RssProperties;
import com.newsagent.api.dto.RssItem;
import com.newsagent.api.exception.RssCollectionException;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RssClient {
    
    private final RssProperties rssProperties;
    
    @Retry(name = "rss-fetch")
    public List<RssItem> fetchRss(RssProperties.RssSource source) {
        if (!source.getEnabled()) {
            log.debug("RSS source {} is disabled, skipping", source.getName());
            return Collections.emptyList();
        }
        
        try {
            log.info("Fetching RSS from source: {} at URL: {}", source.getName(), source.getUrl());
            
            URL feedUrl = new URL(source.getUrl());
            SyndFeedInput input = new SyndFeedInput();
            SyndFeed feed = input.build(new XmlReader(feedUrl));
            
            List<RssItem> items = feed.getEntries().stream()
                .map(entry -> convertToRssItem(entry, source))
                .collect(Collectors.toList());
            
            log.info("Successfully fetched {} items from {}", items.size(), source.getName());
            return items;
            
        } catch (SocketTimeoutException e) {
            log.error("Timeout while fetching RSS from source: {} at URL: {}", source.getName(), source.getUrl());
            throw new RssCollectionException(source.getName(), "Connection timeout", e);
        } catch (ConnectException e) {
            log.error("Connection failed while fetching RSS from source: {} at URL: {}", source.getName(), source.getUrl());
            throw new RssCollectionException(source.getName(), "Connection failed", e);
        } catch (IOException e) {
            log.error("IO error while fetching RSS from source: {} at URL: {}", source.getName(), source.getUrl(), e);
            throw new RssCollectionException(source.getName(), "IO error: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error while fetching RSS from source: {} at URL: {}", source.getName(), source.getUrl(), e);
            throw new RssCollectionException(source.getName(), "Unexpected error: " + e.getMessage(), e);
        }
    }
    
    public List<RssItem> fetchAllSources() {
        List<RssProperties.RssSource> enabledSources = rssProperties.getSources().stream()
            .filter(RssProperties.RssSource::getEnabled)
            .collect(Collectors.toList());
        
        if (enabledSources.isEmpty()) {
            log.warn("No enabled RSS sources found");
            return Collections.emptyList();
        }
        
        log.info("Fetching from {} enabled RSS sources", enabledSources.size());
        
        return enabledSources.stream()
            .flatMap(source -> {
                try {
                    List<RssItem> items = fetchRss(source);
                    log.debug("Successfully fetched {} items from source: {}", items.size(), source.getName());
                    return items.stream();
                } catch (RssCollectionException e) {
                    log.warn("Failed to fetch from source '{}': {} - continuing with other sources", 
                        source.getName(), e.getMessage());
                    return java.util.stream.Stream.empty();
                } catch (Exception e) {
                    log.error("Unexpected error fetching from source '{}': {} - continuing with other sources", 
                        source.getName(), e.getMessage(), e);
                    return java.util.stream.Stream.empty();
                }
            })
            .collect(Collectors.toList());
    }
    
    private RssItem convertToRssItem(SyndEntry entry, RssProperties.RssSource source) {
        RssItem.RssItemBuilder builder = RssItem.builder()
            .source(source.getName())
            .title(cleanTitle(entry.getTitle()))
            .link(entry.getLink())
            .guid(entry.getUri());
        
        // Description/Content
        if (entry.getDescription() != null) {
            builder.description(entry.getDescription().getValue());
        }
        
        if (entry.getContents() != null && !entry.getContents().isEmpty()) {
            builder.content(entry.getContents().get(0).getValue());
        }
        
        // Published date
        if (entry.getPublishedDate() != null) {
            builder.publishedAt(OffsetDateTime.ofInstant(
                entry.getPublishedDate().toInstant(), 
                ZoneOffset.UTC
            ));
        } else if (entry.getUpdatedDate() != null) {
            builder.publishedAt(OffsetDateTime.ofInstant(
                entry.getUpdatedDate().toInstant(), 
                ZoneOffset.UTC
            ));
        } else {
            builder.publishedAt(OffsetDateTime.now(ZoneOffset.UTC));
        }
        
        return builder.build();
    }
    
    private String cleanTitle(String title) {
        if (title == null) return "";
        return title.trim()
            .replaceAll("\\s+", " ")
            .replaceAll("[\\r\\n\\t]", " ");
    }
}