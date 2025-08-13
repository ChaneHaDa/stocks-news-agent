"""Summarization service with real LLM integration."""

import time
import hashlib
import asyncio
import httpx
import json
import re
from typing import Dict, Any, List, Optional

from ..core.config import Settings
from ..core.logging import get_logger

logger = get_logger(__name__)


class SummarizeService:
    """Service for generating article summaries."""
    
    def __init__(self, settings: Settings):
        self.settings = settings
        self.is_loaded = True  # Simple text processing, always available
        self.model_version = "hybrid-v2.0.0"
        self.last_loaded = time.time()
        self.summary_count = 0
        
        # Simple cache for summaries (TTL will be handled by external cache in production)
        self._cache = {}
        
        # HTTP client for LLM API calls
        self._http_client = httpx.AsyncClient(timeout=30.0)
        
        # Initialize forbidden word lists
        self._forbidden_patterns = self._load_forbidden_patterns()
        
        logger.info("Enhanced SummarizeService initialized", model_version=self.model_version)
    
    def _load_forbidden_patterns(self) -> Dict[str, List[str]]:
        """Load forbidden word patterns for compliance checking."""
        return {
            "investment_advice": [
                r"매수\s*하세요", r"매도\s*하세요", r"투자\s*추천", r"추천\s*종목",
                r"반드시\s*사야", r"확실한\s*수익", r"100%\s*수익", r"무조건\s*오른다",
                r"목표\s*주가\s*\d+", r"적정\s*주가\s*\d+", r"주가\s*전망\s*\d+"
            ],
            "speculation": [
                r"대박\s*날", r"로또\s*주식", r"급등\s*확실", r"폭등\s*예상",
                r"몇\s*배\s*오를", r"\d+배\s*수익", r"한방에", r"일확천금",
                r"반드시\s*오른다", r"100%\s*확신"
            ],
            "price_prediction": [
                r"내일\s*주가", r"다음주\s*주가", r"며칠\s*후.*오를",
                r"정확한\s*예측", r"주가\s*예측\s*시스템", r"AI\s*예측.*주가"
            ],
            "gambling": [
                r"도박", r"베팅", r"한탕", r"올인", r"묻지마\s*투자",
                r"운에\s*맡기", r"대박\s*터질"
            ]
        }
    
    def _get_llm_prompt(self, title: str, body: str, tickers: List[str]) -> str:
        """Generate structured prompt for LLM summarization."""
        ticker_info = f" (관련 종목: {', '.join(tickers)})" if tickers else ""
        
        return f"""다음 뉴스 기사를 3-5줄로 요약하고, 주요 포인트 2개를 bullet으로 정리해주세요.

제목: {title}
내용: {body}{ticker_info}

요약 규칙:
1. 객관적 사실만 포함하고 추측성 표현 금지
2. 투자 조언이나 추천 표현 절대 금지 
3. 주가 예측이나 목표가 언급 금지
4. 감정적 수식어(대박, 폭등 등) 사용 금지
5. 마지막에 "이 요약은 투자 자문이 아닙니다" 추가

형식:
요약: [3-5줄 요약]

주요 포인트:
• [핵심 포인트 1]
• [핵심 포인트 2]

이 요약은 투자 자문이 아닙니다."""
    
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
            
            if style == "llm":
                # Try LLM summarization (with OpenAI API or enhanced fallback)
                summary = await self._llm_summarize(title, body, max_length, tickers)
                method = "llm"
            else:
                # Enhanced extractive summarization
                summary = await self._enhanced_extractive_summary(title, body, max_length, tickers)
                method = "extractive"
            
            # Policy compliance check BEFORE finalizing
            policy_flags = self._check_policy_compliance(summary)
            
            # Sanitize summary if violations detected
            if "clean" not in policy_flags:
                logger.warning(f"Policy violations detected for article {id}: {policy_flags}")
                summary = self._sanitize_summary(summary, policy_flags)
                
                # Re-check after sanitization
                policy_flags = self._check_policy_compliance(summary)
                
                # If still not clean, fall back to extractive
                if "clean" not in policy_flags:
                    logger.error(f"Failed to sanitize summary for article {id}, using safe fallback")
                    summary = self._safe_fallback_summary(title, body)
                    policy_flags = ["clean"]
                    method = "safe_fallback"
            
            # Extract key reasons
            reasons = self._extract_reasons(title, body, tickers)
            
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
    
    async def _llm_summarize(self, title: str, body: str, max_length: int, tickers: List[str] = []) -> str:
        """Generate LLM summary with real API call or enhanced fallback."""
        
        # Try real LLM API first (if configured)
        if hasattr(self.settings, 'openai_api_key') and self.settings.openai_api_key:
            try:
                return await self._call_openai_api(title, body, tickers)
            except Exception as e:
                logger.warning("OpenAI API call failed, using enhanced fallback", exc_info=e)
        
        # Enhanced extractive fallback
        return await self._enhanced_extractive_summary(title, body, max_length, tickers)
    
    async def _call_openai_api(self, title: str, body: str, tickers: List[str]) -> str:
        """Call OpenAI API for summarization."""
        prompt = self._get_llm_prompt(title, body, tickers)
        
        payload = {
            "model": "gpt-3.5-turbo",
            "messages": [
                {"role": "system", "content": "당신은 금융 뉴스 요약 전문가입니다. 객관적이고 정확한 요약을 제공하며, 투자 조언은 절대 하지 않습니다."},
                {"role": "user", "content": prompt}
            ],
            "max_tokens": 300,
            "temperature": 0.3
        }
        
        headers = {
            "Authorization": f"Bearer {self.settings.openai_api_key}",
            "Content-Type": "application/json"
        }
        
        response = await self._http_client.post(
            "https://api.openai.com/v1/chat/completions",
            json=payload,
            headers=headers
        )
        
        if response.status_code != 200:
            raise Exception(f"OpenAI API error: {response.status_code} - {response.text}")
        
        result = response.json()
        content = result["choices"][0]["message"]["content"].strip()
        
        # Validate LLM response format
        if not self._validate_llm_response_format(content):
            logger.warning("LLM response format invalid, using fallback")
            raise Exception("Invalid LLM response format")
        
        return content
    
    async def _enhanced_extractive_summary(self, title: str, body: str, max_length: int, tickers: List[str]) -> str:
        """Enhanced extractive summarization with structured format."""
        # Add small delay to simulate processing
        await asyncio.sleep(0.1)
        
        # Extract key sentences
        sentences = self._extract_key_sentences(body, num_sentences=3)
        
        # Build structured summary
        summary_text = f"요약: {title}. " + " ".join(sentences)
        
        # Add key points based on content analysis
        key_points = self._extract_key_points(title, body, tickers)
        
        summary_parts = [summary_text]
        if key_points:
            summary_parts.append("\n주요 포인트:")
            for point in key_points[:2]:  # Limit to 2 points
                summary_parts.append(f"• {point}")
        
        summary_parts.append("\n이 요약은 투자 자문이 아닙니다.")
        
        full_summary = "\n".join(summary_parts)
        
        # Truncate if too long
        if len(full_summary) > max_length:
            # Try to keep the essential parts
            base_summary = summary_text
            if len(base_summary) + 20 <= max_length:  # 20 for disclaimer
                full_summary = base_summary + "\n\n이 요약은 투자 자문이 아닙니다."
            else:
                full_summary = base_summary[:max_length-23] + "...\n\n이 요약은 투자 자문이 아닙니다."
        
        return full_summary
    
    def _extract_key_sentences(self, body: str, num_sentences: int = 3) -> List[str]:
        """Extract key sentences using simple heuristics."""
        sentences = [s.strip() for s in body.split('.') if s.strip()]
        
        if len(sentences) <= num_sentences:
            return sentences
        
        # Score sentences based on financial keywords
        financial_keywords = ["실적", "매출", "영업이익", "주가", "투자", "발표", "증가", "감소", "성장", "수익"]
        
        scored_sentences = []
        for sentence in sentences:
            score = sum(1 for keyword in financial_keywords if keyword in sentence)
            scored_sentences.append((score, sentence))
        
        # Sort by score and take top sentences
        scored_sentences.sort(key=lambda x: x[0], reverse=True)
        return [sent for _, sent in scored_sentences[:num_sentences]]
    
    def _extract_key_points(self, title: str, body: str, tickers: List[str]) -> List[str]:
        """Extract key points for bullet format."""
        points = []
        
        # Company/ticker specific points
        if tickers:
            company_mentions = []
            for ticker in tickers[:2]:  # Limit to first 2 tickers
                # This would need a ticker->company name mapping in production
                company_mentions.append(f"종목 {ticker} 관련")
            if company_mentions:
                points.append(", ".join(company_mentions))
        
        # Financial metrics mentioned
        financial_patterns = {
            r"매출.*?(\d+[%억만원]+)": "매출 관련 수치",
            r"영업이익.*?(\d+[%억만원]+)": "영업이익 관련 수치", 
            r"순이익.*?(\d+[%억만원]+)": "순이익 관련 수치",
            r"주가.*?(\d+[%원]+)": "주가 변동"
        }
        
        for pattern, description in financial_patterns.items():
            if re.search(pattern, body):
                points.append(description)
                if len(points) >= 2:  # Limit to 2 points
                    break
        
        # If no specific points found, use generic ones
        if not points:
            if "발표" in title or "발표" in body:
                points.append("주요 발표 내용")
            if any(word in body for word in ["성장", "증가", "상승"]):
                points.append("긍정적 지표")
            elif any(word in body for word in ["감소", "하락", "부진"]):
                points.append("주의 지표")
        
        return points[:2]  # Return maximum 2 points
    
    def _validate_llm_response_format(self, content: str) -> bool:
        """Validate that LLM response follows expected format."""
        required_elements = [
            "요약:",
            "주요 포인트:",
            "투자 자문이 아닙니다"
        ]
        
        return all(element in content for element in required_elements)
    
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
        """Enhanced policy compliance checking with regex patterns."""
        flags = []
        
        # Check each category of forbidden patterns
        for category, patterns in self._forbidden_patterns.items():
            for pattern in patterns:
                if re.search(pattern, summary, re.IGNORECASE):
                    flags.append(category)
                    logger.warning(f"Policy violation detected: {category} - pattern: {pattern}")
                    break  # One violation per category is enough
        
        # Additional basic word checks for backwards compatibility
        basic_checks = {
            "investment_advice": ["추천", "매수", "매도", "투자하세요"],
            "speculation": ["급등", "폭락", "대박", "확실"],
            "price_prediction": ["내일", "다음주", "예측"]
        }
        
        for category, words in basic_checks.items():
            if category not in flags:  # Don't double-flag
                if any(word in summary for word in words):
                    flags.append(category)
        
        if not flags:
            flags.append("clean")
        
        return flags
    
    def _sanitize_summary(self, summary: str, policy_flags: List[str]) -> str:
        """Sanitize summary if policy violations are detected."""
        if "clean" in policy_flags:
            return summary
        
        sanitized = summary
        
        # Remove problematic phrases
        for category, patterns in self._forbidden_patterns.items():
            if category in policy_flags:
                for pattern in patterns:
                    sanitized = re.sub(pattern, "[편집됨]", sanitized, flags=re.IGNORECASE)
        
        # Ensure disclaimer is present
        if "투자 자문이 아닙니다" not in sanitized:
            sanitized += "\n\n이 요약은 투자 자문이 아니며, 정보 제공 목적입니다."
        
        return sanitized
    
    def _safe_fallback_summary(self, title: str, body: str) -> str:
        """Generate completely safe fallback summary with minimal content."""
        # Very conservative approach - just basic facts
        summary_parts = [
            f"요약: {title}에 대한 뉴스가 발표되었습니다.",
            "\n주요 포인트:",
            "• 관련 뉴스 발표",
            "• 세부 내용은 원문 참조",
            "\n이 요약은 투자 자문이 아닙니다."
        ]
        
        return "\n".join(summary_parts)
    
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
        
        # Close HTTP client
        if hasattr(self, '_http_client'):
            await self._http_client.aclose()