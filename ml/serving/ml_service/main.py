"""Main FastAPI application for ML service."""

import logging
import time
from contextlib import asynccontextmanager
from typing import AsyncGenerator

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from prometheus_client import generate_latest, CONTENT_TYPE_LATEST
import structlog

from .api import admin, v1
from .core.config import get_settings
from .core.logging import setup_logging
from .core.metrics import setup_metrics, REQUEST_COUNT, REQUEST_DURATION
from .services.model_manager import ModelManager

# Setup logging
setup_logging()
logger = structlog.get_logger()

# Global model manager instance
model_manager: ModelManager = None


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncGenerator[None, None]:
    """Application lifespan manager."""
    global model_manager
    
    logger.info("Starting ML service")
    settings = get_settings()
    
    # Initialize model manager
    model_manager = ModelManager(settings)
    app.state.model_manager = model_manager
    
    # Setup metrics
    setup_metrics()
    
    logger.info("ML service started successfully")
    yield
    
    logger.info("Shutting down ML service")
    if model_manager:
        await model_manager.cleanup()


# Create FastAPI app
app = FastAPI(
    title="ML Service API",
    description="Machine Learning services for news importance, summarization, and embedding",
    version="0.1.0",
    lifespan=lifespan,
)

# Add CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # Configure appropriately for production
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.middleware("http")
async def add_process_time_header(request: Request, call_next):
    """Add request processing time and metrics."""
    start_time = time.time()
    
    # Process request
    response = await call_next(request)
    
    # Calculate duration
    process_time = time.time() - start_time
    response.headers["X-Process-Time"] = str(process_time)
    
    # Update metrics
    REQUEST_COUNT.labels(
        method=request.method,
        endpoint=request.url.path,
        status=response.status_code
    ).inc()
    
    REQUEST_DURATION.labels(
        method=request.method,
        endpoint=request.url.path
    ).observe(process_time)
    
    return response


@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    """Global exception handler."""
    logger.error("Unhandled exception", exc_info=exc, path=request.url.path)
    return JSONResponse(
        status_code=500,
        content={
            "error": "Internal server error",
            "code": "INTERNAL_ERROR",
            "timestamp": time.time()
        }
    )


# Include routers
app.include_router(admin.router, prefix="/admin", tags=["admin"])
app.include_router(v1.router, prefix="/v1", tags=["v1"])


@app.get("/metrics")
async def metrics():
    """Prometheus metrics endpoint."""
    return generate_latest().decode("utf-8")


@app.get("/")
async def root():
    """Root endpoint."""
    return {
        "service": "ML Service",
        "version": "0.1.0",
        "status": "running",
        "timestamp": time.time()
    }