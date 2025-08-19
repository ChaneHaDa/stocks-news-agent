"""V1 API endpoints for ML services."""

import time
from typing import List, Dict, Any, Optional

from fastapi import APIRouter, HTTPException, Request, Depends
from pydantic import BaseModel, Field

from ..core.logging import get_logger
from ..services.model_manager import ModelManager
from ..services.clustering_service import clustering_service
from ..services.deep_embed_service import get_deep_embed_service, ModelType
from ..services.onnx_embed_service import get_onnx_embed_service
from ..services.model_comparison_service import get_model_comparison_service, ComparisonMode
from ..services.embedding_cache_service import get_embedding_cache_service

logger = get_logger(__name__)
router = APIRouter()


# Request/Response Models
class NewsArticle(BaseModel):
    """News article for processing."""
    id: str
    title: str = Field(..., min_length=1, max_length=1000)
    body: str = Field(..., min_length=10, max_length=10000)
    source: str
    published_at: str


class ImportanceRequest(BaseModel):
    """Request for importance scoring."""
    items: List[NewsArticle] = Field(..., max_items=100)


class ImportanceResult(BaseModel):
    """Importance scoring result."""
    id: str
    importance_p: float = Field(..., ge=0, le=1)
    features: Optional[Dict[str, float]] = None
    confidence: Optional[float] = Field(None, ge=0, le=1)


class ImportanceResponse(BaseModel):
    """Response for importance scoring."""
    results: List[ImportanceResult]
    model_version: str
    processed_at: float


class SummarizeRequest(BaseModel):
    """Request for summarization."""
    id: str
    title: str
    body: str
    tickers: Optional[List[str]] = None
    options: Optional[Dict[str, Any]] = None


class SummarizeResponse(BaseModel):
    """Response for summarization."""
    id: str
    summary: str = Field(..., max_length=300)
    reasons: Optional[List[str]] = None
    policy_flags: Optional[List[str]] = None
    model_version: str
    method_used: str
    generated_at: float


class TextItem(BaseModel):
    """Text item for embedding."""
    id: str
    text: str = Field(..., max_length=2000)


class EmbedRequest(BaseModel):
    """Request for text embedding."""
    items: List[TextItem] = Field(..., max_items=50)


class EmbedResult(BaseModel):
    """Embedding result."""
    id: str
    vector: List[float]
    norm: Optional[float] = None


class EmbedResponse(BaseModel):
    """Response for text embedding."""
    results: List[EmbedResult]
    model_version: str
    dimension: int
    processed_at: float


def get_model_manager(request: Request) -> ModelManager:
    """Get model manager from app state."""
    return request.app.state.model_manager


@router.post("/importance:score", response_model=ImportanceResponse)
async def score_importance(
    request: ImportanceRequest,
    model_manager: ModelManager = Depends(get_model_manager)
) -> ImportanceResponse:
    """Score news articles for importance."""
    try:
        logger.info("Processing importance request", item_count=len(request.items))
        
        if not model_manager.importance_service.is_loaded:
            raise HTTPException(
                status_code=503,
                detail={
                    "error": "Importance model not loaded",
                    "code": "MODEL_NOT_LOADED"
                }
            )
        
        # Process items
        results = await model_manager.importance_service.score_batch(request.items)
        
        return ImportanceResponse(
            results=[
                ImportanceResult(
                    id=result["id"],
                    importance_p=result["importance_p"],
                    features=result.get("features"),
                    confidence=result.get("confidence")
                )
                for result in results
            ],
            model_version=model_manager.importance_service.model_version or "unknown",
            processed_at=time.time()
        )
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error("Importance scoring failed", exc_info=e)
        raise HTTPException(
            status_code=500,
            detail={
                "error": f"Processing failed: {str(e)}",
                "code": "PROCESSING_ERROR"
            }
        )


@router.post("/summarize", response_model=SummarizeResponse)
async def summarize_article(
    request: SummarizeRequest,
    model_manager: ModelManager = Depends(get_model_manager)
) -> SummarizeResponse:
    """Generate summary for news article."""
    try:
        logger.info("Processing summarize request", article_id=request.id)
        
        if not model_manager.summarize_service.is_loaded:
            raise HTTPException(
                status_code=503,
                detail={
                    "error": "Summarize service not available",
                    "code": "SERVICE_NOT_AVAILABLE"
                }
            )
        
        # Process summarization
        result = await model_manager.summarize_service.summarize(
            id=request.id,
            title=request.title,
            body=request.body,
            tickers=request.tickers or [],
            options=request.options or {}
        )
        
        return SummarizeResponse(
            id=request.id,
            summary=result["summary"],
            reasons=result.get("reasons", []),
            policy_flags=result.get("policy_flags", []),
            model_version=result.get("model_version", "unknown"),
            method_used=result.get("method", "extractive"),
            generated_at=time.time()
        )
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error("Summarization failed", exc_info=e, article_id=request.id)
        raise HTTPException(
            status_code=500,
            detail={
                "error": f"Summarization failed: {str(e)}",
                "code": "PROCESSING_ERROR"
            }
        )


