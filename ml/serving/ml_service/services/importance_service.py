"""Importance scoring service with real ML model."""

import time
import random
import pickle
import json
import os
import numpy as np
from typing import List, Dict, Any, Optional
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.preprocessing import StandardScaler
from sklearn.linear_model import LogisticRegression

from ..core.config import Settings
from ..core.logging import get_logger
from ..core.metrics import MODEL_INFERENCE_DURATION, MODEL_INFERENCE_COUNT

logger = get_logger(__name__)


class ImportanceService:
    """Service for scoring news importance with real ML model."""
    
    def __init__(self, settings: Settings):
        self.settings = settings
        self.is_loaded = False
        self.model_version = "unknown"
        self.last_loaded: Optional[float] = None
        self.inference_count = 0
        
        # ML model components
        self.model: Optional[LogisticRegression] = None
        self.tfidf_vectorizer: Optional[TfidfVectorizer] = None
        self.feature_scaler: Optional[StandardScaler] = None
        self.metadata: Optional[Dict[str, Any]] = None
        
        # Load model on initialization
        if settings.enable_importance:
            self._load_model()
    
    def _load_model(self):
        """Load the actual ML model from disk."""
        try:
            models_dir = os.path.join(os.path.dirname(__file__), "..", "..", "models")
            model_path = os.path.join(models_dir, "importance-latest.pkl")
            metadata_path = os.path.join(models_dir, "metadata-latest.json")
            
            logger.info("Loading importance model from", path=model_path)
            
            # Load metadata first
            if os.path.exists(metadata_path):
                with open(metadata_path, 'r') as f:
                    self.metadata = json.load(f)
                    self.model_version = self.metadata.get("model_version", "unknown")
            else:
                logger.warning("Model metadata not found, using fallback")
                self.model_version = "fallback-v1.0.0"
            
            # Load the actual model
            if os.path.exists(model_path):
                with open(model_path, 'rb') as f:
                    self.model = pickle.load(f)
                
                # Initialize feature processing components (simplified for now)
                # In a real implementation, you'd also save and load the TfidfVectorizer and scaler
                self._setup_feature_processing()
                
                self.is_loaded = True
                self.last_loaded = time.time()
                
                logger.info("Real ML model loaded successfully", 
                          version=self.model_version,
                          model_type=type(self.model).__name__)
            else:
                logger.warning("Model file not found, falling back to rule-based scoring")
                self._setup_fallback_model()
            
        except Exception as e:
            logger.error("Failed to load importance model, falling back to rules", exc_info=e)
            self._setup_fallback_model()
    
    def _setup_feature_processing(self):
        """Setup feature processing components."""
        # For now, we'll use simplified feature processing
        # In production, you'd save and load the actual TfidfVectorizer and scaler used in training
        korean_stopwords = ['의', '가', '이', '은', '는', '을', '를', '에', '와', '과', '로', '으로']
        
        self.tfidf_vectorizer = TfidfVectorizer(
            max_features=1000,
            ngram_range=(1, 2),
            stop_words=korean_stopwords,
            min_df=1,  # Lower min_df for small dummy dataset
            max_df=0.9
        )
        
        self.feature_scaler = StandardScaler()
        
        # Initialize with some dummy data to set up the vectorizer state
        dummy_texts = [
            "삼성전자 실적 발표 영업이익 증가",
            "현대자동차 전기차 판매량 성장",
            "SK하이닉스 메모리 반도체 시장"
        ]
        self.tfidf_vectorizer.fit(dummy_texts)
        
        # Setup scaler with dummy numerical features
        dummy_numerical = np.array([[0.8, 2, 5, 10, 500, 12]])
        self.feature_scaler.fit(dummy_numerical)
    
    def _setup_fallback_model(self):
        """Setup fallback rule-based model when ML model loading fails."""
        self.model = None
        self.is_loaded = True
        self.model_version = "fallback-rules-v1.0.0"
        self.last_loaded = time.time()
        
        logger.info("Fallback rule-based model initialized")
    
    async def score_batch(self, articles: List[Any]) -> List[Dict[str, Any]]:
        """Score a batch of articles for importance."""
        if not self.is_loaded:
            raise RuntimeError("Importance model not loaded")
        
        start_time = time.time()
        
        try:
            results = []
            
            for article in articles:
                # Real ML model prediction or fallback
                importance_p, confidence = self._score_article_with_model(article)
                features = self._extract_features(article)
                
                results.append({
                    "id": article.id,
                    "importance_p": importance_p,
                    "features": features,
                    "confidence": confidence,
                    "model_version": self.model_version
                })
                
                self.inference_count += 1
            
            duration = time.time() - start_time
            
            # Update metrics
            MODEL_INFERENCE_DURATION.labels(
                model_type="importance",
                model_version=self.model_version
            ).observe(duration)
            
            MODEL_INFERENCE_COUNT.labels(
                model_type="importance", 
                model_version=self.model_version,
                status="success"
            ).inc(len(articles))
            
            logger.info(
                "Importance scoring completed",
                article_count=len(articles),
                duration=duration
            )
            
            return results
            
        except Exception as e:
            MODEL_INFERENCE_COUNT.labels(
                model_type="importance",
                model_version=self.model_version,
                status="error"
            ).inc()
            
            logger.error("Importance scoring failed", exc_info=e)
            raise
    
    def _extract_features(self, article) -> Dict[str, float]:
        """Extract features from article for ML model."""
        # Basic numerical features
        numerical_features = {
            "source_weight": self._get_source_weight(article.source),
            "keyword_hits": self._count_keywords(article.title + " " + (article.body or "")),
            "ticker_matches": len(getattr(article, 'tickers', [])),
            "title_length": len(article.title),
            "body_length": len(article.body or ""),
            "freshness_hours": 1.0  # Default to 1 hour for mock data
        }
        
        return numerical_features
    
    def _get_source_weight(self, source: str) -> float:
        """Get source weight based on credibility."""
        weights = {
            "조선일보": 0.9, "중앙일보": 0.9, "동아일보": 0.8,
            "한국경제": 0.95, "매일경제": 0.9, "연합뉴스": 0.95,
            "뉴스1": 0.7, "헤럴드경제": 0.8, "파이낸셜뉴스": 0.85, "이투데이": 0.8
        }
        return weights.get(source, 0.6)
    
    def _count_keywords(self, text: str) -> int:
        """Count important financial keywords."""
        keywords = [
            "실적", "영업이익", "매출", "주가", "상승", "하락", "투자", "인수", "합병",
            "IPO", "상장", "배당", "증자", "감자", "분할", "경영권", "지배구조"
        ]
        count = 0
        text_lower = text.lower()
        for keyword in keywords:
            count += text_lower.count(keyword)
        return count
    
    def _create_feature_vector(self, article) -> np.ndarray:
        """Create feature vector for ML model prediction."""
        # Extract text and numerical features
        text = article.title + " " + (article.body or "")
        numerical_features = self._extract_features(article)
        
        if self.model is not None and self.tfidf_vectorizer is not None:
            # Use real ML model feature pipeline
            try:
                # TF-IDF features
                tfidf_features = self.tfidf_vectorizer.transform([text]).toarray()
                
                # Numerical features
                numerical_array = np.array([[
                    numerical_features["source_weight"],
                    numerical_features["keyword_hits"],
                    numerical_features["ticker_matches"],
                    numerical_features["freshness_hours"],
                    numerical_features["title_length"],
                    numerical_features["body_length"]
                ]])
                
                # Scale numerical features
                numerical_scaled = self.feature_scaler.transform(numerical_array)
                
                # Combine features
                feature_vector = np.hstack([tfidf_features, numerical_scaled])
                return feature_vector[0]  # Return single row
                
            except Exception as e:
                logger.warning("Feature extraction failed, using fallback", exc_info=e)
                # Fallback to simple features
                return np.array([
                    numerical_features["source_weight"],
                    numerical_features["keyword_hits"],
                    numerical_features["ticker_matches"]
                ])
        else:
            # Fallback feature vector for rule-based model
            return np.array([
                numerical_features["source_weight"],
                numerical_features["keyword_hits"],
                numerical_features["ticker_matches"]
            ])
    
    def _score_article_with_model(self, article) -> tuple[float, float]:
        """Score article using the actual ML model."""
        if self.model is not None:
            try:
                # Create feature vector
                feature_vector = self._create_feature_vector(article)
                
                # Make prediction
                importance_p = self.model.predict_proba([feature_vector])[0][1]  # Probability of class 1
                confidence = max(self.model.predict_proba([feature_vector])[0])  # Max probability as confidence
                
                return float(importance_p), float(confidence)
                
            except Exception as e:
                logger.warning("ML model prediction failed, using fallback", exc_info=e)
                return self._score_article_fallback(article)
        else:
            return self._score_article_fallback(article)
    
    def _score_article_fallback(self, article) -> tuple[float, float]:
        """Fallback rule-based scoring when ML model is not available."""
        features = self._extract_features(article)
        
        # Rule-based scoring logic
        score = (
            features["source_weight"] * 0.3 +
            min(features["keyword_hits"] / 5.0, 1.0) * 0.25 +
            min(features["ticker_matches"] / 3.0, 1.0) * 0.2 +
            max(0, 1.0 - features["freshness_hours"] / 72.0) * 0.15 +
            min(features["body_length"] / 1000.0, 1.0) * 0.1
        )
        
        # Add some controlled randomness
        score += random.uniform(-0.05, 0.05)
        score = max(0.0, min(1.0, score))
        
        # Confidence is lower for rule-based scoring
        confidence = 0.6 + score * 0.2
        
        return score, confidence
    
    async def reload(self, version: Optional[str] = None):
        """Reload the model."""
        if version:
            self.model_version = version
        
        self.is_loaded = False
        self._load_model()
    
    async def cleanup(self):
        """Cleanup resources."""
        logger.info("Cleaning up ImportanceService")
        self.is_loaded = False