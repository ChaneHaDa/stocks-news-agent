# F0 API 레퍼런스

## 📋 개요

F0 단계에서 추가된 익명 사용자 식별, 실험 배정, 행동 로깅 API들의 상세 문서입니다.

---

## 🎯 Base URL

- **API Service**: `http://localhost:8000`

---

## 👤 Anonymous User Management

### GET /analytics/anon-id
익명 사용자 ID를 가져오거나 신규 생성합니다.

#### Request
```http
GET /analytics/anon-id
```

#### Response
```json
{
  "anonId": "2211f203-a242-44ea-bd71-080a28ec8504",
  "isNewUser": true,
  "newUser": true,
  "sessionCount": 1
}
```

#### Response Fields
| Field | Type | Description |
|-------|------|-------------|
| `anonId` | string | UUID 형태의 익명 사용자 ID |
| `isNewUser` | boolean | 새로운 사용자 여부 |
| `newUser` | boolean | 하위 호환성용 필드 |
| `sessionCount` | integer | 세션 방문 횟수 |

#### 동작 방식
1. **기존 사용자**: 쿠키의 `anon_id` 확인 → 세션 카운트 증가
2. **신규 사용자**: UUID 생성 → 365일 쿠키 설정 → DB 저장

#### Cookie 정보
- **이름**: `anon_id`
- **유효기간**: 365일
- **경로**: `/`
- **HttpOnly**: `true` (XSS 방지)
- **SameSite**: `Lax` (권장)

---

## 🧪 Experiment Management

### GET /analytics/experiment/{experimentKey}/assignment
실험 variant 배정을 가져옵니다.

#### Request
```http
GET /analytics/experiment/rank_personalization_v1/assignment?anonId=2211f203-a242-44ea-bd71-080a28ec8504
```

#### Parameters
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `experimentKey` | string | ✅ | 실험 식별 키 |
| `anonId` | string | ✅ | 익명 사용자 ID |

#### Response
```json
{
  "experimentKey": "rank_personalization_v1",
  "variant": "treatment",
  "isActive": true,
  "experimentId": 1
}
```

#### Response Fields
| Field | Type | Description |
|-------|------|-------------|
| `experimentKey` | string | 실험 키 |
| `variant` | string | 배정된 variant ("control", "treatment" 등) |
| `isActive` | boolean | 실험 활성화 상태 |
| `experimentId` | integer | 실험 DB ID |

#### 배정 알고리즘
```java
// hash(anon_id + experiment_key) mod 100 → 0-99 버킷
String hashInput = anonId + ":" + experimentKey;
int bucket = Math.abs(md5(hashInput)) % 100;

// 누적 트래픽 배정으로 variant 결정
// 예: {"control": 50, "treatment": 50} → 0-49: control, 50-99: treatment
```

#### 일관성 보장
- **동일한 anon_id**: 항상 동일한 variant 반환
- **실험 비활성화**: 모든 사용자에게 "control" 반환
- **실험 없음**: "control" 반환 + `isActive: false`

---

## 📊 Analytics Logging

### POST /analytics/impressions
뉴스 노출 이벤트를 로깅합니다.

#### Request
```http
POST /analytics/impressions
Content-Type: application/json

{
  "anonId": "2211f203-a242-44ea-bd71-080a28ec8504",
  "sessionId": "session_1705230000",
  "newsItems": [
    {
      "newsId": 1,
      "importanceScore": 0.85,
      "rankScore": 0.92
    },
    {
      "newsId": 2,  
      "importanceScore": 0.73,
      "rankScore": 0.81
    }
  ],
  "pageType": "top",
  "experimentKey": "rank_personalization_v1",
  "variant": "treatment",
  "personalized": true,
  "diversityApplied": true
}
```

#### Request Fields
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `anonId` | string | ✅ | 익명 사용자 ID |
| `sessionId` | string | ❌ | 세션 ID |
| `newsItems` | array | ✅ | 노출된 뉴스 항목들 |
| `pageType` | string | ❌ | 페이지 타입 ("top", "search" 등) |
| `experimentKey` | string | ❌ | 실험 키 |
| `variant` | string | ❌ | 실험 variant |
| `personalized` | boolean | ❌ | 개인화 적용 여부 |
| `diversityApplied` | boolean | ❌ | 다양성 필터링 적용 여부 |

#### NewsItem Fields
| Field | Type | Description |
|-------|------|-------------|
| `newsId` | integer | 뉴스 ID |
| `importanceScore` | number | 중요도 점수 (0-1) |
| `rankScore` | number | 최종 랭킹 점수 (0-1) |

#### Response
```http
200 OK
```

#### 자동 추가 필드
- `position`: 1-based 순서 (배열 인덱스 + 1)
- `timestamp`: 현재 시각
- `datePartition`: YYYY-MM-DD 형태
- `userAgent`, `ipAddress`, `referer`: 요청 헤더에서 자동 추출

### POST /analytics/clicks
뉴스 클릭 이벤트를 로깅합니다.

#### Request
```http
POST /analytics/clicks
Content-Type: application/json

{
  "anonId": "2211f203-a242-44ea-bd71-080a28ec8504",
  "newsId": 1,
  "sessionId": "session_1705230000",
  "rankPosition": 1,
  "importanceScore": 0.85,
  "experimentKey": "rank_personalization_v1",
  "variant": "treatment",
  "dwellTimeMs": 15000,
  "clickSource": "news_list",
  "personalized": true
}
```

