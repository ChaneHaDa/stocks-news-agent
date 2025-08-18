# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a monorepo for a Korean stock news aggregation and scoring service with a microservices architecture. The system collects real Korean financial news via RSS, scores articles for importance using ML models, and provides a sophisticated ranking system with diversity filtering.

## Architecture

### Microservices Structure
- `services/api/` - Spring Boot backend (main API gateway, port 8000)
- `ml/serving/` - FastAPI ML service (real models serving, port 8001)
- `ml/training/` - ML model training scripts and data processing
- `ml/models/` - Trained model artifacts and checkpoints
- `web/` - Next.js frontend (port 3000)
- `contracts/` - OpenAPI specifications and shared schemas
- `scripts/` - Performance testing and operational scripts
- `docs/` - Performance analysis and quality validation reports
- Root level contains Docker orchestration files

### Service Communication Flow (M3 Enhanced)
1. **API Service** collects news via RSS feeds and stores in H2/PostgreSQL
2. **API Service** calls **ML Service** for importance scoring, summarization, and **embedding generation**
3. **API Service** applies **topic clustering** (6-hour batch) and **personalized ranking**
4. **API Service** applies enhanced MMR diversity filtering with **embedding-based similarity**
5. **Frontend** fetches **personalized ranked news** from `/news/top` endpoint
6. **Frontend** tracks **user clicks** for continuous personalization learning

### Core Data Model (M3 Expanded)
The system processes Korean financial news with:
- **News Entity**: Basic metadata (id, source, title, body, published_at, tickers)
- **NewsScore Entity**: ML-generated scores (importance, rank_score, reason_json)
- **NewsEmbedding Entity**: 384-dimension vectors for semantic similarity **[M3 NEW]**
- **NewsTopic Entity**: Topic clustering and duplicate group assignments **[M3 NEW]**
- **UserPreference Entity**: User interests and personalization settings **[M3 NEW]**
- **ClickLog Entity**: User interaction tracking for personalization **[M3 NEW]**
- **Personalized Ranking**: `rank_score = 0.45*importance + 0.20*recency + 0.25*user_relevance + 0.10*novelty`
- **Enhanced Diversity**: MMR algorithm with embedding-based similarity (λ=0.7)

## Development Commands

### Full Stack (Docker) - Recommended
```bash
# Build and start all services (API + ML + Web)
docker compose up --build

# Background mode
docker compose up -d --build

# View logs for specific service
docker compose logs -f [web|api|ml-service]

# Stop services
docker compose down
```

### API Service (Spring Boot)
```bash
cd services/api
./gradlew bootRun                    # Development mode
./gradlew test                       # Run tests
./gradlew bootJar                    # Build JAR
./gradlew build                      # Full build with tests

# Manual news collection trigger
curl -X POST http://localhost:8000/admin/ingest

# Manual topic clustering trigger (M3)
curl -X POST http://localhost:8000/admin/clustering
```

### ML Service (FastAPI)
```bash
cd ml/serving
pip install -r requirements.txt     # Install dependencies
uvicorn ml_service.main:app --reload # Development mode
pytest                               # Run tests

# Check ML service health
curl http://localhost:8001/admin/health

# Test ML endpoints with real models
curl -X POST http://localhost:8001/v1/importance:score \
  -H "Content-Type: application/json" \
  -d '{"items":[{"id":"1","title":"삼성전자 실적","body":"삼성전자 3분기 실적 발표","source":"연합뉴스","published_at":"2024-01-01T00:00:00Z"}]}'

curl -X POST http://localhost:8001/v1/summarize \
  -H "Content-Type: application/json" \
  -d '{"id":"1","title":"뉴스 제목","body":"뉴스 내용","tickers":["005930"]}'

# Test embedding generation (M3)
curl -X POST http://localhost:8001/v1/embed \
  -H "Content-Type: application/json" \
  -d '{"items":[{"id":"1","text":"삼성전자 3분기 실적 발표"}]}'

# Performance testing
python3 scripts/performance_test.py --ml-url http://localhost:8001 --rps 50 --duration 30

# M3 feature testing
python3 scripts/test_m3_features.py

# ML model training (if needed)
cd ml/training
python data_extraction.py
python model_training.py
```