@router.post("/embed", response_model=EmbedResponse)
async def embed_text(
    request: EmbedRequest,
    model_manager: ModelManager = Depends(get_model_manager)
) -> EmbedResponse:
    """Generate embeddings for text."""
    try:
        logger.info("Processing embed request", item_count=len(request.items))
        
        if not model_manager.embed_service.is_loaded:
            raise HTTPException(
                status_code=503,
                detail={
                    "error": "Embedding model not loaded",
                    "code": "MODEL_NOT_LOADED"
                }
            )
        
        # Process embeddings
        results = await model_manager.embed_service.embed_batch(request.items)
        
        return EmbedResponse(
            results=[
                EmbedResult(
                    id=result["id"],
                    vector=result["vector"],
                    norm=result.get("norm")
                )
                for result in results
            ],
            model_version=model_manager.embed_service.model_version or "unknown",
            dimension=len(results[0]["vector"]) if results else 0,
            processed_at=time.time()
        )
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error("Embedding failed", exc_info=e)
        raise HTTPException(
            status_code=500,
            detail={
                "error": f"Embedding failed: {str(e)}",
                "code": "PROCESSING_ERROR"
            }
        )


# F3: Advanced Clustering API Models
class ClusteringRequest(BaseModel):
    """Request for clustering operations."""
    embeddings: List[List[float]] = Field(..., max_items=10000)
    ids: List[str] = Field(..., max_items=10000)


class HDBSCANRequest(ClusteringRequest):
    """Request for HDBSCAN clustering."""
    min_cluster_size: int = Field(default=3, ge=2, le=100)
    min_samples: int = Field(default=2, ge=1, le=50)
    cluster_selection_epsilon: float = Field(default=0.3, ge=0.0, le=1.0)


class KMeansRequest(ClusteringRequest):
    """Request for K-means clustering."""
    n_clusters: int = Field(..., ge=2, le=100)
    batch_size: int = Field(default=1000, ge=100, le=10000)
    max_iter: int = Field(default=100, ge=10, le=1000)
    random_state: int = Field(default=42, ge=0)


class QualityMetricsRequest(BaseModel):
    """Request for clustering quality metrics."""
    embeddings: List[List[float]] = Field(..., max_items=10000)
    labels: List[int] = Field(..., max_items=10000)


class ClusteringResponse(BaseModel):
    """Response for clustering operations."""
    success: bool
    message: str
    cluster_labels: List[int]
    metadata: Dict[str, Any]


class QualityMetricsResponse(BaseModel):
    """Response for clustering quality metrics."""
    silhouette_score: float
    davies_bouldin_index: float
    calinski_harabasz_index: float


# F3: Advanced Clustering Endpoints
@router.post("/cluster/hdbscan", response_model=ClusteringResponse)
async def hdbscan_clustering(request: HDBSCANRequest):
    """Perform HDBSCAN clustering on embeddings."""
    try:
        logger.info("Processing HDBSCAN clustering request", 
                   item_count=len(request.embeddings),
                   min_cluster_size=request.min_cluster_size)
        
        if not clustering_service.check_availability():
            raise HTTPException(
                status_code=503,
                detail={
                    "error": "HDBSCAN clustering service not available",
                    "code": "SERVICE_NOT_AVAILABLE"
                }
            )
        
        # Validate input
        if len(request.embeddings) != len(request.ids):
            raise HTTPException(
                status_code=400,
                detail={
                    "error": "Embeddings and IDs length mismatch",
                    "code": "VALIDATION_ERROR"
                }
            )
        
        # Perform HDBSCAN clustering
        result = clustering_service.hdbscan_clustering(
            embeddings=request.embeddings,
            ids=request.ids,
            min_cluster_size=request.min_cluster_size,
            min_samples=request.min_samples,
            cluster_selection_epsilon=request.cluster_selection_epsilon
        )
        
        if not result.success:
            raise HTTPException(
                status_code=400,
                detail={
                    "error": result.message,
                    "code": "CLUSTERING_FAILED"
                }
            )
        
        # Convert response
        return ClusteringResponse(
            success=result.success,
            message=result.message,
            cluster_labels=result.cluster_labels,
            metadata=result.metadata
        )
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error("HDBSCAN clustering failed", exc_info=e)
        raise HTTPException(
            status_code=500,
            detail={
                "error": f"HDBSCAN clustering failed: {str(e)}",
                "code": "PROCESSING_ERROR"
            }
        )


