"""Summarization service (mock implementation)."""

import time
import hashlib
import asyncio
from typing import Dict, Any, List, Optional

from ..core.config import Settings
from ..core.logging import get_logger

logger = get_logger(__name__)


class SummarizeService:
    """Service for generating article summaries."""
    
    def __init__(self, settings: Settings):
        self.settings = settings
        self.is_loaded = True  # Simple text processing, always available
        self.model_version = "extractive-v1.0.0"
        self.last_loaded = time.time()
        self.summary_count = 0
        
        # Simple cache for summaries
        self._cache = {}
        
        logger.info("SummarizeService initialized")
    
    async def summarize(
        self, 
        id: str, 
        title: str, 
        body: str, 
        tickers: List[str], 
        options: Dict[str, Any]
    ) -> Dict[str, Any]:
        """Generate summary for an article."""
        
        # Check cache first
        cache_key = self._get_cache_key(title, body)
        if cache_key in self._cache:
            cached_result = self._cache[cache_key]
            cached_result["method"] = "cached"
            return cached_result
        
        start_time = time.time()
        
        try:
            # Determine method
            style = options.get("style", "extractive")
            max_length = options.get("max_length", 240)
            
            if style == "llm" and self.settings.llm_api_url:
                # Mock LLM call (in real implementation, would call external API)
                summary = await self._llm_summarize(title, body, max_length)
                method = "llm"
            else:
                # Extractive summarization
                summary = self._extractive_summarize(title, body, max_length)
                method = "extractive"
            
            # Extract key reasons
            reasons = self._extract_reasons(title, body, tickers)
            
            # Policy compliance check
            policy_flags = self._check_policy_compliance(summary)
            
            # Add investment disclaimer if needed
            if any(flag in ["investment_advice", "speculation"] for flag in policy_flags):
                summary += f" {self.settings.investment_disclaimer}"
            
            result = {
                "summary": summary,
                "reasons": reasons,
                "policy_flags": policy_flags,
                "model_version": self.model_version,
                "method": method
            }
            
            # Cache the result
            self._cache[cache_key] = result.copy()
            
            self.summary_count += 1
            
            duration = time.time() - start_time
            logger.info(
                "Summary generated",
                article_id=id,
                method=method,
                duration=duration,
                summary_length=len(summary)
            )
            
            return result
            
        except Exception as e:
            logger.error("Summarization failed", exc_info=e, article_id=id)
            
            # Fallback to simple extractive
            fallback_summary = self._extractive_summarize(title, body, 200)
            return {
                "summary": fallback_summary,
                "reasons": [],
                "policy_flags": ["clean"],
                "model_version": self.model_version,
                "method": "extractive_fallback"
            }
    
    def _extractive_summarize(self, title: str, body: str, max_length: int) -> str:
        """Generate extractive summary."""
        # Simple extractive approach: take first few sentences
        sentences = body.split('. ')
        
        summary = title + ". "
        current_length = len(summary)
        
        for sentence in sentences:
            sentence = sentence.strip()
            if not sentence:
                continue
                
            # Add sentence if it fits
            if current_length + len(sentence) + 2 <= max_length:
                summary += sentence + ". "
                current_length = len(summary)
            else:
                break
        
        return summary.strip()
    
    async def _llm_summarize(self, title: str, body: str, max_length: int) -> str:
        """Generate LLM summary (mock implementation)."""
        # In real implementation, this would call external LLM API
        await asyncio.sleep(0.5)  # Mock API call delay
        
        # For now, return enhanced extractive summary
        sentences = body.split('. ')[:3]
        summary = f"{title}의 주요 내용: " + ". ".join(sentences)
        
        if len(summary) > max_length:
            summary = summary[:max_length-3] + "..."
        
        return summary
    
    def _extract_reasons(self, title: str, body: str, tickers: List[str]) -> List[str]:
        """Extract key reasons/points from the article."""
        reasons = []
        
        # Look for ticker mentions
        if tickers:
            reasons.append(f"관련 종목: {', '.join(tickers[:3])}")
        
        # Look for key financial terms
        financial_terms = ["주가", "매출", "영업이익", "실적", "투자", "인수합병"]
        found_terms = [term for term in financial_terms if term in body]
        
        if found_terms:
            reasons.append(f"주요 키워드: {', '.join(found_terms[:3])}")
        
        # Check for urgency indicators
        urgency_words = ["긴급", "속보", "발표", "공시"]
        if any(word in title for word in urgency_words):
            reasons.append("긴급 뉴스")
        
        return reasons[:3]  # Limit to 3 reasons
    
    def _check_policy_compliance(self, summary: str) -> List[str]:
        """Check policy compliance of the summary."""
        flags = []
        
        # Check for investment advice indicators
        advice_words = ["추천", "매수", "매도", "투자하세요", "수익", "전망"]
        if any(word in summary for word in advice_words):
            flags.append("investment_advice")
        
        # Check for speculation
        speculation_words = ["급등", "폭락", "대박", "확실", "반드시"]
        if any(word in summary for word in speculation_words):
            flags.append("speculation")
        
        if not flags:
            flags.append("clean")
        
        return flags
    
    def _get_cache_key(self, title: str, body: str) -> str:
        """Generate cache key for article."""
        content = f"{title}|{body}"
        return hashlib.md5(content.encode()).hexdigest()
    
    async def reload(self, version: Optional[str] = None):
        """Reload the service."""
        if version:
            self.model_version = version
        
        # Clear cache on reload
        self._cache.clear()
        self.last_loaded = time.time()
        
        logger.info("SummarizeService reloaded", version=version)
    
    async def cleanup(self):
        """Cleanup resources."""
        logger.info("Cleaning up SummarizeService")
        self._cache.clear()