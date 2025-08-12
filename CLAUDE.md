# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a monorepo for a Korean stock news aggregation and scoring service with a Spring Boot backend and Next.js frontend. The system currently serves mock data and is designed to eventually collect real news, generate AI summaries, and score articles for importance.

## Architecture

### Monorepo Structure
- `services/api/` - Spring Boot backend serving news data
- `web/` - Next.js frontend displaying news cards
- `contracts/` - OpenAPI specifications and JSON schemas
- Root level contains Docker orchestration files

### Data Flow
1. Frontend fetches news from `/news/top` endpoint
2. API returns structured NewsItem objects with importance scoring
3. Frontend renders news cards with ticker chips and reason labels

### Core Data Model
The `NewsItem` schema defines the central data structure:
- Basic metadata: id, source, title, url, published_at
- Stock relevance: tickers array (6-digit Korean stock codes)
- AI analysis: summary (AI-generated), importance (0-1 score)
- Scoring breakdown: reason object with source_weight, tickers_hit, keywords_hit, freshness

## Development Commands

### Full Stack (Docker)
```bash
# Build and start all services
docker compose up --build

# Background mode
docker compose up -d --build

# View logs
docker compose logs -f [web|api]

# Stop services
docker compose down
```

### API Development
```bash
cd services/api
./gradlew bootJar                    # Build
java -jar build/libs/news-agent-api.jar    # Run

# Or for development
./gradlew bootRun
```

### Frontend Development
```bash
cd web
npm install
npm run dev      # Development server
npm run build    # Production build
npm run lint     # ESLint check
```

## Key Implementation Details

### API Service
- Spring Boot with CORS enabled for development
- Health check endpoint at `/healthz` 
- OpenAPI documentation at `/docs` (Swagger UI)
- Mock data returns 5 sample news items with Korean content
- Uses Korean stock ticker format (6-digit codes like 005930 for Samsung)
- Importance scoring uses multiple factors combined into reason object
- Gradle-based build system with multi-stage Docker builds

### Frontend Service
- Next.js 14 with TypeScript and Tailwind CSS
- Single page application displaying news cards
- Fetches API URL from `NEXT_PUBLIC_API_URL` environment variable
- Displays importance scores, stock tickers, and top 3 scoring reasons
- Korean localized date formatting

### Environment Variables
- `NEXT_PUBLIC_API_URL`: API endpoint URL for frontend (default: http://localhost:8000)
- `NODE_ENV`: Node.js environment mode
- `JAVA_OPTS`: JVM options for API service (e.g., -Xmx512m -Xms256m)

### Docker Configuration
- API service includes health check using wget
- Web service depends on API health check before starting
- Services communicate via Docker bridge network
- API accessible on port 8000, frontend on port 3000

## Current State

This is a Day 1 prototype with:
- ✅ Working API with mock endpoints
- ✅ Functional UI displaying news cards  
- ✅ Docker containerization
- ✅ OpenAPI specification
- 🔄 Ready for real news pipeline integration
- 🔄 Ready for AI summarization system
- 🔄 Ready for database integration

## Code Conventions

- API uses Spring Boot with DTOs and proper REST controllers
- Frontend uses TypeScript strict mode with proper interfaces
- Korean content mixed with English variable names
- Stock tickers use Korean format (6-digit codes)
- Importance scores are floats between 0-1
- Timestamps use ISO 8601 format with UTC timezone