@router.post("/cluster/kmeans", response_model=ClusteringResponse)
async def kmeans_clustering(request: KMeansRequest):
    """Perform K-means mini-batch clustering on embeddings."""
    try:
        logger.info("Processing K-means clustering request", 
                   item_count=len(request.embeddings),
                   n_clusters=request.n_clusters)
        
        if not clustering_service.check_availability():
            raise HTTPException(
                status_code=503,
                detail={
                    "error": "K-means clustering service not available",
                    "code": "SERVICE_NOT_AVAILABLE"
                }
            )
        
        # Validate input
        if len(request.embeddings) != len(request.ids):
            raise HTTPException(
                status_code=400,
                detail={
                    "error": "Embeddings and IDs length mismatch",
                    "code": "VALIDATION_ERROR"
                }
            )
        
        if len(request.embeddings) < request.n_clusters:
            raise HTTPException(
                status_code=400,
                detail={
                    "error": f"Insufficient data points: {len(request.embeddings)} < {request.n_clusters}",
                    "code": "VALIDATION_ERROR"
                }
            )
        
        # Perform K-means clustering
        result = clustering_service.kmeans_minibatch_clustering(
            embeddings=request.embeddings,
            ids=request.ids,
            n_clusters=request.n_clusters,
            batch_size=request.batch_size,
            max_iter=request.max_iter,
            random_state=request.random_state
        )
        
        if not result.success:
            raise HTTPException(
                status_code=400,
                detail={
                    "error": result.message,
                    "code": "CLUSTERING_FAILED"
                }
            )
        
        # Convert response
        return ClusteringResponse(
            success=result.success,
            message=result.message,
            cluster_labels=result.cluster_labels,
            metadata=result.metadata
        )
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error("K-means clustering failed", exc_info=e)
        raise HTTPException(
            status_code=500,
            detail={
                "error": f"K-means clustering failed: {str(e)}",
                "code": "PROCESSING_ERROR"
            }
        )


@router.post("/cluster/quality-metrics", response_model=QualityMetricsResponse)
async def quality_metrics(request: QualityMetricsRequest):
    """Calculate clustering quality metrics."""
    try:
        logger.info("Processing quality metrics request", 
                   item_count=len(request.embeddings))
        
        if not clustering_service.check_availability():
            raise HTTPException(
                status_code=503,
                detail={
                    "error": "Clustering quality metrics service not available",
                    "code": "SERVICE_NOT_AVAILABLE"
                }
            )
        
        # Validate input
        if len(request.embeddings) != len(request.labels):
            raise HTTPException(
                status_code=400,
                detail={
                    "error": "Embeddings and labels length mismatch",
                    "code": "VALIDATION_ERROR"
                }
            )
        
        # Calculate quality metrics
        result = clustering_service.calculate_quality_metrics(
            embeddings=request.embeddings,
            labels=request.labels
        )
        
        return QualityMetricsResponse(
            silhouette_score=result.silhouette_score,
            davies_bouldin_index=result.davies_bouldin_index,
            calinski_harabasz_index=result.calinski_harabasz_index
        )
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error("Quality metrics calculation failed", exc_info=e)
        raise HTTPException(
            status_code=500,
            detail={
                "error": f"Quality metrics calculation failed: {str(e)}",
                "code": "PROCESSING_ERROR"
            }
        )


# F4: Deep Learning Embedding API Models
class DeepEmbedRequest(BaseModel):
    """Request for deep learning embedding generation."""
    items: List[EmbedRequest] = Field(..., max_items=100)
    model_type: str = Field(default="kobert", description="Model type: kobert, roberta, sentence-transformer")
    use_cache: bool = Field(default=True, description="Use caching for embeddings")
    use_onnx: bool = Field(default=False, description="Use ONNX optimized version")


class DeepEmbedResponse(BaseModel):
    """Response for deep learning embedding generation."""
    success: bool
    message: str
    items: List[EmbedResponseItem]
    model_info: Dict[str, Any]
    performance_metrics: Dict[str, Any]


