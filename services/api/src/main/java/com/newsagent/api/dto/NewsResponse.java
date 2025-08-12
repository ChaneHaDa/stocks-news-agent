package com.newsagent.api.dto;

import com.newsagent.api.model.NewsItem;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class NewsResponse {
    private List<NewsItem> items;
    
    @JsonProperty("next_cursor")
    private String nextCursor;

    public NewsResponse() {}

    public NewsResponse(List<NewsItem> items, String nextCursor) {
        this.items = items;
        this.nextCursor = nextCursor;
    }

    public List<NewsItem> getItems() {
        return items;
    }

    public void setItems(List<NewsItem> items) {
        this.items = items;
    }

    public String getNextCursor() {
        return nextCursor;
    }

    public void setNextCursor(String nextCursor) {
        this.nextCursor = nextCursor;
    }
}