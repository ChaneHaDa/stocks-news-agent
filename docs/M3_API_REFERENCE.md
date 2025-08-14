# M3 API 레퍼런스

## 📋 개요

M3 마일스톤에서 추가된 API 엔드포인트들과 기존 API의 확장 기능을 상세히 설명합니다.

---

## 🎯 Base URLs

- **API Service**: `http://localhost:8000`
- **ML Service**: `http://localhost:8001`
- **Web Frontend**: `http://localhost:3000`

---

## 📰 News API (확장)

### GET /news/top - 개인화 뉴스 조회

M3에서 개인화 기능이 추가된 메인 뉴스 조회 API입니다.

#### Request
```http
GET /news/top?n=20&diversity=true&personalized=true&userId=user123
```

#### Parameters
| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `n` | integer | 20 | 반환할 뉴스 개수 (최대 100) |
| `tickers` | string | - | 필터링할 종목코드 (쉼표 구분) |
| `lang` | string | "ko" | 언어 설정 |
| `hours` | integer | 168 | 조회 기간 (시간) |
| `sort` | string | "rank" | 정렬 방식 ("rank", "time") |
| `diversity` | boolean | true | 다양성 필터링 적용 여부 |
| `personalized` | boolean | false | **[M3 신규]** 개인화 랭킹 적용 여부 |
| `userId` | string | - | **[M3 신규]** 사용자 ID (개인화 시 필수) |

#### Response
```json
{
  "items": [
    {
      "id": "1",
      "source": "연합뉴스",
      "title": "삼성전자 3분기 실적 발표",
      "url": "https://example.com/news/1",
      "publishedAt": "2024-01-14T10:00:00Z",
      "tickers": ["005930"],
      "summary": "삼성전자가 3분기 실적을 발표했습니다...",
      "importance": 0.85,
      "reason": {
        "sourceWeight": 0.2,
        "tickersHit": 0.3,
        "keywordsHit": 0.25,
        "freshness": 0.1
      }
    }
  ],
  "metadata": null
}
```

#### 개인화 동작 방식
1. `personalized=false`: 기존 방식 (중요도 + 최신성 기반)
2. `personalized=true`: 4요소 개인화 공식 적용
   - 45% 중요도 + 20% 최신성 + 25% 사용자 관련성 + 10% 새로움

### POST /news/{id}/click - 클릭 이벤트 기록

**[M3 신규]** 사용자의 뉴스 클릭을 기록하여 개인화 학습에 활용합니다.

#### Request
```http
POST /news/123/click?userId=user123&position=1&importance=0.85
```

#### Parameters
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `userId` | string | ✅ | 사용자 ID |
| `sessionId` | string | ❌ | 세션 ID |
| `position` | integer | ❌ | 클릭 시점의 랭킹 위치 |
| `importance` | number | ❌ | 클릭 시점의 중요도 점수 |

#### Headers
- `User-Agent`: 브라우저 정보 (자동 수집)
- `X-Forwarded-For`: 클라이언트 IP (자동 수집)

#### Response
```http
200 OK
```

---

## 👤 User API (신규)

### GET /users/{userId}/preferences - 사용자 환경설정 조회

#### Request
```http
GET /users/user123/preferences
```

#### Response
```json
{
  "id": 1,
  "userId": "user123",
  "interestedTickers": "[\"005930\", \"035720\"]",
  "interestedKeywords": "[\"반도체\", \"AI\", \"전기차\"]",
  "diversityWeight": 0.7,
  "personalizationEnabled": true,
  "isActive": true,
  "createdAt": "2024-01-14T10:00:00Z",
  "updatedAt": "2024-01-14T11:00:00Z"
}
```

### PUT /users/{userId}/preferences - 사용자 환경설정 업데이트

#### Request
```http
PUT /users/user123/preferences
Content-Type: application/json

{
  "interestedTickers": ["005930", "035720", "000660"],
  "interestedKeywords": ["반도체", "AI", "자동차"],
  "diversityWeight": 0.8,
  "personalizationEnabled": true
}
```