class ModelComparisonRequest(BaseModel):
    """Request for model A/B testing."""
    model_a: str = Field(..., description="First model to compare")
    model_b: str = Field(..., description="Second model to compare")
    comparison_mode: str = Field(default="comprehensive", description="Comparison mode: performance, quality, comprehensive")


class ModelComparisonResponse(BaseModel):
    """Response for model comparison."""
    test_id: str
    model_a: str
    model_b: str
    winner: Optional[str]
    confidence_level: float
    recommendation: str
    performance_comparison: Dict[str, Any]
    quality_comparison: Dict[str, Any]
    test_duration_sec: float


class CacheStatsResponse(BaseModel):
    """Response for cache statistics."""
    overall: Dict[str, Any]
    by_model: Dict[str, Any]
    configurations: Dict[str, Any]


# F4: Deep Learning Embedding Endpoints
@router.post("/embed/deep", response_model=DeepEmbedResponse)
async def deep_embed_text(request: DeepEmbedRequest):
    """Generate embeddings using deep learning models (Ko-BERT, RoBERTa)."""
    try:
        logger.info("Processing deep embedding request", 
                   item_count=len(request.items),
                   model_type=request.model_type,
                   use_cache=request.use_cache,
                   use_onnx=request.use_onnx)
        
        start_time = time.time()
        
        # Get services
        cache_service = get_embedding_cache_service(ModelManager.settings)
        
        # Check cache first if enabled
        cached_results = []
        items_to_process = request.items
        
        if request.use_cache:
            model_name = f"onnx_{request.model_type}" if request.use_onnx else request.model_type
            cached_embeddings, cache_misses = await cache_service.get_cached_embeddings(
                request.items, model_name
            )
            
            # Convert cached results to response format
            for cached in cached_embeddings:
                cached_results.append(EmbedResponseItem(
                    id=cached.text_hash[:8],  # Use hash prefix as ID
                    vector=cached.embedding_vector,
                    norm=cached.norm
                ))
            
            items_to_process = cache_misses
            logger.info(f"Cache: {len(cached_embeddings)} hits, {len(cache_misses)} misses")
        
        # Process remaining items
        new_results = []
        if items_to_process:
            if request.use_onnx:
                # Use ONNX optimized service
                onnx_service = get_onnx_embed_service(ModelManager.settings)
                onnx_results = await onnx_service.embed_batch_onnx(items_to_process, request.model_type)
                
                new_results = [
                    EmbedResponseItem(
                        id=result.id,
                        vector=result.vector,
                        norm=result.norm
                    )
                    for result in onnx_results
                ]
                
                # Store in cache if enabled
                if request.use_cache:
                    embedding_dicts = [{"vector": r.vector, "norm": r.norm} for r in onnx_results]
                    await cache_service.store_embeddings(
                        items_to_process, embedding_dicts, f"onnx_{request.model_type}"
                    )
            else:
                # Use regular deep learning service
                deep_service = get_deep_embed_service(ModelManager.settings)
                model_type = ModelType.KOBERT if "kobert" in request.model_type.lower() else ModelType.ROBERTA_KOREAN
                
                deep_results = await deep_service.embed_batch(items_to_process, model_type)
                
                new_results = [
                    EmbedResponseItem(
                        id=result.id,
                        vector=result.vector,
                        norm=result.norm
                    )
                    for result in deep_results
                ]
                
                # Store in cache if enabled
                if request.use_cache:
                    embedding_dicts = [{"vector": r.vector, "norm": r.norm} for r in deep_results]
                    await cache_service.store_embeddings(
                        items_to_process, embedding_dicts, request.model_type
                    )
        
        # Combine cached and new results
        all_results = cached_results + new_results
        
        duration = time.time() - start_time
        
        # Get model info
        if request.use_onnx:
            onnx_service = get_onnx_embed_service(ModelManager.settings)
            model_info = await onnx_service.get_performance_stats()
        else:
            deep_service = get_deep_embed_service(ModelManager.settings)
            model_info = await deep_service.get_model_info()
        
        return DeepEmbedResponse(
            success=True,
            message=f"Generated {len(all_results)} embeddings using {request.model_type}",
            items=all_results,
            model_info=model_info,
            performance_metrics={
                "total_duration_ms": duration * 1000,
                "items_processed": len(request.items),
                "cache_hits": len(cached_results),
                "new_computations": len(new_results),
                "use_onnx": request.use_onnx,
                "model_type": request.model_type
            }
        )
        
    except Exception as e:
        logger.error("Deep embedding generation failed", exc_info=e)
        raise HTTPException(
            status_code=500,
            detail={
                "error": f"Deep embedding generation failed: {str(e)}",
                "code": "PROCESSING_ERROR"
            }
        )


