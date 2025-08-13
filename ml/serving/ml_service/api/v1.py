"""V1 API endpoints for ML services."""

import time
from typing import List, Dict, Any, Optional

from fastapi import APIRouter, HTTPException, Request, Depends
from pydantic import BaseModel, Field

from ..core.logging import get_logger
from ..services.model_manager import ModelManager

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