#### Response
```json
{
  "id": 1,
  "userId": "user123",
  "interestedTickers": "[\"005930\", \"035720\", \"000660\"]",
  "interestedKeywords": "[\"반도체\", \"AI\", \"자동차\"]",
  "diversityWeight": 0.8,
  "personalizationEnabled": true,
  "isActive": true,
  "updatedAt": "2024-01-14T12:00:00Z"
}
```

### PUT /users/{userId}/preferences/tickers - 관심 종목 업데이트

#### Request
```http
PUT /users/user123/preferences/tickers
Content-Type: application/json

["005930", "035720", "051910"]
```

### PUT /users/{userId}/preferences/keywords - 관심 키워드 업데이트

#### Request
```http
PUT /users/user123/preferences/keywords
Content-Type: application/json

["반도체", "AI", "메타버스", "ESG"]
```

### PUT /users/{userId}/preferences/personalization - 개인화 활성화/비활성화

#### Request
```http
PUT /users/user123/preferences/personalization?enabled=true
```

### PUT /users/{userId}/preferences/diversity - 다양성 가중치 조정

#### Request
```http
PUT /users/user123/preferences/diversity?weight=0.8
```

#### Parameters
- `weight`: 0.0-1.0 범위의 MMR λ 파라미터
  - 0.0 = 100% 다양성 우선
  - 1.0 = 100% 관련성 우선
  - 기본값 = 0.7

---

## 🔧 Admin API (확장)

### POST /admin/clustering - 토픽 클러스터링 트리거

**[M3 신규]** 수동으로 토픽 클러스터링을 실행합니다.

#### Request
```http
POST /admin/clustering
```

#### Response
```json
{
  "success": true,
  "message": "Topic clustering completed",
  "startTime": "2024-01-14T10:00:00Z",
  "endTime": "2024-01-14T10:00:05Z",
  "durationMs": 5234,
  "totalArticles": 150,
  "articlesWithEmbeddings": 142,
  "clustersGenerated": 25,
  "duplicateGroupsFound": 8,
  "topicsAssigned": 142
}
```

### GET /admin/status - 시스템 상태 (확장)

기존 API에 ML 서비스 상태가 추가되었습니다.

#### Response
```json
{
  "timestamp": "2024-01-14T10:00:00Z",
  "service": "news-agent-api",
  "status": "running",
  "features": {
    "rss_collection": true,
    "scoring": true,
    "scheduling": true
  },
  "ml_service": {
    "healthy": true,
    "base_url": "http://ml-service:8001",
    "features": {
      "importance": true,
      "summarize": true,
      "embed": true
    }
  }
}
```

### POST /admin/features/embed - 임베딩 기능 토글

**[M3 신규]** 임베딩 생성 기능을 활성화/비활성화합니다.

#### Request
```http
POST /admin/features/embed?enabled=true
```

#### Response
```json
{
  "feature": "embed",
  "enabled": true,
  "timestamp": "2024-01-14T10:00:00Z",
  "message": "Feature enabled"
}
```

---

## 🤖 ML Service API

### POST /v1/embed - 텍스트 임베딩 생성

**[M3 신규]** 텍스트를 384차원 벡터로 변환합니다.

#### Request
```http
POST /v1/embed
Content-Type: application/json

{
  "items": [
    {
      "id": "1",
      "text": "삼성전자 3분기 실적 발표"
    },
    {
      "id": "2", 
      "text": "네이버 클라우드 서비스 확장"
    }
  ]
}
```

#### Response
```json
{
  "results": [
    {
      "id": "1",
      "vector": [0.1, -0.2, 0.3, ...],  // 384차원 배열
      "norm": 1.0
    },
    {
      "id": "2",
      "vector": [-0.1, 0.4, -0.2, ...],
      "norm": 1.0
    }
  ],
  "model_version": "mock-embed-v1.0.0",
  "dimension": 384,
  "processed_at": 1705230000.0
}
```

#### 제한사항
- 최대 배치 크기: 50개
- 최대 텍스트 길이: 2000자
- 타임아웃: 60초

### GET /admin/health - ML 서비스 헬스체크