@router.post("/models/compare", response_model=ModelComparisonResponse)
async def compare_models(request: ModelComparisonRequest):
    """Run A/B test comparison between two embedding models."""
    try:
        logger.info("Starting model comparison", 
                   model_a=request.model_a,
                   model_b=request.model_b,
                   mode=request.comparison_mode)
        
        comparison_service = get_model_comparison_service(ModelManager.settings)
        
        # Convert string to enum
        if request.comparison_mode == "performance":
            mode = ComparisonMode.PERFORMANCE
        elif request.comparison_mode == "quality":
            mode = ComparisonMode.QUALITY
        else:
            mode = ComparisonMode.COMPREHENSIVE
        
        # Run A/B test
        result = await comparison_service.run_ab_test(
            request.model_a, request.model_b, mode
        )
        
        return ModelComparisonResponse(
            test_id=result.test_id,
            model_a=result.model_a,
            model_b=result.model_b,
            winner=result.winner,
            confidence_level=result.confidence_level,
            recommendation=result.recommendation,
            performance_comparison=result.performance_comparison,
            quality_comparison=result.quality_comparison,
            test_duration_sec=result.test_duration_sec
        )
        
    except Exception as e:
        logger.error("Model comparison failed", exc_info=e)
        raise HTTPException(
            status_code=500,
            detail={
                "error": f"Model comparison failed: {str(e)}",
                "code": "PROCESSING_ERROR"
            }
        )


@router.get("/models/benchmark/{model_name}")
async def benchmark_model(model_name: str):
    """Benchmark specific model performance."""
    try:
        logger.info(f"Benchmarking model: {model_name}")
        
        comparison_service = get_model_comparison_service(ModelManager.settings)
        
        # Run performance benchmark
        performance_result = await comparison_service.benchmark_model_performance(model_name)
        
        return {
            "model_name": model_name,
            "performance_metrics": {
                "mean_latency_ms": performance_result.mean_latency_ms,
                "p95_latency_ms": performance_result.p95_latency_ms,
                "p99_latency_ms": performance_result.p99_latency_ms,
                "throughput_per_sec": performance_result.throughput_per_sec,
                "success_rate": performance_result.success_rate,
                "embedding_dimension": performance_result.embedding_dimension
            },
            "test_summary": {
                "total_requests": performance_result.total_requests,
                "error_count": performance_result.error_count,
                "model_type": performance_result.model_type
            }
        }
        
    except Exception as e:
        logger.error(f"Model benchmark failed: {model_name}", exc_info=e)
        raise HTTPException(
            status_code=500,
            detail={
                "error": f"Model benchmark failed: {str(e)}",
                "code": "PROCESSING_ERROR"
            }
        )


@router.get("/cache/stats", response_model=CacheStatsResponse)
async def get_cache_statistics():
    """Get embedding cache statistics and performance metrics."""
    try:
        cache_service = get_embedding_cache_service(ModelManager.settings)
        stats = await cache_service.get_cache_statistics()
        
        return CacheStatsResponse(
            overall=stats["overall"],
            by_model=stats["by_model"],
            configurations=stats["configurations"]
        )
        
    except Exception as e:
        logger.error("Cache statistics retrieval failed", exc_info=e)
        raise HTTPException(
            status_code=500,
            detail={
                "error": f"Cache statistics retrieval failed: {str(e)}",
                "code": "PROCESSING_ERROR"
            }
        )


@router.post("/cache/invalidate")
async def invalidate_cache(model_name: Optional[str] = None, text_pattern: Optional[str] = None):
    """Invalidate embedding cache entries."""
    try:
        cache_service = get_embedding_cache_service(ModelManager.settings)
        await cache_service.invalidate_cache(model_name, text_pattern)
        
        return {
            "success": True,
            "message": f"Cache invalidated for model: {model_name or 'all'}, pattern: {text_pattern or 'all'}",
            "timestamp": time.time()
        }
        
    except Exception as e:
        logger.error("Cache invalidation failed", exc_info=e)
        raise HTTPException(
            status_code=500,
            detail={
                "error": f"Cache invalidation failed: {str(e)}",
                "code": "PROCESSING_ERROR"
            }
        )