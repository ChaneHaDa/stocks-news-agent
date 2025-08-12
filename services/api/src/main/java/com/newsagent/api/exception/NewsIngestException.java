package com.newsagent.api.exception;

public class NewsIngestException extends RuntimeException {
    
    public NewsIngestException(String message) {
        super(message);
    }
    
    public NewsIngestException(String message, Throwable cause) {
        super(message, cause);
    }
}