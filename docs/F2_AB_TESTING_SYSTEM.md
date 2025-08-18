# F2: A/B Testing System - 개인화 알고리즘 실험 플랫폼

## 📋 개요

F2 A/B Testing System은 개인화 랭킹 알고리즘의 효과를 과학적으로 검증하기 위한 실험 플랫폼입니다. 익명 사용자 기반의 일관된 실험 배정, 포괄적 메트릭 수집, 자동 성능 모니터링을 통해 데이터 기반 의사결정을 지원합니다.

## 🎯 핵심 기능

### 1. 실험 배정 시스템 (Experiment Bucketing)
- **일관된 배정**: SHA-256 해시 기반 `hash(anon_id, experiment_key) % 100` 알고리즘
- **50/50 분할**: 트래픽의 50%는 control, 50%는 treatment 그룹
- **세션 지속성**: 동일한 사용자는 실험 기간 동안 항상 같은 variant 배정

### 2. A/B 랭킹 Variants
- **Control 그룹**: 표준 importance 기반 랭킹
- **Treatment 그룹**: 개인화된 4-factor 랭킹
  - 45% importance + 20% recency + 25% user_relevance + 10% novelty

### 3. 실험 로깅 파이프라인
- **Impression 로깅**: 뉴스 노출 시 실험 메타데이터 기록
- **Click 로깅**: 클릭 시 실험 context 및 dwell time 추적
- **비동기 처리**: 메인 요청 흐름에 영향 없는 이벤트 기반 로깅

### 4. 메트릭 수집 및 분석
- **CTR (Click-Through Rate)**: variant별 클릭률 비교
- **Dwell Time**: 평균 체류 시간 분석
- **Hide Rate**: 사용자 숨김 빈도 측정
- **Diversity Score**: 콘텐츠 다양성 지표
- **Personalization Score**: 개인화 효과 측정

### 5. 자동 중단 시스템
- **성능 모니터링**: 6시간마다 자동 실험 성능 검사
- **중단 조건**: CTR이 5%p 이상 악화되고 24시간 지속 시 자동 중단
- **Feature Flag 연동**: 자동으로 실험 플래그 비활성화

## 🏗️ 시스템 아키텍처

### 핵심 컴포넌트

```
┌─────────────────────┐    ┌──────────────────────┐    ┌─────────────────────┐
│ ExperimentBucketing │    │ ExperimentalNews     │    │ AnalyticsLogging    │
│ Service             │───▶│ Service              │───▶│ Service             │
│ (사용자 배정)        │    │ (A/B 랭킹 제공)      │    │ (이벤트 로깅)       │
└─────────────────────┘    └──────────────────────┘    └─────────────────────┘
           │                           │                           │
           ▼                           ▼                           ▼
┌─────────────────────┐    ┌──────────────────────┐    ┌─────────────────────┐
│ ExperimentMetrics   │    │ ExperimentAutoStop   │    │ FeatureFlag         │
│ Service             │    │ Service              │    │ Service             │
│ (지표 계산)         │    │ (자동 중단)          │    │ (실험 제어)         │
└─────────────────────┘    └──────────────────────┘    └─────────────────────┘
```

### 데이터베이스 스키마

#### experiment_metrics_daily
```sql
CREATE TABLE experiment_metrics_daily (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    experiment_key VARCHAR(100) NOT NULL,
    variant VARCHAR(50) NOT NULL,
    date_partition VARCHAR(10) NOT NULL,
    impressions BIGINT DEFAULT 0,
    clicks BIGINT DEFAULT 0,
    ctr DOUBLE DEFAULT 0.0,
    avg_dwell_time_ms DOUBLE DEFAULT 0.0,
    diversity_score DOUBLE DEFAULT 0.0,
    personalization_score DOUBLE DEFAULT 0.0
);
```

#### experiment_assignment
```sql
CREATE TABLE experiment_assignment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    anon_id VARCHAR(36) NOT NULL,
    experiment_key VARCHAR(100) NOT NULL,
    variant VARCHAR(50) NOT NULL,
    assigned_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
```

#### experiment_alert
```sql
CREATE TABLE experiment_alert (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    experiment_key VARCHAR(100) NOT NULL,
    alert_type VARCHAR(50) NOT NULL,
    message TEXT NOT NULL,
    triggered_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    is_resolved BOOLEAN DEFAULT FALSE
);
```

## 📡 API 엔드포인트

### 실험적 뉴스 조회
```http
GET /news/top/experimental
```

**Parameters:**
- `X-Anonymous-ID` (header): 익명 사용자 ID
- `n` (query): 반환할 기사 수 (기본값: 20)
- `tickers` (query): 필터링할 종목 코드
- `sort` (query): 정렬 방식 (rank/time)
- `diversity` (query): MMR 다양성 필터링 적용 여부

**Response:**
```json
{
  "items": [/* 뉴스 기사 배열 */],
  "experimentKey": "ranking_ab",
  "variant": "control",
  "diversityApplied": true,
  "anonId": "user-123",
  "personalized": false
}
```

### 관리 API

#### 수동 실험 중단 체크
```http
POST /admin/experiments/{experimentKey}/check-auto-stop
```

#### 실험 메트릭 수동 계산
```http
POST /admin/experiments/calculate-metrics
```

## 🔧 구성 및 설정

### Feature Flags

