package com.newsagent.api.exception;

public class RssCollectionException extends RuntimeException {
    
    private final String sourceName;
    
    public RssCollectionException(String sourceName, String message) {
        super(String.format("RSS collection failed for source '%s': %s", sourceName, message));
        this.sourceName = sourceName;
    }
    
    public RssCollectionException(String sourceName, String message, Throwable cause) {
        super(String.format("RSS collection failed for source '%s': %s", sourceName, message), cause);
        this.sourceName = sourceName;
    }
    
    public String getSourceName() {
        return sourceName;
    }
}