# F2 A/B Testing System - API Reference

## 개요

F2 A/B Testing System의 모든 API 엔드포인트에 대한 상세 참조 문서입니다.

## 🧪 실험 API

### GET /news/top/experimental

개인화 알고리즘 A/B 테스트가 적용된 뉴스 목록을 반환합니다.

#### Request

**Headers:**
```http
X-Anonymous-ID: string (optional)
Content-Type: application/json
```

**Query Parameters:**
| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `n` | integer | 20 | 반환할 기사 수 (최대 100) |
| `tickers` | string | null | 쉼표로 구분된 종목 코드 필터 |
| `lang` | string | "ko" | 언어 설정 |
| `hours` | integer | 168 | 조회할 시간 범위 (시간) |
| `sort` | string | "rank" | 정렬 방식: "rank", "time" |
| `diversity` | boolean | true | MMR 다양성 필터링 적용 여부 |

#### Response

**Success (200 OK):**
```json
{
  "items": [
    {
      "id": "1",
      "source": "Yonhap Economy",
      "title": "삼성전자, 3분기 영업이익 전년비 277% 증가",
      "url": "https://example.com/news/1",
      "tickers": ["005930"],
      "summary": "요약 내용...",
      "importance": 0.8542,
      "reason": {
        "freshness": 1.0,
        "source_weight": 0.9,
        "tickers_hit": 0.8,
        "keywords_hit": 0.7
      },
      "published_at": "2025-08-18T03:40:24.106630Z"
    }
  ],
  "experimentKey": "ranking_ab",
  "variant": "control",
  "diversityApplied": true,
  "anonId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "personalized": false
}
```

**Response Fields:**
| Field | Type | Description |
|-------|------|-------------|
| `items` | array | 뉴스 기사 배열 |
| `experimentKey` | string | 실험 식별자 |
| `variant` | string | 배정된 실험 그룹 ("control" 또는 "treatment") |
| `diversityApplied` | boolean | 다양성 필터링 적용 여부 |
| `anonId` | string | 익명 사용자 ID |
| `personalized` | boolean | 개인화 랭킹 적용 여부 |

#### Example Request

```bash
curl -X GET "http://localhost:8000/news/top/experimental?n=10&sort=rank&diversity=true" \
  -H "X-Anonymous-ID: test-user-123" \
  -H "Content-Type: application/json"
```

#### Example Response

```json
{
  "items": [
    {
      "id": "1",
      "source": "Yonhap Economy", 
      "title": "삼성전자, 3분기 영업이익 전년비 277% 증가",
      "url": "https://example.com/news/1",
      "tickers": ["005930"],
      "summary": "요약: 삼성전자, 3분기 영업이익 전년비 277% 증가...",
      "importance": 0.5772940775597488,
      "reason": {
        "freshness": 1.0,
        "source_weight": 0.9,
        "tickers_hit": 0.8,
        "keywords_hit": 0.7
      },
      "published_at": "2025-08-18T03:40:24.106630Z"
    }
  ],
  "experimentKey": "ranking_ab",
  "variant": "control",
  "diversityApplied": true,
  "anonId": "test-user-123", 
  "personalized": false
}
```

## 🔧 관리 API

### POST /admin/experiments/{experimentKey}/check-auto-stop

특정 실험의 자동 중단 조건을 수동으로 확인합니다.

#### Request

**Path Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| `experimentKey` | string | 실험 식별자 (예: "ranking_ab") |

#### Response

**Success (200 OK):**
```json
{
  "experimentKey": "ranking_ab",
  "shouldStop": false,
  "analysis": {
    "avgControlCtr": 0.0523,
    "avgTreatmentCtr": 0.0487,
    "maxDegradation": 0.0036,
    "daysWithDegradation": 0,
    "summary": "CTR: control=0.0523, treatment=0.0487, max_degradation=0.0036, days_with_degradation=0"
  },
  "checkedAt": "2025-08-18T04:30:00Z"
}
```

#### Example Request