#### 실험 제어
```properties
experiment.ranking_ab.enabled=true              # 랭킹 A/B 실험 활성화
experiment.auto_stop.enabled=true               # 자동 중단 시스템 활성화
```

#### 메트릭 수집
```properties
analytics.impression_logging.enabled=true      # 노출 로깅 활성화
analytics.click_logging.enabled=true           # 클릭 로깅 활성화
```

### 스케줄링 설정

#### 메트릭 계산 (매시간)
```java
@Scheduled(fixedRate = 3600000) // 1시간
public void calculateHourlyMetrics()
```

#### 자동 중단 검사 (6시간마다)
```java
@Scheduled(fixedRate = 21600000) // 6시간
public void checkExperimentsForAutoStop()
```

## 📊 메트릭 상세

### 1. CTR (Click-Through Rate)
```
CTR = (클릭 수 / 노출 수) × 100
```
- **목표**: treatment 그룹의 CTR이 control 대비 향상
- **임계값**: 5%p 이상 악화 시 자동 중단

### 2. Dwell Time
```
평균 체류 시간 = Σ(개별 체류 시간) / 클릭 수
```
- **의미**: 사용자 참여도 및 콘텐츠 품질 지표
- **단위**: 밀리초

### 3. Diversity Score
```
Diversity Score = 1 - (중복 제거된 기사 수 / 전체 노출 기사 수)
```
- **범위**: 0.0 (낮은 다양성) ~ 1.0 (높은 다양성)
- **목표**: 개인화와 다양성의 균형

### 4. Personalization Score
```
Personalization Score = Σ(사용자 관심도 × 기사 관련성) / 노출 기사 수
```
- **계산**: 사용자 선호도와 기사 특성의 코사인 유사도
- **범위**: 0.0 ~ 1.0

## ⚙️ 운영 가이드

### 실험 시작
1. **Feature Flag 활성화**
   ```sql
   UPDATE feature_flag 
   SET flag_value = 'true' 
   WHERE flag_key = 'experiment.ranking_ab.enabled';
   ```

2. **베이스라인 측정**
   - 최소 7일간 데이터 수집
   - 일일 1000+ impression 확보

### 실험 모니터링
1. **일일 메트릭 확인**
   ```sql
   SELECT variant, ctr, avg_dwell_time_ms, diversity_score
   FROM experiment_metrics_daily
   WHERE experiment_key = 'ranking_ab'
   AND date_partition >= CURRENT_DATE - INTERVAL 7 DAY;
   ```

2. **자동 알림 확인**
   ```sql
   SELECT * FROM experiment_alert
   WHERE experiment_key = 'ranking_ab'
   AND is_resolved = FALSE;
   ```

### 실험 종료
1. **수동 중단**
   ```sql
   UPDATE feature_flag 
   SET flag_value = 'false' 
   WHERE flag_key = 'experiment.ranking_ab.enabled';
   ```

2. **결과 분석**
   - 통계적 유의성 검정
   - 비즈니스 메트릭 영향 평가
   - 사용자 피드백 수집

## 🔍 트러블슈팅

### 일반적인 문제

#### 1. 실험 배정 불일치
**증상**: 동일한 사용자가 다른 variant에 배정됨
**원인**: anon_id 불일치 또는 해시 알고리즘 변경
**해결**: 
```sql
SELECT anon_id, experiment_key, variant, assigned_at
FROM experiment_assignment
WHERE anon_id = 'problem-user-id'
ORDER BY assigned_at DESC;
```

#### 2. 메트릭 계산 오류
**증상**: 비정상적인 CTR 값 (> 100% 또는 < 0%)
**원인**: 중복 이벤트 로깅 또는 타이밍 이슈
**해결**:
```sql
-- 중복 제거 후 재계산
DELETE FROM impression_log 
WHERE id NOT IN (
    SELECT MIN(id) FROM impression_log 
    GROUP BY anon_id, news_id, date_partition
);
```

#### 3. 자동 중단 오작동
**증상**: 정상 실험이 조기 중단됨
**원인**: 샘플 크기 부족 또는 임계값 설정 오류
**해결**:
```java
// MIN_IMPRESSIONS_THRESHOLD 조정
private static final long MIN_IMPRESSIONS_THRESHOLD = 1000;
```

### 성능 최적화

#### 1. 데이터베이스 인덱스
```sql
-- 핵심 인덱스 확인
CREATE INDEX idx_experiment_metrics_key_variant_date 
ON experiment_metrics_daily(experiment_key, variant, date_partition);

CREATE INDEX idx_impression_experiment_date 
ON impression_log(experiment_key, variant, date_partition);
```

#### 2. 캐시 설정
```java
@Cacheable(value = "experimentAssignments", key = "#anonId + '_' + #experimentKey")
public String getExperimentAssignment(String anonId, String experimentKey)
```

## 📈 확장 계획

### F3 예정 기능
1. **다중 실험 지원**: 동시 여러 실험 실행
2. **세그먼트 기반 실험**: 사용자 그룹별 차별화 실험
3. **베이지안 통계**: 더 정확한 실험 결과 해석
4. **실시간 대시보드**: 웹 기반 실험 모니터링 UI

### 성능 목표
- **응답 시간**: P95 < 100ms (실험 배정 포함)
- **처리량**: 1000+ RPS 지원
- **정확도**: 99.9% 배정 일관성
- **가용성**: 99.9% 업타임

---

**마지막 업데이트**: 2025-08-18  
**버전**: F2 1.0.0  
**담당자**: Claude Code System