#### Request Fields
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `anonId` | string | ✅ | 익명 사용자 ID |
| `newsId` | integer | ✅ | 클릭한 뉴스 ID |
| `sessionId` | string | ❌ | 세션 ID |
| `rankPosition` | integer | ❌ | 클릭 시점의 랭킹 위치 |
| `importanceScore` | number | ❌ | 클릭 시점의 중요도 점수 |
| `experimentKey` | string | ❌ | 실험 키 |
| `variant` | string | ❌ | 실험 variant |
| `dwellTimeMs` | integer | ❌ | 페이지 체류 시간 (밀리초) |
| `clickSource` | string | ❌ | 클릭 소스 ("news_list", "similar" 등) |
| `personalized` | boolean | ❌ | 개인화 적용 여부 |

#### Response
```http
200 OK
```

---

## 📈 Analytics Metrics

### GET /analytics/experiment/{experimentKey}/metrics
실험 지표를 조회합니다.

#### Request
```http
GET /analytics/experiment/rank_personalization_v1/metrics?days=7
```

#### Parameters
| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `days` | integer | 7 | 조회 기간 (일) |

#### Response
```json
{
  "experimentKey": "rank_personalization_v1",
  "dateFrom": "2025-01-07",
  "dateTo": "2025-01-14",
  "impressionsByVariant": {
    "control": 1250,
    "treatment": 1180
  },
  "clicksByVariant": {
    "control": 89,
    "treatment": 142
  },
  "ctrByVariant": {
    "control": 0.0712,
    "treatment": 0.1203
  }
}
```

#### Response Fields
| Field | Type | Description |
|-------|------|-------------|
| `experimentKey` | string | 실험 키 |
| `dateFrom` / `dateTo` | string | 조회 기간 (YYYY-MM-DD) |
| `impressionsByVariant` | object | variant별 노출 수 |
| `clicksByVariant` | object | variant별 클릭 수 |
| `ctrByVariant` | object | variant별 CTR (클릭률) |

#### CTR 계산 공식
```
CTR = clicks / impressions
예: 142 / 1180 = 0.1203 (12.03%)
```

---

## ⚙️ Feature Flag Integration

모든 로깅 API는 Feature Flag에 의해 제어됩니다:

### 관련 Feature Flags
```sql
analytics.impression_logging.enabled = true
analytics.click_logging.enabled = true
experiment.rank_ab.enabled = false
```

### 동작 방식
- **Flag OFF**: API 호출은 성공하지만 실제 로깅은 생략
- **Flag ON**: 정상적인 로깅 수행

---

## 🔒 프라이버시 & 보안

### PII 제거
- 이메일, 전화번호 등 개인식별정보 수집 금지
- anon_id만 사용한 익명화된 추적

### GDPR 준수
```sql
-- 익명 사용자 데이터 삭제 (365일 후)
DELETE FROM anonymous_user WHERE last_seen_at < CURRENT_DATE - INTERVAL '365' DAY;
DELETE FROM impression_log WHERE timestamp < CURRENT_DATE - INTERVAL '90' DAY;
DELETE FROM click_log WHERE clicked_at < CURRENT_DATE - INTERVAL '90' DAY;
```

### 보안 헤더
- `HttpOnly` 쿠키로 XSS 방지
- IP 주소는 해시화 고려 (선택사항)
- User-Agent는 분석용으로만 사용

---

## 🛠️ 개발자 가이드

### 통합 예시 (JavaScript)
```javascript
// 1. 익명 ID 가져오기
const anonResponse = await fetch('/analytics/anon-id');
const { anonId } = await anonResponse.json();

// 2. 실험 배정 확인
const expResponse = await fetch(
  `/analytics/experiment/rank_personalization_v1/assignment?anonId=${anonId}`
);
const { variant } = await expResponse.json();

// 3. 뉴스 목록 가져오기 (기존 API)
const newsResponse = await fetch('/news/top?n=10');
const { items } = await newsResponse.json();

// 4. 노출 로깅
await fetch('/analytics/impressions', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    anonId,
    sessionId: sessionStorage.getItem('sessionId'),
    newsItems: items.map(item => ({
      newsId: item.id,
      importanceScore: item.importance,
      rankScore: item.rankScore
    })),
    experimentKey: 'rank_personalization_v1',
    variant,
    personalized: variant === 'treatment'
  })
});

// 5. 클릭 로깅 (사용자가 뉴스 클릭 시)
await fetch('/analytics/clicks', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    anonId,
    newsId: clickedNewsId,
    rankPosition: clickedPosition,
    experimentKey: 'rank_personalization_v1',
    variant,
    dwellTimeMs: timeSpentOnPage
  })
});
```

### 에러 처리
```javascript
try {
  await logImpression(data);
} catch (error) {
  // 로깅 실패는 사용자 경험에 영향 주지 않음
  console.warn('Analytics logging failed:', error);
}
```

---

## 📊 성능 가이드라인

### API 응답 시간 목표
| Endpoint | Target Response Time |
|----------|----------------------|
| `GET /analytics/anon-id` | < 100ms |
| `GET /analytics/experiment/.../assignment` | < 50ms |
| `POST /analytics/impressions` | < 200ms (비동기) |
| `POST /analytics/clicks` | < 100ms (비동기) |
| `GET /analytics/experiment/.../metrics` | < 500ms |

### Rate Limiting
| Endpoint | Limit |
|----------|-------|
| 익명 ID 생성 | 60 req/min per IP |
| 노출 로깅 | 1000 req/min per anon_id |
| 클릭 로깅 | 500 req/min per anon_id |

---

*F0 API 레퍼런스 v1.0*  
*최종 업데이트: 2025-01-14*  
*OpenAPI Spec: 향후 `/api-docs` 엔드포인트에서 제공 예정*