package com.newsagent.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

public class NewsItem {
    private String id;
    private String source;
    private String title;
    private String url;
    
    @JsonProperty("published_at")
    private Instant publishedAt;
    
    private List<String> tickers;
    private String summary;
    private double importance;
    private ImportanceReason reason;

    public NewsItem() {}

    public NewsItem(String id, String source, String title, String url, Instant publishedAt, 
                   List<String> tickers, String summary, double importance, ImportanceReason reason) {
        this.id = id;
        this.source = source;
        this.title = title;
        this.url = url;
        this.publishedAt = publishedAt;
        this.tickers = tickers;
        this.summary = summary;
        this.importance = importance;
        this.reason = reason;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public List<String> getTickers() {
        return tickers;
    }

    public void setTickers(List<String> tickers) {
        this.tickers = tickers;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public double getImportance() {
        return importance;
    }

    public void setImportance(double importance) {
        this.importance = importance;
    }

    public ImportanceReason getReason() {
        return reason;
    }

    public void setReason(ImportanceReason reason) {
        this.reason = reason;
    }
}