```bash
curl -X POST "http://localhost:8000/admin/experiments/ranking_ab/check-auto-stop" \
  -H "Content-Type: application/json"
```

### POST /admin/experiments/calculate-metrics

모든 활성 실험의 메트릭을 수동으로 계산합니다.

#### Response

**Success (200 OK):**
```json
{
  "experimentsProcessed": ["ranking_ab"],
  "metricsCalculated": 2,
  "calculatedAt": "2025-08-18T04:30:00Z"
}
```

#### Example Request

```bash
curl -X POST "http://localhost:8000/admin/experiments/calculate-metrics" \
  -H "Content-Type: application/json"
```

### GET /admin/experiments/{experimentKey}/metrics

특정 실험의 메트릭 조회

#### Request

**Path Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| `experimentKey` | string | 실험 식별자 |

**Query Parameters:**
| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `days` | integer | 7 | 조회할 일수 |

#### Response

**Success (200 OK):**
```json
{
  "experimentKey": "ranking_ab",
  "dateRange": {
    "from": "2025-08-11",
    "to": "2025-08-18"
  },
  "variants": {
    "control": {
      "totalImpressions": 15420,
      "totalClicks": 806,
      "ctr": 0.0523,
      "avgDwellTimeMs": 12500.5,
      "diversityScore": 0.72,
      "personalizationScore": 0.0
    },
    "treatment": {
      "totalImpressions": 15180,
      "totalClicks": 739,
      "ctr": 0.0487,
      "avgDwellTimeMs": 13200.3,
      "diversityScore": 0.68,
      "personalizationScore": 0.84
    }
  },
  "comparison": {
    "ctrDifference": -0.0036,
    "ctrPercentageChange": -6.89,
    "dwellTimeDifference": 699.8,
    "diversityDifference": -0.04,
    "statistically_significant": false
  }
}
```

## 📊 메트릭 API

### GET /admin/experiments/{experimentKey}/daily-metrics

일별 상세 메트릭 조회

#### Request

**Path Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| `experimentKey` | string | 실험 식별자 |

**Query Parameters:**
| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `date_from` | string | 7일 전 | 시작 날짜 (YYYY-MM-DD) |
| `date_to` | string | 오늘 | 종료 날짜 (YYYY-MM-DD) |

#### Response

**Success (200 OK):**
```json
{
  "experimentKey": "ranking_ab",
  "dailyMetrics": [
    {
      "date": "2025-08-18",
      "control": {
        "impressions": 2205,
        "clicks": 115,
        "ctr": 0.0522,
        "avgDwellTimeMs": 12450.2,
        "diversityScore": 0.71,
        "personalizationScore": 0.0
      },
      "treatment": {
        "impressions": 2180,
        "clicks": 106,
        "ctr": 0.0486,
        "avgDwellTimeMs": 13150.8,
        "diversityScore": 0.69,
        "personalizationScore": 0.83
      }
    }
  ]
}
```

## 🚨 알림 API

### GET /admin/experiments/alerts

실험 관련 알림 조회

#### Request

**Query Parameters:**
| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `resolved` | boolean | null | 해결 상태 필터 |
| `experiment_key` | string | null | 특정 실험 필터 |
| `limit` | integer | 50 | 결과 수 제한 |

#### Response

**Success (200 OK):**
```json
{
  "alerts": [
    {
      "id": 1,
      "experimentKey": "ranking_ab",
      "alertType": "auto_stop_triggered", 
      "message": "AUTO-STOPPED experiment ranking_ab due to performance degradation: CTR: control=0.0523, treatment=0.0487, max_degradation=0.0036, days_with_degradation=1",
      "triggeredAt": "2025-08-18T04:25:30Z",
      "isResolved": false
    }
  ],
  "total": 1,
  "unresolved": 1
}
```

### POST /admin/experiments/alerts/{alertId}/resolve

알림을 해결됨으로 표시

#### Request

**Path Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| `alertId` | integer | 알림 ID |

#### Response

**Success (200 OK):**
```json
{
  "alertId": 1,
  "resolved": true,
  "resolvedAt": "2025-08-18T04:30:00Z"
}
```

