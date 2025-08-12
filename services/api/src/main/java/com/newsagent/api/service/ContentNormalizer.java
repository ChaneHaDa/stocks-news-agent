package com.newsagent.api.service;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
public class ContentNormalizer {
    
    private static final int MAX_BODY_LENGTH = 5000;
    private static final int MAX_TITLE_LENGTH = 500;
    
    public String cleanHtml(String html) {
        if (html == null || html.trim().isEmpty()) {
            return "";
        }
        
        // Remove HTML tags and get clean text
        String cleanText = Jsoup.clean(html, Safelist.none());
        
        // Normalize whitespace
        cleanText = cleanText.replaceAll("\\s+", " ").trim();
        
        // Limit length
        if (cleanText.length() > MAX_BODY_LENGTH) {
            cleanText = cleanText.substring(0, MAX_BODY_LENGTH) + "...";
        }
        
        return cleanText;
    }
    
    public String normalizeTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            return "";
        }
        
        String normalized = title.trim()
            .replaceAll("\\s+", " ")
            .replaceAll("[\\r\\n\\t]", " ");
        
        if (normalized.length() > MAX_TITLE_LENGTH) {
            normalized = normalized.substring(0, MAX_TITLE_LENGTH) + "...";
        }
        
        return normalized;
    }
    
    public String generateDedupKey(String title, String source, OffsetDateTime publishedAt) {
        try {
            // Create deduplication key using title + source + date (without time)
            String dateStr = publishedAt != null ? 
                publishedAt.format(DateTimeFormatter.ISO_LOCAL_DATE) : 
                OffsetDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            
            String input = String.format("%s|%s|%s", 
                normalizeForDedup(title), 
                normalizeForDedup(source), 
                dateStr);
            
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.toString();
            
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-1 algorithm not available", e);
            // Fallback to a simple hash
            return String.valueOf(Math.abs((title + source).hashCode()));
        }
    }
    
    private String normalizeForDedup(String text) {
        if (text == null) return "";
        
        return text.toLowerCase()
            .replaceAll("[^\\p{L}\\p{N}]", "") // Keep only letters and numbers
            .trim();
    }
    
    public String extractBestContent(String description, String content) {
        // Prefer content over description, but use description if content is empty
        String bestContent = null;
        
        if (content != null && !content.trim().isEmpty()) {
            bestContent = content;
        } else if (description != null && !description.trim().isEmpty()) {
            bestContent = description;
        }
        
        return cleanHtml(bestContent);
    }
    
    public boolean isContentTooShort(String content) {
        return content == null || content.trim().length() < 50;
    }
    
    public boolean isContentSuspicious(String content) {
        if (content == null) return true;
        
        // Check for spam indicators
        String lowerContent = content.toLowerCase();
        
        // Too many repeated characters
        if (lowerContent.matches(".*(.{3,})\\1{3,}.*")) {
            return true;
        }
        
        // Too many special characters
        long specialChars = content.chars()
            .filter(ch -> !Character.isLetterOrDigit(ch) && !Character.isWhitespace(ch))
            .count();
        
        return specialChars > (content.length() * 0.3);
    }
}