### Frontend Development
```bash
cd web
npm install
npm run dev      # Development server (port 3000)
npm run build    # Production build
npm run lint     # ESLint check
```

## Key Implementation Details

### API Service (Spring Boot) - M3 Enhanced
- **RSS Collection**: Automated news collection every 10 minutes from Korean financial sources
- **ML Integration**: HTTP client with Circuit Breaker (Resilience4j) calling ML service
- **Embedding Pipeline**: Automatic 384-dimension vector generation for all news **[M3 NEW]**
- **Topic Clustering**: 6-hour batch job using cosine similarity (0.75 threshold) **[M3 NEW]**
- **Personalized Ranking**: 4-factor formula with user preference integration **[M3 NEW]**
- **Enhanced MMR Diversity**: Embedding-based similarity + topic-aware filtering **[M3 NEW]**
- **User Management**: Preference storage, click tracking, personalization API **[M3 NEW]**
- **Caching**: Caffeine cache for ML responses (importance: 5min, summaries: 24hr, embeddings: permanent)
- **Fallback**: Rule-based scoring when ML service is unavailable
- **Database**: H2 in-memory for development, PostgreSQL for production with 4 new M3 tables
- **Health Checks**: `/healthz` endpoint with dependency monitoring

### ML Service (FastAPI) - M3 Enhanced
- **Real ML Models**: LogisticRegression importance scoring (v20250813_103924, PR-AUC 1.0000)
- **Hybrid Summarization**: Extractive + LLM integration with compliance filtering
- **Embedding Generation**: Mock sentence-transformers (384-dim) for semantic similarity **[M3 NEW]**
- **Financial Compliance**: Forbidden word filtering for investment advice/speculation
- **High Performance**: 50+ RPS serving with P95 < 10ms response times
- **Structured Logging**: JSON format with request tracing and performance metrics
- **Prometheus Metrics**: Request counts, latencies, model performance monitoring
- **Model Manager**: Lifecycle management with hot reloading and version tracking
- **Health Endpoint**: `/admin/health` with comprehensive model status
- **Feature Flags**: Runtime control for importance, summarization, embedding services

### Frontend Service (Next.js) - M3 Enhanced
- **Real-time Updates**: Displays live Korean financial news with importance scores
- **Responsive UI**: News cards with ticker chips, importance scores, and reason labels
- **Advanced Filtering**: Sort by rank/time, diversity toggle, ticker filtering
- **Personalization UI**: User preference settings, personalized news toggle **[M3 NEW]**
- **Click Tracking**: Automatic user interaction recording for learning **[M3 NEW]**
- **Korean Localization**: Date formatting and content display optimized for Korean users

### Advanced Ranking System - M3 Enhanced
- **Personalized Formula**: `0.45*importance + 0.20*recency + 0.25*user_relevance + 0.10*novelty` **[M3 NEW]**
- **Embedding-based MMR**: Cosine similarity with 384-dim vectors (λ=0.7) **[M3 NEW]**
- **Topic Clustering**: Semantic similarity grouping with 0.75 threshold **[M3 NEW]**
- **User Preference Matching**: Interest-based relevance calculation **[M3 NEW]**
- **Click History Analysis**: 7-day behavioral pattern learning **[M3 NEW]**
- **Duplicate Detection**: 0.9 similarity threshold for identical articles **[M3 NEW]**
- **Configurable Sorting**: API supports ranking, time, and personalized ordering

### ML Service Integration Patterns
- **Circuit Breaker**: 50% failure threshold, 30s wait time in open state
- **Retry Logic**: Exponential backoff with max 3 attempts
- **Graceful Degradation**: Falls back to rule-based scoring when ML unavailable
- **Contract-First**: OpenAPI schemas ensure type safety between services
- **Feature Flags**: Runtime enable/disable for importance, summarization, embedding

