# F1 API Reference: Vector Search & Embedding Pipeline

**Version**: F1 Complete  
**Base URL**: `http://localhost:8000`  
**API Prefix**: `/api/v1/vector`

## 📡 API Endpoints Overview

| Method | Endpoint | Description | Status |
|--------|----------|-------------|---------|
| GET | `/vector/health` | Vector search health check | ✅ Active |
| GET | `/vector/similar/{newsId}` | Find similar news by ID | ✅ Active |  
| POST | `/vector/similar/text` | Find similar news by text query | ✅ Active |
| POST | `/vector/embeddings/backlog` | Process embedding backlog | ✅ Active |

---

## 🔍 Vector Similarity Search

### GET /vector/similar/{newsId}

Find news articles similar to a given news ID using vector embeddings.

#### Parameters
- **Path Parameters**:
  - `newsId` (required): Target news article ID (Long)
- **Query Parameters**:
  - `limit` (optional): Maximum results to return (1-100, default: 10)

#### Example Request
```bash
curl -X GET "http://localhost:8000/api/v1/vector/similar/123?limit=5" \
  -H "Accept: application/json"
```

#### Example Response
```json
[
  {
    "id": "456",
    "source": "연합뉴스", 
    "title": "삼성전자 3분기 실적 발표",
    "url": "https://example.com/news/456",
    "publishedAt": "2024-01-15T10:30:00Z",
    "tickers": ["005930"],
    "summary": "삼성전자가 3분기 실적을 발표했습니다...",
    "importance": 0.85,
    "reason": {
      "category": "earnings",
      "keywords": ["실적", "발표", "삼성전자"]
    }
  }
]
```

#### Response Codes
- `200 OK`: Similar articles found
- `400 Bad Request`: Invalid limit parameter  
- `404 Not Found`: News ID not found or no embedding available
- `500 Internal Server Error`: Vector search failed

#### Performance
- **Target Response Time**: < 50ms (with pgvector HNSW index)
- **Similarity Threshold**: > 0.3 (cosine similarity)
- **Database Support**: PostgreSQL (pgvector) / H2 (manual cosine)

---

## 📝 Text-Based Similarity Search

### POST /vector/similar/text

Find news articles similar to a provided text query using semantic embeddings.

#### Request Body
```json
{
  "query": "삼성전자 실적 발표",
  "limit": 10
}
```

#### Parameters
- **Body Parameters**:
  - `query` (required): Text query for semantic search (String)
  - `limit` (optional): Maximum results to return (1-100, default: 10)

#### Example Request
```bash
curl -X POST "http://localhost:8000/api/v1/vector/similar/text" \
  -H "Content-Type: application/json" \
  -d '{
    "query": "카카오 주가 상승", 
    "limit": 3
  }'
```

#### Example Response
```json
[
  {
    "id": "789",
    "source": "한국경제",
    "title": "카카오 주가 5% 상승세",
    "url": "https://example.com/news/789", 
    "publishedAt": "2024-01-15T14:20:00Z",
    "tickers": ["035720"],
    "summary": "카카오 주가가 장중 5% 상승했습니다...",
    "importance": 0.78,
    "reason": {
      "category": "market_movement",
      "keywords": ["주가", "상승", "카카오"]
    }
  }
]
```

#### Response Codes
- `200 OK`: Text search completed (may return empty array)
- `400 Bad Request`: Empty or invalid query
- `500 Internal Server Error`: Text embedding failed

#### Implementation Status
- **Current**: Endpoint defined, returns empty results
- **Future**: Requires ML service integration for query embedding

---

## ⚙️ Embedding Management

### POST /vector/embeddings/backlog

Process embedding backlog for news articles without embeddings.

#### Parameters
- **Query Parameters**:
  - `batchSize` (optional): Batch size for processing (1-200, default: 50)

#### Example Request
```bash
curl -X POST "http://localhost:8000/api/v1/vector/embeddings/backlog?batchSize=100" \
  -H "Content-Type: application/json"
```

#### Example Response
```json
{
  "processed": 45,
  "batchSize": 100,
  "status": "success"
}
```

#### Response Fields
- `processed`: Number of embeddings successfully generated
- `batchSize`: Requested batch size
- `status`: Operation status (`success`, `no_work`, `error`)

#### Response Codes
- `200 OK`: Backlog processing completed
- `400 Bad Request`: Invalid batch size
- `500 Internal Server Error`: Batch processing failed

#### Performance
- **Batch Processing**: 32-64 items optimal for ML service
- **ML Service Call**: Circuit breaker protected
- **Throughput**: ~5k embeddings/minute target

---

## 🔍 Health Check

### GET /vector/health

Check the health and status of vector search capabilities.

#### Example Request
```bash
curl -X GET "http://localhost:8000/api/v1/vector/health" \
  -H "Accept: application/json"
```

#### Example Response (Healthy)
```json
{
  "status": "healthy",
  "service": "vector-search", 
  "capabilities": ["similarity_search", "embedding_generation"],
  "timestamp": 1705392000000
}
```

#### Example Response (Unhealthy)
```json
{
  "status": "unhealthy",
  "service": "vector-search",
  "error": "ML service unavailable", 
  "timestamp": 1705392000000
}
```

#### Response Codes
- `200 OK`: Service is healthy
- `503 Service Unavailable`: Service is unhealthy

