"""Importance scoring service (mock implementation)."""

import time
import random
from typing import List, Dict, Any, Optional

from ..core.config import Settings
from ..core.logging import get_logger
from ..core.metrics import MODEL_INFERENCE_DURATION, MODEL_INFERENCE_COUNT

logger = get_logger(__name__)


class ImportanceService:
    """Service for scoring news importance."""
    
    def __init__(self, settings: Settings):
        self.settings = settings
        self.is_loaded = False
        self.model_version = "mock-v1.0.0"
        self.last_loaded: Optional[float] = None
        self.inference_count = 0
        
        # Load model on initialization
        if settings.enable_importance:
            self._load_model()
    
    def _load_model(self):
        """Load the importance model (mock implementation)."""
        try:
            logger.info("Loading importance model", version=self.model_version)
            
            # Mock loading time
            time.sleep(0.1)
            
            self.is_loaded = True
            self.last_loaded = time.time()
            
            logger.info("Importance model loaded successfully")
            
        except Exception as e:
            logger.error("Failed to load importance model", exc_info=e)
            self.is_loaded = False
            raise
    
    async def score_batch(self, articles: List[Any]) -> List[Dict[str, Any]]:
        """Score a batch of articles for importance."""
        if not self.is_loaded:
            raise RuntimeError("Importance model not loaded")
        
        start_time = time.time()
        
        try:
            results = []
            
            for article in articles:
                # Mock feature extraction and scoring
                features = self._extract_features(article)
                importance_p = self._score_article(features)
                confidence = random.uniform(0.7, 0.95)
                
                results.append({
                    "id": article.id,
                    "importance_p": importance_p,
                    "features": features,
                    "confidence": confidence
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
        """Extract features from article (mock implementation)."""
        # Mock feature extraction
        features = {
            "source_weight": random.uniform(0.3, 0.9),
            "keyword_hits": random.randint(0, 5),
            "ner_count": random.randint(0, 10),
            "title_length": len(article.title),
            "body_length": len(article.body),
            "freshness_hours": random.uniform(0, 72),
            "ticker_matches": random.randint(0, 3)
        }
        
        return features
    
    def _score_article(self, features: Dict[str, float]) -> float:
        """Score article importance (mock implementation)."""
        # Mock scoring logic
        score = (
            features["source_weight"] * 0.3 +
            min(features["keyword_hits"] / 5.0, 1.0) * 0.25 +
            min(features["ticker_matches"] / 3.0, 1.0) * 0.2 +
            max(0, 1.0 - features["freshness_hours"] / 72.0) * 0.15 +
            min(features["body_length"] / 1000.0, 1.0) * 0.1
        )
        
        # Add some noise
        score += random.uniform(-0.1, 0.1)
        
        return max(0.0, min(1.0, score))
    
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