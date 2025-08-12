package com.newsagent.api.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class RssItem {
    private String source;
    private String title;
    private String link;
    private String description;
    private String content;
    private OffsetDateTime publishedAt;
    private String guid;
}