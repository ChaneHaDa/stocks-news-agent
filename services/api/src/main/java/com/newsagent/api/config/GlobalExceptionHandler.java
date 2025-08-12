package com.newsagent.api.config;

import com.newsagent.api.exception.NewsIngestException;
import com.newsagent.api.exception.RssCollectionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.OffsetDateTime;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(
            Exception ex, WebRequest request) {
        
        log.error("Unexpected error occurred", ex);
        
        Map<String, Object> errorResponse = Map.of(
            "timestamp", OffsetDateTime.now(),
            "error", "Internal Server Error",
            "message", "An unexpected error occurred",
            "path", request.getDescription(false).replace("uri=", "")
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
    
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(
            IllegalArgumentException ex, WebRequest request) {
        
        log.warn("Invalid request parameter: {}", ex.getMessage());
        
        Map<String, Object> errorResponse = Map.of(
            "timestamp", OffsetDateTime.now(),
            "error", "Bad Request",
            "message", ex.getMessage(),
            "path", request.getDescription(false).replace("uri=", "")
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }
    
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(
            RuntimeException ex, WebRequest request) {
        
        log.error("Runtime error occurred: {}", ex.getMessage(), ex);
        
        Map<String, Object> errorResponse = Map.of(
            "timestamp", OffsetDateTime.now(),
            "error", "Service Error",
            "message", "A service error occurred: " + ex.getMessage(),
            "path", request.getDescription(false).replace("uri=", "")
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
    
    @ExceptionHandler(RssCollectionException.class)
    public ResponseEntity<Map<String, Object>> handleRssCollectionException(
            RssCollectionException ex, WebRequest request) {
        
        log.error("RSS collection error: {}", ex.getMessage());
        
        Map<String, Object> errorResponse = Map.of(
            "timestamp", OffsetDateTime.now(),
            "error", "RSS Collection Error",
            "message", ex.getMessage(),
            "source", ex.getSourceName(),
            "path", request.getDescription(false).replace("uri=", "")
        );
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
    }
    
    @ExceptionHandler(NewsIngestException.class)
    public ResponseEntity<Map<String, Object>> handleNewsIngestException(
            NewsIngestException ex, WebRequest request) {
        
        log.error("News ingestion error: {}", ex.getMessage());
        
        Map<String, Object> errorResponse = Map.of(
            "timestamp", OffsetDateTime.now(),
            "error", "News Ingestion Error",
            "message", ex.getMessage(),
            "path", request.getDescription(false).replace("uri=", "")
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}