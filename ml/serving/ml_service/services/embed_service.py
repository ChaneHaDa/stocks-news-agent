"""Text embedding service (mock implementation)."""

import time
import random
from typing import List, Dict, Any, Optional

from ..core.config import Settings
from ..core.logging import get_logger
from ..core.metrics import MODEL_INFERENCE_DURATION, MODEL_INFERENCE_COUNT

logger = get_logger(__name__)


class EmbedService:
    """Service for generating text embeddings."""
    
    def __init__(self, settings: Settings):
        self.settings = settings
        self.is_loaded = False
        self.model_version = "mock-embed-v1.0.0"
        self.embedding_dimension = 384  # Common dimension for sentence transformers
        self.last_loaded: Optional[float] = None
        self.embedding_count = 0
        
        # Load model on initialization
        if settings.enable_embed:
            self._load_model()
    
    def _load_model(self):
        """Load the embedding model (mock implementation)."""
        try:
            logger.info("Loading embedding model", model_name=self.settings.embed_model_name)
            
            # Mock loading time
            time.sleep(0.2)
            
            self.is_loaded = True
            self.last_loaded = time.time()
            
            logger.info("Embedding model loaded successfully")
            
        except Exception as e:
            logger.error("Failed to load embedding model", exc_info=e)
            self.is_loaded = False
            raise
    
    async def embed_batch(self, text_items: List[Any]) -> List[Dict[str, Any]]:
        """Generate embeddings for a batch of text items."""
        if not self.is_loaded:
            raise RuntimeError("Embedding model not loaded")
        
        start_time = time.time()
        
        try:
            results = []
            
            for item in text_items:
                # Mock embedding generation
                vector = self._generate_mock_embedding(item.text)
                norm = self._calculate_norm(vector)
                
                results.append({
                    "id": item.id,
                    "vector": vector,
                    "norm": norm
                })
                
                self.embedding_count += 1
            
            duration = time.time() - start_time
            
            # Update metrics
            MODEL_INFERENCE_DURATION.labels(
                model_type="embed",
                model_version=self.model_version
            ).observe(duration)
            
            MODEL_INFERENCE_COUNT.labels(
                model_type="embed",
                model_version=self.model_version,
                status="success"
            ).inc(len(text_items))
            
            logger.info(
                "Embeddings generated",
                item_count=len(text_items),
                duration=duration,
                dimension=self.embedding_dimension
            )
            
            return results
            
        except Exception as e:
            MODEL_INFERENCE_COUNT.labels(
                model_type="embed",
                model_version=self.model_version,
                status="error"
            ).inc()
            
            logger.error("Embedding generation failed", exc_info=e)
            raise
    
    def _generate_mock_embedding(self, text: str) -> List[float]:
        """Generate mock embedding vector."""
        # Create deterministic but pseudo-random embedding based on text
        random.seed(hash(text) % (2**32))
        
        # Generate normalized random vector
        vector = [random.gauss(0, 1) for _ in range(self.embedding_dimension)]
        
        # Normalize to unit length
        norm = sum(x**2 for x in vector) ** 0.5
        if norm > 0:
            vector = [x / norm for x in vector]
        
        return vector
    
    def _calculate_norm(self, vector: List[float]) -> float:
        """Calculate L2 norm of vector."""
        return sum(x**2 for x in vector) ** 0.5
    
    async def reload(self, version: Optional[str] = None):
        """Reload the model."""
        if version:
            self.model_version = version
        
        self.is_loaded = False
        self._load_model()
    
    async def cleanup(self):
        """Cleanup resources."""
        logger.info("Cleaning up EmbedService")
        self.is_loaded = False