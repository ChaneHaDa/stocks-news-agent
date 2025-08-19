"""Admin API endpoints."""

import time
from typing import Dict, Any, Optional

from fastapi import APIRouter, HTTPException, Query, Request, Depends
from pydantic import BaseModel

from ..core.logging import get_logger
from ..services.model_manager import ModelManager
from ..services.clustering_service import clustering_service
from ..services.deep_embed_service import get_deep_embed_service
from ..services.onnx_embed_service import get_onnx_embed_service
from ..services.embedding_cache_service import get_embedding_cache_service

logger = get_logger(__name__)
router = APIRouter()


class HealthResponse(BaseModel):
    """Health check response model."""
    status: str
    models: Dict[str, Any]
    clustering: Optional[Dict[str, Any]] = None
    deep_learning: Optional[Dict[str, Any]] = None
    onnx_optimization: Optional[Dict[str, Any]] = None
    caching: Optional[Dict[str, Any]] = None
    timestamp: float


class ModelStatus(BaseModel):
    """Model status information."""
    loaded: bool
    version: Optional[str] = None
    last_loaded: Optional[float] = None
    memory_usage: Optional[int] = None


def get_model_manager(request: Request) -> ModelManager:
    """Get model manager from app state."""
    return request.app.state.model_manager


@router.get("/health", response_model=HealthResponse)
async def health_check(
    model_manager: ModelManager = Depends(get_model_manager)
) -> HealthResponse:
    """Health check endpoint."""
    try:
        # Check model statuses
        models_status = {
            "importance": ModelStatus(
                loaded=model_manager.importance_service.is_loaded,
                version=getattr(model_manager.importance_service, 'model_version', None),
                last_loaded=getattr(model_manager.importance_service, 'last_loaded', None),
            ).dict(),
            "summarize": ModelStatus(
                loaded=model_manager.summarize_service.is_loaded,
                version=getattr(model_manager.summarize_service, 'model_version', None),
                last_loaded=getattr(model_manager.summarize_service, 'last_loaded', None),
            ).dict(),
            "embed": ModelStatus(
                loaded=model_manager.embed_service.is_loaded,
                version=getattr(model_manager.embed_service, 'model_version', None),
                last_loaded=getattr(model_manager.embed_service, 'last_loaded', None),
            ).dict(),
        }
        
        # Determine overall status
        loaded_models = sum(1 for status in models_status.values() if status["loaded"])
        
        if loaded_models == 3:
            status = "healthy"
        elif loaded_models > 0:
            status = "degraded"
        else:
            status = "unhealthy"
        
        # Add clustering service health
        clustering_health = clustering_service.health_check()
        
        # Add F4 deep learning service health
        deep_learning_health = None
        try:
            deep_service = get_deep_embed_service(model_manager.settings)
            deep_info = await deep_service.get_model_info()
            deep_learning_health = {
                "service": "deep_learning",
                "available": True,
                "loaded_models": deep_info.get("loaded_models", []),
                "default_model": deep_info.get("default_model", "kobert"),
                "device": deep_info.get("device", "cpu"),
                "cuda_available": deep_info.get("cuda_available", False),
                "embedding_counts": deep_info.get("embedding_counts", {})
            }
        except Exception as e:
            deep_learning_health = {
                "service": "deep_learning",
                "available": False,
                "error": str(e)
            }
        
        # Add F4 ONNX optimization health
        onnx_health = None
        try:
            onnx_service = get_onnx_embed_service(model_manager.settings)
            onnx_stats = await onnx_service.get_performance_stats()
            onnx_health = {
                "service": "onnx_optimization",
                "available": True,
                "onnx_runtime_version": onnx_stats.get("onnx_runtime_version", "unknown"),
                "available_providers": onnx_stats.get("available_providers", []),
                "target_p95_ms": onnx_stats.get("target_p95_ms", 50),
                "loaded_models": onnx_stats.get("loaded_models", []),
                "model_stats": onnx_stats.get("model_stats", {})
            }
        except Exception as e:
            onnx_health = {
                "service": "onnx_optimization", 
                "available": False,
                "error": str(e)
            }
        
        # Add F4 caching health
        caching_health = None
        try:
            cache_service = get_embedding_cache_service(model_manager.settings)
            cache_stats = await cache_service.get_cache_statistics()
            caching_health = {
                "service": "embedding_cache",
                "available": True,
                "redis_available": cache_stats["overall"].get("redis_available", False),
                "total_models": cache_stats["overall"].get("total_models", 0),
                "cache_configurations": list(cache_stats["configurations"].keys()),
                "performance_summary": {
                    model: {
                        "hit_rate": stats.get("hit_rate", 0.0),
                        "total_requests": stats.get("total_requests", 0)
                    }
                    for model, stats in cache_stats["by_model"].items()
                }
            }
        except Exception as e:
            caching_health = {
                "service": "embedding_cache",
                "available": False,
                "error": str(e)
            }
        
        response = HealthResponse(
            status=status,
            models=models_status,
            clustering=clustering_health,
            deep_learning=deep_learning_health,
            onnx_optimization=onnx_health,
            caching=caching_health,
            timestamp=time.time()
        )
        
        if status == "unhealthy":
            raise HTTPException(status_code=503, detail=response.dict())
        
        return response
        
    except Exception as e:
        logger.error("Health check failed", exc_info=e)
        raise HTTPException(
            status_code=503,
            detail={
                "status": "unhealthy",
                "error": str(e),
                "timestamp": time.time()
            }
        )


@router.post("/reload")
async def reload_models(
    version: Optional[str] = Query(None, description="Model version to load"),
    model_manager: ModelManager = Depends(get_model_manager)
) -> Dict[str, Any]:
    """Reload ML models."""
    try:
        logger.info("Reloading models", version=version)
        
        # Reload models
        reload_results = await model_manager.reload_models(version)
        
        logger.info("Models reloaded successfully", results=reload_results)
        
        return {
            "status": "success",
            "results": reload_results,
            "timestamp": time.time()
        }
        
    except Exception as e:
        logger.error("Model reload failed", exc_info=e, version=version)
        raise HTTPException(
            status_code=500,
            detail={
                "error": f"Model reload failed: {str(e)}",
                "code": "RELOAD_ERROR",
                "timestamp": time.time()
            }
        )


@router.get("/metrics")
async def get_custom_metrics(
    model_manager: ModelManager = Depends(get_model_manager)
) -> Dict[str, Any]:
    """Get custom application metrics."""
    try:
        return {
            "service": "ml-serving",
            "version": "0.1.0",
            "uptime_seconds": time.time() - model_manager.start_time,
            "models": {
                "importance": {
                    "loaded": model_manager.importance_service.is_loaded,
                    "inference_count": getattr(model_manager.importance_service, 'inference_count', 0),
                },
                "summarize": {
                    "loaded": model_manager.summarize_service.is_loaded,
                    "summary_count": getattr(model_manager.summarize_service, 'summary_count', 0),
                },
                "embed": {
                    "loaded": model_manager.embed_service.is_loaded,
                    "embedding_count": getattr(model_manager.embed_service, 'embedding_count', 0),
                },
            },
            "timestamp": time.time()
        }
    except Exception as e:
        logger.error("Failed to get metrics", exc_info=e)
        raise HTTPException(status_code=500, detail=str(e))