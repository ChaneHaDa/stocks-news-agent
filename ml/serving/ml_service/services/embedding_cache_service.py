"""F4: Advanced embedding caching service with model-specific strategies."""

import asyncio
import hashlib
import json
import time
from typing import List, Dict, Any, Optional, Tuple, Union
from dataclasses import dataclass, asdict
from enum import Enum
import pickle
import gzip

import redis.asyncio as redis
from cachetools import TTLCache, LRUCache

from ..core.config import Settings
from ..core.logging import get_logger
from ..core.metrics import MODEL_INFERENCE_DURATION, MODEL_INFERENCE_COUNT

logger = get_logger(__name__)


class CacheStrategy(Enum):
    """Cache strategies for different scenarios."""
    MEMORY_ONLY = "memory_only"
    REDIS_ONLY = "redis_only"
    HYBRID = "hybrid"
    PERSISTENT = "persistent"


class CompressionType(Enum):
    """Compression types for cache storage."""
    NONE = "none"
    GZIP = "gzip"
    PICKLE = "pickle"
    GZIP_PICKLE = "gzip_pickle"


@dataclass
class CacheConfig:
    """Cache configuration for different models."""
    model_name: str
    strategy: CacheStrategy
    ttl_seconds: int
    max_memory_items: int
    compression: CompressionType
    redis_key_prefix: str
    enable_precompute: bool = False
    batch_eviction: bool = True


@dataclass
class CacheStats:
    """Cache statistics for monitoring."""
    model_name: str
    total_requests: int
    cache_hits: int
    cache_misses: int
    hit_rate: float
    avg_retrieval_time_ms: float
    memory_usage_mb: float
    redis_usage_items: int
    eviction_count: int
    last_cleanup: float


@dataclass
class CachedEmbedding:
    """Cached embedding with metadata."""
    text_hash: str
    model_name: str
    embedding_vector: List[float]
    dimension: int
    norm: float
    created_at: float
    access_count: int
    last_accessed: float
    compression_type: str


