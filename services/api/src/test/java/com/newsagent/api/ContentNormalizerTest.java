package com.newsagent.api;

import com.newsagent.api.service.ContentNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class ContentNormalizerTest {

    private ContentNormalizer contentNormalizer;

    @BeforeEach
    void setUp() {
        contentNormalizer = new ContentNormalizer();
    }

    @Test
    void testCleanHtml() {
        String htmlContent = "<p>테스트 <b>뉴스</b> 내용입니다.</p><script>alert('xss');</script>";
        String cleaned = contentNormalizer.cleanHtml(htmlContent);
        
        assertEquals("테스트 뉴스 내용입니다.", cleaned);
        assertFalse(cleaned.contains("<"));
        assertFalse(cleaned.contains("script"));
    }

    @Test
    void testNormalizeTitle() {
        String title = "  테스트\n\t뉴스   제목  ";
        String normalized = contentNormalizer.normalizeTitle(title);
        
        assertEquals("테스트 뉴스 제목", normalized);
    }

    @Test
    void testGenerateDedupKey() {
        String title = "테스트 뉴스";
        String source = "테스트 소스";
        OffsetDateTime publishedAt = OffsetDateTime.parse("2023-12-01T10:00:00Z");
        
        String key1 = contentNormalizer.generateDedupKey(title, source, publishedAt);
        String key2 = contentNormalizer.generateDedupKey(title, source, publishedAt);
        
        assertEquals(key1, key2);
        assertNotNull(key1);
        assertTrue(key1.length() > 0);
    }

    @Test
    void testGenerateDedupKey_DifferentContent() {
        OffsetDateTime publishedAt = OffsetDateTime.parse("2023-12-01T10:00:00Z");
        
        String key1 = contentNormalizer.generateDedupKey("제목1", "소스", publishedAt);
        String key2 = contentNormalizer.generateDedupKey("제목2", "소스", publishedAt);
        
        assertNotEquals(key1, key2);
    }

    @Test
    void testExtractBestContent() {
        String description = "짧은 설명";
        String content = "<p>더 긴 내용입니다. 여기에 더 많은 정보가 있습니다.</p>";
        
        String best = contentNormalizer.extractBestContent(description, content);
        assertEquals("더 긴 내용입니다. 여기에 더 많은 정보가 있습니다.", best);
    }

    @Test
    void testExtractBestContent_FallbackToDescription() {
        String description = "설명 내용";
        String content = null;
        
        String best = contentNormalizer.extractBestContent(description, content);
        assertEquals("설명 내용", best);
    }

    @Test
    void testIsContentTooShort() {
        assertTrue(contentNormalizer.isContentTooShort("짧음"));
        assertTrue(contentNormalizer.isContentTooShort(null));
        assertFalse(contentNormalizer.isContentTooShort("이것은 충분히 긴 내용입니다. 최소 50자는 되어야 합니다. 더 많은 텍스트를 추가해보겠습니다."));
    }

    @Test
    void testIsContentSuspicious() {
        // Normal content
        assertFalse(contentNormalizer.isContentSuspicious("정상적인 뉴스 내용입니다. 삼성전자의 실적이 좋습니다."));
        
        // Repeated patterns
        assertTrue(contentNormalizer.isContentSuspicious("abc abc abc abc abc"));
        
        // Too many special characters
        assertTrue(contentNormalizer.isContentSuspicious("!!!@@@###$$$%%%^^^&&&***"));
        
        // Null content
        assertTrue(contentNormalizer.isContentSuspicious(null));
    }

    @Test
    void testCleanHtml_LongContent() {
        StringBuilder longContent = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longContent.append("긴 내용 ");
        }
        
        String cleaned = contentNormalizer.cleanHtml(longContent.toString());
        assertTrue(cleaned.length() <= 5003); // 5000 + "..."
        assertTrue(cleaned.endsWith("..."));
    }
}