"""Prometheus metrics for ML service."""

from prometheus_client import Counter, Histogram, Gauge, Info

# Request metrics
REQUEST_COUNT = Counter(
    'ml_requests_total',
    'Total number of requests',
    ['method', 'endpoint', 'status']
)

REQUEST_DURATION = Histogram(
    'ml_request_duration_seconds',
    'Request duration in seconds',
    ['method', 'endpoint']
)

# Model metrics
MODEL_INFERENCE_DURATION = Histogram(
    'ml_model_inference_duration_seconds',
    'Model inference duration in seconds',
    ['model_type', 'model_version']
)

MODEL_INFERENCE_COUNT = Counter(
    'ml_model_inferences_total',
    'Total number of model inferences',
    ['model_type', 'model_version', 'status']
)

MODEL_LOAD_TIME = Gauge(
    'ml_model_load_time_seconds',
    'Time taken to load model in seconds',
    ['model_type', 'model_version']
)

MODEL_MEMORY_USAGE = Gauge(
    'ml_model_memory_usage_bytes',
    'Memory usage of loaded models in bytes',
    ['model_type', 'model_version']
)

# Cache metrics
CACHE_HIT_COUNT = Counter(
    'ml_cache_hits_total',
    'Total number of cache hits',
    ['cache_type']
)

CACHE_MISS_COUNT = Counter(
    'ml_cache_misses_total',
    'Total number of cache misses',
    ['cache_type']
)

# Error metrics
ERROR_COUNT = Counter(
    'ml_errors_total',
    'Total number of errors',
    ['error_type', 'model_type']
)

# Feature extraction metrics
FEATURE_EXTRACTION_DURATION = Histogram(
    'ml_feature_extraction_duration_seconds',
    'Feature extraction duration in seconds',
    ['feature_type']
)

# Batch processing metrics
BATCH_SIZE = Histogram(
    'ml_batch_size',
    'Batch size for processing',
    ['operation_type']
)

BATCH_PROCESSING_DURATION = Histogram(
    'ml_batch_processing_duration_seconds',
    'Batch processing duration in seconds',
    ['operation_type', 'batch_size_bucket']
)

# Service info
SERVICE_INFO = Info(
    'ml_service_info',
    'Information about the ML service'
)


def setup_metrics():
    """Initialize metrics with service information."""
    SERVICE_INFO.info({
        'version': '0.1.0',
        'service': 'ml-serving'
    })