#### Response
```json
{
  "status": "healthy",
  "models": {
    "importance": {
      "loaded": true,
      "version": "fallback-rules-v1.0.0",
      "last_loaded": 1705230000.0,
      "memory_usage": null
    },
    "summarize": {
      "loaded": true,
      "version": "hybrid-v2.0.0", 
      "last_loaded": 1705230000.0,
      "memory_usage": null
    },
    "embed": {
      "loaded": true,
      "version": "mock-embed-v1.0.0",
      "last_loaded": 1705230000.0,
      "memory_usage": null
    }
  },
  "timestamp": 1705230000.0
}
```

---

## 🔍 사용 예시

### 완전한 개인화 워크플로우

```bash
# 1. 사용자 환경설정 생성
curl -X PUT "http://localhost:8000/users/user123/preferences/personalization?enabled=true"

# 2. 관심 종목 설정
curl -X PUT "http://localhost:8000/users/user123/preferences/tickers" \
  -H "Content-Type: application/json" \
  -d '["005930", "035720"]'

# 3. 개인화된 뉴스 조회
curl "http://localhost:8000/news/top?n=10&personalized=true&userId=user123"

# 4. 클릭 이벤트 기록
curl -X POST "http://localhost:8000/news/1/click?userId=user123&position=1"

# 5. 다시 개인화된 뉴스 조회 (학습 반영)
curl "http://localhost:8000/news/top?n=10&personalized=true&userId=user123"
```

### 관리자 운영 작업

```bash
# 시스템 상태 확인
curl "http://localhost:8000/admin/status"

# 뉴스 수집 트리거
curl -X POST "http://localhost:8000/admin/ingest"

# 토픽 클러스터링 실행
curl -X POST "http://localhost:8000/admin/clustering"

# 임베딩 기능 활성화
curl -X POST "http://localhost:8000/admin/features/embed?enabled=true"
```

### A/B 테스트 시나리오

```bash
# Control Group: 기본 랭킹
curl "http://localhost:8000/news/top?n=20&diversity=true&personalized=false"

# Test Group: 개인화 랭킹  
curl "http://localhost:8000/news/top?n=20&diversity=true&personalized=true&userId=user123"

# 클릭률 비교를 위한 클릭 추적
curl -X POST "http://localhost:8000/news/{id}/click?userId=user123&position=1"
```

---

## ⚠️ 오류 코드

### 공통 오류

| Code | Message | Description |
|------|---------|-------------|
| 400 | Bad Request | 잘못된 요청 파라미터 |
| 404 | Not Found | 리소스를 찾을 수 없음 |
| 500 | Internal Server Error | 서버 내부 오류 |

### 개인화 관련 오류

| Code | Message | Description |
|------|---------|-------------|
| 400 | User ID required for personalization | 개인화 요청 시 사용자 ID 누락 |
| 400 | Invalid diversity weight | 다양성 가중치 범위 오류 (0.0-1.0) |

### ML 서비스 오류

| Code | Message | Description |
|------|---------|-------------|
| 503 | Embedding model not loaded | 임베딩 모델 미로드 |
| 413 | Batch size too large | 배치 크기 초과 (최대 50개) |
| 413 | Text too long | 텍스트 길이 초과 (최대 2000자) |

---

## 📊 성능 가이드라인

### API 응답 시간 목표

| Endpoint | Target Response Time |
|----------|----------------------|
| `GET /news/top` | < 500ms |
| `POST /news/{id}/click` | < 100ms |
| `GET /users/{id}/preferences` | < 200ms |
| `PUT /users/{id}/preferences` | < 300ms |
| `POST /admin/clustering` | < 30s |
| `POST /v1/embed` | < 1s (단건), < 5s (배치) |

### Rate Limiting

| Endpoint | Limit |
|----------|-------|
| `GET /news/top` | 60 req/min per IP |
| `POST /news/{id}/click` | 100 req/min per user |
| `POST /v1/embed` | 10 req/min per IP |

---

*API 문서 버전: M3-v1.0*  
*최종 업데이트: 2025-01-14*  
*OpenAPI Spec: `/api-docs` (Swagger UI: `/docs`)*