class EmbeddingCacheService:
    """Advanced caching service for embedding results with multiple strategies."""
    
    def __init__(self, settings: Settings):
        self.settings = settings
        
        # Cache configurations for different models
        self.cache_configs = {
            "mock": CacheConfig(
                model_name="mock",
                strategy=CacheStrategy.MEMORY_ONLY,
                ttl_seconds=3600,  # 1 hour
                max_memory_items=10000,
                compression=CompressionType.NONE,
                redis_key_prefix="embed:mock:"
            ),
            "kobert": CacheConfig(
                model_name="kobert",
                strategy=CacheStrategy.HYBRID,
                ttl_seconds=86400,  # 24 hours
                max_memory_items=5000,
                compression=CompressionType.GZIP_PICKLE,
                redis_key_prefix="embed:kobert:",
                enable_precompute=True
            ),
            "roberta": CacheConfig(
                model_name="roberta",
                strategy=CacheStrategy.HYBRID,
                ttl_seconds=86400,  # 24 hours
                max_memory_items=5000,
                compression=CompressionType.GZIP_PICKLE,
                redis_key_prefix="embed:roberta:",
                enable_precompute=True
            ),
            "onnx_kobert": CacheConfig(
                model_name="onnx_kobert",
                strategy=CacheStrategy.PERSISTENT,
                ttl_seconds=172800,  # 48 hours (longer for expensive ONNX)
                max_memory_items=8000,
                compression=CompressionType.GZIP_PICKLE,
                redis_key_prefix="embed:onnx:kobert:",
                enable_precompute=True
            ),
            "onnx_roberta": CacheConfig(
                model_name="onnx_roberta",
                strategy=CacheStrategy.PERSISTENT,
                ttl_seconds=172800,  # 48 hours
                max_memory_items=8000,
                compression=CompressionType.GZIP_PICKLE,
                redis_key_prefix="embed:onnx:roberta:",
                enable_precompute=True
            )
        }
        
        # Initialize cache layers
        self.memory_caches = {}
        self.redis_client = None
        self.cache_stats = {}
        
        # Performance monitoring
        self.request_times = {}
        self.cleanup_interval = 3600  # 1 hour
        self.last_cleanup = time.time()
        
        # Initialize caches
        self._initialize_memory_caches()
        asyncio.create_task(self._initialize_redis())
        
        logger.info("EmbeddingCacheService initialized",
                   cache_configs=list(self.cache_configs.keys()),
                   cleanup_interval=self.cleanup_interval)
    
    def _initialize_memory_caches(self):
        """Initialize memory caches for each model."""
        for model_name, config in self.cache_configs.items():
            if config.strategy in [CacheStrategy.MEMORY_ONLY, CacheStrategy.HYBRID]:
                self.memory_caches[model_name] = TTLCache(
                    maxsize=config.max_memory_items,
                    ttl=config.ttl_seconds
                )
            
            # Initialize stats
            self.cache_stats[model_name] = CacheStats(
                model_name=model_name,
                total_requests=0,
                cache_hits=0,
                cache_misses=0,
                hit_rate=0.0,
                avg_retrieval_time_ms=0.0,
                memory_usage_mb=0.0,
                redis_usage_items=0,
                eviction_count=0,
                last_cleanup=time.time()
            )
            
            self.request_times[model_name] = []
        
        logger.info("Memory caches initialized", 
                   models=list(self.memory_caches.keys()))
    
    async def _initialize_redis(self):
        """Initialize Redis connection if available."""
        try:
            redis_url = getattr(self.settings, 'redis_url', 'redis://localhost:6379/0')
            self.redis_client = redis.from_url(redis_url, decode_responses=False)
            
            # Test connection
            await self.redis_client.ping()
            logger.info("Redis cache initialized", redis_url=redis_url)
            
        except Exception as e:
            logger.warning("Redis not available, using memory-only caching", exc_info=e)
            self.redis_client = None
            
            # Fallback to memory-only for all configs
            for config in self.cache_configs.values():
                if config.strategy in [CacheStrategy.REDIS_ONLY, CacheStrategy.HYBRID, CacheStrategy.PERSISTENT]:
                    config.strategy = CacheStrategy.MEMORY_ONLY
    
    async def get_cached_embeddings(self, text_items: List[Any], 
                                  model_name: str) -> Tuple[List[CachedEmbedding], List[Any]]:
        """Get cached embeddings and return cache misses for computation."""
        start_time = time.time()
        
        config = self.cache_configs.get(model_name, self.cache_configs["mock"])
        cached_results = []
        cache_misses = []
        
        for item in text_items:
            text_hash = self._generate_text_hash(item.text, model_name)
            cached_embedding = await self._get_from_cache(text_hash, model_name, config)
            
            if cached_embedding:
                # Update access statistics
                cached_embedding.access_count += 1
                cached_embedding.last_accessed = time.time()
                cached_results.append(cached_embedding)
                self.cache_stats[model_name].cache_hits += 1
            else:
                cache_misses.append(item)
                self.cache_stats[model_name].cache_misses += 1
            
            self.cache_stats[model_name].total_requests += 1
        
        # Update statistics
        retrieval_time = (time.time() - start_time) * 1000
        self.request_times[model_name].append(retrieval_time)
        
        stats = self.cache_stats[model_name]
        stats.hit_rate = stats.cache_hits / stats.total_requests if stats.total_requests > 0 else 0
        stats.avg_retrieval_time_ms = sum(self.request_times[model_name][-100:]) / min(100, len(self.request_times[model_name]))
        
        logger.debug(f"Cache lookup completed for {model_name}",
                    total_items=len(text_items),
                    cache_hits=len(cached_results),
                    cache_misses=len(cache_misses),
                    hit_rate=stats.hit_rate,
                    retrieval_time_ms=retrieval_time)
        
        return cached_results, cache_misses
    
    async def store_embeddings(self, text_items: List[Any], 
                             embedding_results: List[Dict[str, Any]], 
                             model_name: str):
        """Store computed embeddings in cache."""
        config = self.cache_configs.get(model_name, self.cache_configs["mock"])
        
        for i, (item, result) in enumerate(zip(text_items, embedding_results)):
            text_hash = self._generate_text_hash(item.text, model_name)
            
            cached_embedding = CachedEmbedding(
                text_hash=text_hash,
                model_name=model_name,
                embedding_vector=result["vector"],
                dimension=len(result["vector"]),
                norm=result.get("norm", 0.0),
                created_at=time.time(),
                access_count=1,
                last_accessed=time.time(),
                compression_type=config.compression.value
            )
            
            await self._store_in_cache(text_hash, cached_embedding, model_name, config)
        
        logger.debug(f"Stored {len(embedding_results)} embeddings for {model_name}")
    
    async def _get_from_cache(self, text_hash: str, model_name: str, 
                            config: CacheConfig) -> Optional[CachedEmbedding]:
        """Get embedding from cache using configured strategy."""
        # Try memory cache first (if applicable)
        if config.strategy in [CacheStrategy.MEMORY_ONLY, CacheStrategy.HYBRID] and model_name in self.memory_caches:
            cached = self.memory_caches[model_name].get(text_hash)
            if cached:
                return cached
        
        # Try Redis cache (if applicable and available)
        if config.strategy in [CacheStrategy.REDIS_ONLY, CacheStrategy.HYBRID, CacheStrategy.PERSISTENT] and self.redis_client:
            try:
                redis_key = f"{config.redis_key_prefix}{text_hash}"
                cached_data = await self.redis_client.get(redis_key)
                
                if cached_data:
                    cached_embedding = self._decompress_embedding(cached_data, config.compression)
                    
                    # Store in memory cache for faster future access (if hybrid strategy)
                    if config.strategy == CacheStrategy.HYBRID and model_name in self.memory_caches:
                        self.memory_caches[model_name][text_hash] = cached_embedding
                    
                    return cached_embedding
                    
            except Exception as e:
                logger.warning(f"Redis cache retrieval failed for {model_name}", exc_info=e)
        
        return None
    
    async def _store_in_cache(self, text_hash: str, embedding: CachedEmbedding, 
                            model_name: str, config: CacheConfig):
        """Store embedding in cache using configured strategy."""
        # Store in memory cache (if applicable)
        if config.strategy in [CacheStrategy.MEMORY_ONLY, CacheStrategy.HYBRID] and model_name in self.memory_caches:
            self.memory_caches[model_name][text_hash] = embedding
        
        # Store in Redis cache (if applicable and available)
        if config.strategy in [CacheStrategy.REDIS_ONLY, CacheStrategy.HYBRID, CacheStrategy.PERSISTENT] and self.redis_client:
            try:
                redis_key = f"{config.redis_key_prefix}{text_hash}"
                compressed_data = self._compress_embedding(embedding, config.compression)
                
                await self.redis_client.setex(
                    redis_key,
                    config.ttl_seconds,
                    compressed_data
                )
                
            except Exception as e:
                logger.warning(f"Redis cache storage failed for {model_name}", exc_info=e)
    
    def _generate_text_hash(self, text: str, model_name: str) -> str:
        """Generate hash for text + model combination."""
        content = f"{model_name}:{text}"
        return hashlib.sha256(content.encode('utf-8')).hexdigest()
    
    def _compress_embedding(self, embedding: CachedEmbedding, 
                          compression: CompressionType) -> bytes:
        """Compress embedding for storage."""
        data = asdict(embedding)
        
        if compression == CompressionType.NONE:
            return json.dumps(data).encode('utf-8')
        elif compression == CompressionType.GZIP:
            return gzip.compress(json.dumps(data).encode('utf-8'))
        elif compression == CompressionType.PICKLE:
            return pickle.dumps(data)
        elif compression == CompressionType.GZIP_PICKLE:
            return gzip.compress(pickle.dumps(data))
        else:
            return json.dumps(data).encode('utf-8')
    
    def _decompress_embedding(self, data: bytes, 
                            compression: CompressionType) -> CachedEmbedding:
        """Decompress embedding from storage."""
        try:
            if compression == CompressionType.NONE:
                embedding_dict = json.loads(data.decode('utf-8'))
            elif compression == CompressionType.GZIP:
                embedding_dict = json.loads(gzip.decompress(data).decode('utf-8'))
            elif compression == CompressionType.PICKLE:
                embedding_dict = pickle.loads(data)
            elif compression == CompressionType.GZIP_PICKLE:
                embedding_dict = pickle.loads(gzip.decompress(data))
            else:
                embedding_dict = json.loads(data.decode('utf-8'))
            
            return CachedEmbedding(**embedding_dict)
            
        except Exception as e:
            logger.error("Failed to decompress embedding", exc_info=e)
            raise
    
    async def precompute_embeddings(self, model_name: str, texts: List[str]):
        """Precompute embeddings for frequently accessed texts."""
        config = self.cache_configs.get(model_name, self.cache_configs["mock"])
        
        if not config.enable_precompute:
            logger.debug(f"Precompute disabled for {model_name}")
            return
        
        logger.info(f"Starting precompute for {model_name}", text_count=len(texts))
        
        # This would call the actual embedding service
        # For now, we'll create placeholder cached entries
        for text in texts:
            text_hash = self._generate_text_hash(text, model_name)
            
            # Check if already cached
            cached = await self._get_from_cache(text_hash, model_name, config)
            if cached:
                continue
            
            # Create placeholder embedding (in production, call actual service)
            placeholder_embedding = CachedEmbedding(
                text_hash=text_hash,
                model_name=model_name,
                embedding_vector=[0.0] * 768,  # Placeholder
                dimension=768,
                norm=1.0,
                created_at=time.time(),
                access_count=0,
                last_accessed=time.time(),
                compression_type=config.compression.value
            )
            
            await self._store_in_cache(text_hash, placeholder_embedding, model_name, config)
        
        logger.info(f"Precompute completed for {model_name}")
    
    async def cleanup_expired_cache(self):
        """Clean up expired cache entries and update statistics."""
        current_time = time.time()
        
        # Skip if cleanup was done recently
        if current_time - self.last_cleanup < self.cleanup_interval:
            return
        
        logger.info("Starting cache cleanup")
        
        for model_name, config in self.cache_configs.items():
            stats = self.cache_stats[model_name]
            
            # Memory cache cleanup (TTLCache handles this automatically)
            if model_name in self.memory_caches:
                cache = self.memory_caches[model_name]
                old_size = len(cache)
                # Force cleanup by accessing cache info
                _ = cache.currsize
                new_size = len(cache)
                stats.eviction_count += (old_size - new_size)
                
                # Estimate memory usage (rough calculation)
                stats.memory_usage_mb = new_size * 0.01  # Rough estimate
            
            # Redis cache statistics (if available)
            if self.redis_client and config.strategy in [CacheStrategy.REDIS_ONLY, CacheStrategy.HYBRID, CacheStrategy.PERSISTENT]:
                try:
                    pattern = f"{config.redis_key_prefix}*"
                    keys = await self.redis_client.keys(pattern)
                    stats.redis_usage_items = len(keys)
                except Exception as e:
                    logger.warning(f"Redis stats collection failed for {model_name}", exc_info=e)
            
            stats.last_cleanup = current_time
        
        self.last_cleanup = current_time
        logger.info("Cache cleanup completed")
    
    async def get_cache_statistics(self) -> Dict[str, Any]:
        """Get comprehensive cache statistics."""
        await self.cleanup_expired_cache()
        
        stats = {
            "overall": {
                "total_models": len(self.cache_configs),
                "redis_available": self.redis_client is not None,
                "cleanup_interval_sec": self.cleanup_interval,
                "last_cleanup": self.last_cleanup
            },
            "by_model": {}
        }
        
        for model_name, model_stats in self.cache_stats.items():
            stats["by_model"][model_name] = asdict(model_stats)
        
        # Add configuration info
        stats["configurations"] = {
            model_name: {
                "strategy": config.strategy.value,
                "ttl_seconds": config.ttl_seconds,
                "max_memory_items": config.max_memory_items,
                "compression": config.compression.value,
                "precompute_enabled": config.enable_precompute
            }
            for model_name, config in self.cache_configs.items()
        }
        
        return stats
    
    async def invalidate_cache(self, model_name: Optional[str] = None, 
                             text_pattern: Optional[str] = None):
        """Invalidate cache entries."""
        if model_name and model_name not in self.cache_configs:
            raise ValueError(f"Unknown model: {model_name}")
        
        models_to_clear = [model_name] if model_name else list(self.cache_configs.keys())
        
        for model in models_to_clear:
            config = self.cache_configs[model]
            
            # Clear memory cache
            if model in self.memory_caches:
                if text_pattern:
                    # Remove specific patterns (simplified)
                    keys_to_remove = [k for k in self.memory_caches[model].keys() if text_pattern in k]
                    for key in keys_to_remove:
                        del self.memory_caches[model][key]
                else:
                    self.memory_caches[model].clear()
            
            # Clear Redis cache
            if self.redis_client:
                try:
                    if text_pattern:
                        pattern = f"{config.redis_key_prefix}*{text_pattern}*"
                    else:
                        pattern = f"{config.redis_key_prefix}*"
                    
                    keys = await self.redis_client.keys(pattern)
                    if keys:
                        await self.redis_client.delete(*keys)
                        
                except Exception as e:
                    logger.warning(f"Redis cache invalidation failed for {model}", exc_info=e)
        
        logger.info(f"Cache invalidated", 
                   models=models_to_clear, 
                   pattern=text_pattern)
    
    async def cleanup(self):
        """Cleanup cache service resources."""
        logger.info("Cleaning up EmbeddingCacheService")
        
        # Clear memory caches
        for cache in self.memory_caches.values():
            cache.clear()
        
        # Close Redis connection
        if self.redis_client:
            await self.redis_client.close()
        
        logger.info("EmbeddingCacheService cleanup completed")


# Global instance
embedding_cache_service = None


def get_embedding_cache_service(settings: Settings) -> EmbeddingCacheService:
    """Get or create embedding cache service instance."""
    global embedding_cache_service
    if embedding_cache_service is None:
        embedding_cache_service = EmbeddingCacheService(settings)
    return embedding_cache_service