## 🏷️ Feature Flag API

### GET /admin/feature-flags

모든 Feature Flag 조회

#### Response

**Success (200 OK):**
```json
{
  "flags": [
    {
      "flagKey": "experiment.ranking_ab.enabled",
      "name": "EXPERIMENT RANKING AB ENABLED",
      "description": "Enable A/B testing for ranking algorithm",
      "category": "experiment",
      "valueType": "boolean",
      "flagValue": "true",
      "defaultValue": "false",
      "isEnabled": true,
      "environment": "all",
      "createdAt": "2025-08-18T00:00:00Z",
      "updatedAt": "2025-08-18T04:25:30Z"
    }
  ]
}
```

### PUT /admin/feature-flags/{flagKey}

Feature Flag 값 업데이트

#### Request

**Path Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| `flagKey` | string | Feature Flag 키 |

**Body:**
```json
{
  "flagValue": "false",
  "updatedBy": "admin"
}
```

#### Response

**Success (200 OK):**
```json
{
  "flagKey": "experiment.ranking_ab.enabled",
  "flagValue": "false", 
  "previousValue": "true",
  "updatedBy": "admin",
  "updatedAt": "2025-08-18T04:30:00Z"
}
```

## 🔍 진단 API

### GET /admin/experiments/{experimentKey}/diagnostics

실험 상태 진단

#### Response

**Success (200 OK):**
```json
{
  "experimentKey": "ranking_ab",
  "status": "active",
  "diagnostics": {
    "featureFlagEnabled": true,
    "assignmentConsistency": {
      "totalUsers": 1250,
      "consistentAssignments": 1250,
      "inconsistentAssignments": 0,
      "consistencyRate": 1.0
    },
    "dataQuality": {
      "impressionDataAvailable": true,
      "clickDataAvailable": true,
      "lastImpressionAt": "2025-08-18T04:29:45Z",
      "lastClickAt": "2025-08-18T04:28:12Z"
    },
    "sampleSize": {
      "control": {
        "dailyImpressions": 2205,
        "sufficientSample": true
      },
      "treatment": {
        "dailyImpressions": 2180,
        "sufficientSample": true
      }
    },
    "autoStopCheck": {
      "lastCheckedAt": "2025-08-18T04:00:00Z",
      "nextCheckAt": "2025-08-18T10:00:00Z",
      "shouldStop": false
    }
  }
}
```

## 📝 에러 코드

### 일반 에러

| HTTP Code | Error Code | Description |
|-----------|------------|-------------|
| 400 | `INVALID_PARAMETER` | 잘못된 요청 파라미터 |
| 404 | `EXPERIMENT_NOT_FOUND` | 존재하지 않는 실험 |
| 500 | `INTERNAL_SERVER_ERROR` | 서버 내부 오류 |

### 실험 관련 에러

| HTTP Code | Error Code | Description |
|-----------|------------|-------------|
| 409 | `EXPERIMENT_DISABLED` | 비활성화된 실험 |
| 422 | `INSUFFICIENT_DATA` | 분석에 필요한 데이터 부족 |
| 429 | `RATE_LIMITED` | 요청 한도 초과 |

### 에러 응답 형식

```json
{
  "error": {
    "code": "EXPERIMENT_NOT_FOUND",
    "message": "Experiment 'invalid_experiment' not found",
    "timestamp": "2025-08-18T04:30:00Z",
    "path": "/admin/experiments/invalid_experiment/metrics"
  }
}
```

## 🔐 인증 및 권한

### 관리 API 인증
관리 API는 내부 네트워크에서만 접근 가능하며, 추후 JWT 인증이 추가될 예정입니다.

### Rate Limiting
- **일반 API**: 사용자당 100 req/min
- **관리 API**: IP당 1000 req/min
- **메트릭 API**: IP당 100 req/min

---

**마지막 업데이트**: 2025-08-18  
**버전**: F2 1.0.0  
**담당자**: Claude Code System