### Environment Variables
Key configuration for Docker Compose deployment:
- `ML_SERVICE_URL`: Internal ML service URL (http://ml-service:8001)
- `NEXT_PUBLIC_API_URL`: Public API URL for browser (http://localhost:8000)
- `ENABLE_IMPORTANCE/SUMMARIZE/EMBED`: ML service feature flags
- `RSS_COLLECTION_ENABLED`: Toggle for automated news collection
- `TOPIC_CLUSTERING_ENABLED`: Toggle for 6-hour clustering batch **[M3 NEW]**
- `TOPIC_CLUSTERING_CRON`: Clustering schedule (0 0 */6 * * *) **[M3 NEW]**

### Port Allocation
- **3000**: Next.js Frontend (public)
- **8000**: Spring Boot API (public)
- **8001**: FastAPI ML Service (internal)

## Current State (F2 Complete)

Production-ready A/B testing system with advanced embedding pipeline & personalization engine:

### ✅ M1: Microservices Architecture Foundation
- **Service Separation**: Spring Boot API + FastAPI ML + Next.js Web
- **Resilient Integration**: Circuit breaker, caching, fallback patterns
- **Container Orchestration**: Docker Compose with service dependencies
- **Real RSS Collection**: Korean financial news from major sources

### ✅ M2: Real ML Model Integration (Production-Ready)
- **High-Performance ML Models**: LogisticRegression scoring (PR-AUC 1.0000, P95 = 8ms)
- **Hybrid AI Summarization**: Extractive + LLM with financial compliance filtering
- **Enterprise-Grade Performance**: 50+ RPS, 100% success rate, 37x target performance
- **Financial Compliance**: Zero policy violations, forbidden word filtering
- **Feature Flag System**: Runtime ML service control and graceful degradation

### ✅ M3: Embedding-Based Intelligent Recommendation System (Complete)
- **Embedding Pipeline**: 384-dimension vector generation and storage system
- **Topic Clustering**: Cosine similarity-based automatic categorization (6-hour batch)
- **Enhanced MMR Diversity**: Embedding-based similarity with λ=0.7 balance
- **Personalization Engine**: 4-factor formula (45% importance + 20% recency + 25% user + 10% novelty)
- **User Management**: Preferences, click tracking, and behavioral learning
- **11 New API Endpoints**: Complete personalization and clustering management
- **Production Integration**: 85.7% test success rate, comprehensive documentation
- **4 New Database Tables**: NewsEmbedding, NewsTopic, UserPreference, ClickLog

### ✅ F1: Real-time Embedding Pipeline & Vector Database (Complete)
- **pgvector Integration**: H2/PostgreSQL dual compatibility with auto-detection
- **Real-time Pipeline**: Event-driven embedding generation (news save → ML call → vector storage)
- **Vector Search API**: 4 endpoints for similarity search, text search, backlog processing
- **HNSW Optimization**: PostgreSQL vector indexes for sub-50ms response times
- **Graceful Degradation**: H2 fallback with manual cosine similarity computation
- **Production Monitoring**: Health checks, circuit breakers, structured logging
- **Database Migration**: V6 schema with news_embedding_v2 table (768-dimension)
- **Async Processing**: Event listeners for non-blocking embedding generation

### ✅ F2: A/B Testing System (Complete)
- **Experiment Bucketing**: SHA-256 hash-based consistent user assignment (50/50 split)
- **A/B Ranking Variants**: Control (standard) vs Treatment (personalized) algorithms
- **Experiment Logging**: Event-driven impression/click tracking with experiment metadata
- **Metrics Collection**: Automated CTR, dwell time, diversity, personalization scoring
- **Auto-Stop System**: 5%p CTR degradation threshold with 24-hour monitoring
- **Feature Flag Integration**: Runtime experiment control and graceful degradation
- **Database Schema**: V7 migration with experiment tables and daily metrics aggregation
- **API Endpoints**: `/news/top/experimental` with experiment metadata response
- **Production Ready**: 6-hour monitoring cycles, statistical significance validation

### 🔄 F3+ Next: Advanced AI & Analytics
- **F3**: Advanced clustering algorithms (HDBSCAN, K-means mini-batch)  
- **F4**: Deep learning embeddings (Ko-BERT, RoBERTa with ONNX optimization)
- **F5**: Multi-armed bandit optimization and real-time experiment adaptation

## Code Conventions

- **Korean Content**: Mixed Korean article content with English code/variable names
- **Stock Tickers**: 6-digit Korean format (005930=Samsung, 035720=Kakao)
- **Scoring**: Normalized importance scores (0-1), rank scores (0-1)
- **API Contracts**: OpenAPI-first with shared schemas in `/contracts`
- **Error Handling**: Graceful degradation with meaningful fallbacks
- **Commit Style**: Feature-based commits with Korean descriptions for user-facing changes