---

## 🔧 Technical Implementation

### Database Architecture

#### H2 Database (Development)
- **Storage**: JSON arrays in TEXT columns
- **Search**: Manual cosine similarity calculation  
- **Performance**: O(n) linear scan
- **Index**: Standard B-tree on news_id, created_at

#### PostgreSQL Database (Production)
- **Storage**: pgvector VECTOR(768) + JSON fallback
- **Search**: HNSW approximate nearest neighbor
- **Performance**: O(log n) with HNSW index
- **Index**: `vector_cosine_ops`, `vector_l2_ops`

### Vector Processing Pipeline

1. **News Saved Event** → `NewsEvent.NewsSaved`
2. **Async Processing** → `EmbeddingEventListener.handleNewsSaved()`
3. **ML Service Call** → `POST /v1/embed` (batch 32-64 items)
4. **Vector Storage** → `news_embedding_v2` table
5. **Index Update** → Automatic HNSW rebalancing

### Error Handling

#### Circuit Breaker Pattern
- **Failure Threshold**: 50% over 30 seconds
- **Open State Duration**: 30 seconds
- **Fallback**: Return empty results, log warning

#### Retry Logic
- **Max Attempts**: 3
- **Backoff**: Exponential (1s, 2s, 4s)
- **Retry Conditions**: Network timeout, 5xx errors

---

## 📊 Performance Characteristics

### Latency Targets
- **Vector Similarity**: < 50ms (P95)
- **Embedding Generation**: < 5 seconds (batch)
- **Health Check**: < 100ms

### Throughput Capacity
- **Similarity Queries**: 1000+ QPS (PostgreSQL)
- **Embedding Generation**: 5k+ items/minute
- **Concurrent Users**: 100+ simultaneous

### Resource Requirements
- **Memory**: ~2GB for 100k embeddings (768-dim)
- **Storage**: ~300MB per 100k embeddings
- **CPU**: 2 cores minimum for H2, 4+ for PostgreSQL

---

## 🛠️ Configuration

### Feature Flags
```bash
# Enable real-time embedding generation
FEATURE_REALTIME_EMBEDDING_ENABLED=true

# Enable embedding regeneration on content updates  
FEATURE_EMBEDDING_REGENERATION_ENABLED=false
```

### Database Settings
```bash
# Auto-detect database type (recommended)
APP_DATABASE_TYPE=auto

# Explicit database type
APP_DATABASE_TYPE=postgresql  # or h2
```

### ML Service Integration
```bash
# ML service endpoint
ML_SERVICE_URL=http://localhost:8001

# Circuit breaker settings
ML_CIRCUIT_BREAKER_FAILURE_THRESHOLD=50
ML_CIRCUIT_BREAKER_WAIT_DURATION=30000
```

---

## 📈 Monitoring & Metrics

### Key Metrics
- `vector_search_requests_total`: Total similarity search requests
- `embedding_generation_duration_seconds`: Time to generate embeddings
- `ml_service_calls_total`: Calls to ML service (success/failure)
- `vector_search_latency_seconds`: P50/P95/P99 response times

### Log Events
```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "level": "INFO", 
  "service": "vector-search",
  "operation": "similarity_search",
  "news_id": 123,
  "similar_count": 5,
  "duration_ms": 45,
  "database_type": "postgresql"
}
```

### Health Indicators
- ML service connectivity
- Database connection pool health
- Vector index status (PostgreSQL only)
- Embedding generation success rate

---

## 🔄 Migration & Deployment

### Database Migration
- **V6__migrate_to_pgvector.sql**: Creates `news_embedding_v2` table
- **Auto-detection**: Runs appropriate setup for H2/PostgreSQL
- **Index Creation**: HNSW indexes created at startup (PostgreSQL only)

### Rolling Deployment
1. Deploy new API version with feature flags disabled
2. Run database migration
3. Enable real-time embedding generation
4. Monitor health and performance metrics
5. Enable embedding regeneration if needed

### Rollback Procedure
1. Disable feature flags via environment variables
2. Restart application (falls back to basic ranking)
3. Revert database migration if necessary
4. Monitor system stability

---

## ❗ Error Codes & Troubleshooting

### Common Error Scenarios

#### `404 Not Found` - No embedding available
```json
{
  "error": "No embedding found for news ID: 123",
  "suggestion": "Run backlog processing or wait for embedding generation"
}
```

#### `503 Service Unavailable` - ML service down
```json
{
  "error": "Vector search temporarily unavailable", 
  "fallback": "Using basic ranking algorithm"
}
```

#### `400 Bad Request` - Invalid parameters
```json
{
  "error": "Limit must be between 1 and 100",
  "provided": 150
}
```

### Troubleshooting Guide

1. **No similar results returned**:
   - Check if embeddings exist for the news ID
   - Verify similarity threshold (0.3+) 
   - Run embedding backlog processing

2. **Slow response times**:
   - Check database type (PostgreSQL faster than H2)
   - Verify HNSW indexes are created
   - Monitor ML service performance

3. **Embedding generation failures**:
   - Check ML service connectivity
   - Verify news text content is not empty
   - Review circuit breaker status

---

**Documentation Version**: F1 Complete  
**Last Updated**: 2024-01-18  
**API Stability**: Stable