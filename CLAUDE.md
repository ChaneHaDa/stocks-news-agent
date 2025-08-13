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

### Service Communication Flow
1. **API Service** collects news via RSS feeds and stores in H2/PostgreSQL
2. **API Service** calls **ML Service** for importance scoring and summarization
3. **API Service** applies advanced ranking with MMR diversity filtering
4. **Frontend** fetches ranked news from `/news/top` endpoint
5. **Frontend** renders news cards with importance scores and reason chips

### Core Data Model
The system processes Korean financial news with:
- **News Entity**: Basic metadata (id, source, title, body, published_at, tickers)
- **NewsScore Entity**: ML-generated scores (importance, rank_score, reason_json)
- **Advanced Ranking**: `rank_score = 0.45*importance + 0.25*recency + 0.25*relevance + 0.05*novelty`
- **Diversity Filtering**: MMR algorithm limiting duplicate topics to ≤2 items per cluster

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

# Performance testing
python3 scripts/performance_test.py --ml-url http://localhost:8001 --rps 50 --duration 30

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

### API Service (Spring Boot)
- **RSS Collection**: Automated news collection every 10 minutes from Korean financial sources
- **ML Integration**: HTTP client with Circuit Breaker (Resilience4j) calling ML service
- **Advanced Ranking**: 4-component formula with MMR diversity filtering via `DiversityService`
- **Caching**: Caffeine cache for ML responses (importance: 5min, summaries: 24hr)
- **Fallback**: Rule-based scoring when ML service is unavailable
- **Database**: H2 in-memory for development, PostgreSQL for production
- **Health Checks**: `/healthz` endpoint with dependency monitoring

### ML Service (FastAPI)
- **Real ML Models**: LogisticRegression importance scoring (v20250813_103924, PR-AUC 1.0000)
- **Hybrid Summarization**: Extractive + LLM integration with compliance filtering
- **Financial Compliance**: Forbidden word filtering for investment advice/speculation
- **High Performance**: 50+ RPS serving with P95 < 10ms response times
- **Structured Logging**: JSON format with request tracing and performance metrics
- **Prometheus Metrics**: Request counts, latencies, model performance monitoring
- **Model Manager**: Lifecycle management with hot reloading and version tracking
- **Health Endpoint**: `/admin/health` with comprehensive model status
- **Feature Flags**: Runtime control for importance, summarization, embedding services

### Frontend Service (Next.js)
- **Real-time Updates**: Displays live Korean financial news with importance scores
- **Responsive UI**: News cards with ticker chips, importance scores, and reason labels
- **Advanced Filtering**: Sort by rank/time, diversity toggle, ticker filtering
- **Korean Localization**: Date formatting and content display optimized for Korean users

### Advanced Ranking System
- **Enhanced Formula**: Combines importance, recency, relevance, and novelty factors
- **MMR Diversity**: Maximal Marginal Relevance algorithm (λ=0.7) prevents topic clustering
- **Similarity Detection**: Jaccard coefficient with Korean stop-word filtering
- **Topic Clustering**: Groups similar articles, limits exposure to 2 per cluster
- **Configurable Sorting**: API supports both ranking and chronological ordering

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

### Port Allocation
- **3000**: Next.js Frontend (public)
- **8000**: Spring Boot API (public)
- **8001**: FastAPI ML Service (internal)

## Current State (M2 Complete)

Production-ready ML-powered news platform with:

### ✅ M1: Microservices Architecture Foundation
- **Service Separation**: Spring Boot API + FastAPI ML + Next.js Web
- **Advanced Ranking**: 4-component scoring with MMR diversity filtering
- **Resilient Integration**: Circuit breaker, caching, fallback patterns
- **Container Orchestration**: Docker Compose with service dependencies
- **Real RSS Collection**: Korean financial news from major sources

### ✅ M2: Real ML Model Integration (Production-Ready)
- **High-Performance ML Models**: LogisticRegression scoring (PR-AUC 1.0000, P95 = 8ms)
- **Hybrid AI Summarization**: Extractive + LLM with financial compliance filtering
- **Enterprise-Grade Performance**: 50+ RPS, 100% success rate, 37x target performance
- **Financial Compliance**: Zero policy violations, forbidden word filtering
- **Feature Flag System**: Runtime ML service control and graceful degradation
- **Comprehensive Monitoring**: Performance analytics, quality validation

### 🔄 M3 Next: Advanced Ranking Integration
- MMR diversity filtering optimization and performance tuning
- Enhanced topic clustering with improved accuracy (≤2 items/cluster)
- Real-time ranking update system with cache invalidation
- Advanced similarity detection with semantic embeddings

## Code Conventions

- **Korean Content**: Mixed Korean article content with English code/variable names
- **Stock Tickers**: 6-digit Korean format (005930=Samsung, 035720=Kakao)
- **Scoring**: Normalized importance scores (0-1), rank scores (0-1)
- **API Contracts**: OpenAPI-first with shared schemas in `/contracts`
- **Error Handling**: Graceful degradation with meaningful fallbacks
- **Commit Style**: Feature-based commits with Korean descriptions for user-facing changes