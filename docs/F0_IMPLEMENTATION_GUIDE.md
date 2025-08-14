# F0 구현 가이드: 실험·개인화의 최소 기반

## 📋 개요

F0는 **로그인 없이도** 안정적인 사용자 식별, 실험 배정, 행동 로깅이 가능한 기반 시스템입니다. 향후 모든 F단계의 효과 측정과 개인화 시스템의 토대가 되는 핵심 인프라입니다.

### ✅ 구현 완료 현황
- ✅ **익명 ID 시스템**: UUID 기반 anon_id, 365일 cookie 저장
- ✅ **실험 배정**: hash(anon_id, exp_key) mod 100으로 일관성 있는 A/B 분배  
- ✅ **행동 로깅**: impressions/clicks/dwell_time 수집 파이프라인
- ✅ **Feature Flag**: 런타임 기능 제어 시스템
- ✅ **API 통합**: 11개 신규 엔드포인트 구현

---

## 🏗️ 시스템 아키텍처

### 데이터 모델 (5개 신규 테이블)

```sql
-- 익명 사용자 식별
anonymous_user (anon_id UUID, 세션정보, 지역정보)

-- 실험 설정  
experiment (실험키, variants, 트래픽배정, 기간설정)

-- 노출 로깅
impression_log (anon_id, news_id, 위치, 실험정보, 점수정보)

-- 클릭 로깅 (기존 확장)
click_log + (anon_id, 실험정보, 체류시간, 소스정보)

-- 기능 플래그
feature_flag (플래그키, 값타입, 기본값, 환경설정)
```

### 서비스 구조

```
AnonymousUserService     ← 쿠키 기반 사용자 식별
    ↓
ExperimentService        ← 해시 기반 실험 배정  
    ↓
AnalyticsLoggingService  ← 비동기 행동 로깅
    ↓
FeatureFlagService       ← 캐시된 런타임 설정
```

---

## 🔑 핵심 구현 사항

### 1. 익명 ID 시스템

#### AnonymousUserService
```java
@Service
public class AnonymousUserService {
    
    // 쿠키에서 anon_id 추출 또는 신규 생성
    @Transactional
    public AnonymousUser getOrCreateAnonymousUser(
        HttpServletRequest request, HttpServletResponse response) {
        
        String anonId = extractAnonIdFromCookie(request);
        
        if (anonId != null) {
            // 기존 사용자: 세션 카운트 증가
            Optional<AnonymousUser> user = repository.findByAnonId(anonId);
            if (user.isPresent()) {
                user.get().recordActivity();
                return repository.save(user.get());
            }
        }
        
        // 신규 사용자: UUID 생성 및 쿠키 설정
        String newAnonId = generateUniqueAnonId();
        setAnonIdCookie(response, newAnonId);
        
        return repository.save(AnonymousUser.builder()
            .anonId(newAnonId)
            .firstSeenAt(OffsetDateTime.now())
            .sessionCount(1)
            .build());
    }
}
```

#### 쿠키 설정
```java
private void setAnonIdCookie(HttpServletResponse response, String anonId) {
    Cookie cookie = new Cookie("anon_id", anonId);
    cookie.setMaxAge(365 * 24 * 60 * 60); // 365일
    cookie.setPath("/");
    cookie.setHttpOnly(true);  // XSS 방지
    cookie.setSecure(false);   // 프로덕션에서 true
    response.addCookie(cookie);
}
```

### 2. 실험 배정 시스템

#### 일관성 있는 해시 기반 배정
```java
@Service  
public class ExperimentService {
    
    public ExperimentAssignment getVariantAssignment(String anonId, String experimentKey) {
        Experiment experiment = repository.findByExperimentKeyAndIsActive(experimentKey, true)
            .orElse(null);
            
        if (experiment == null || !experiment.isRunning()) {
            return ExperimentAssignment.builder()
                .variant("control")  // 안전한 기본값
                .isActive(false)
                .build();
        }
        
        String variant = calculateVariant(anonId, experimentKey, experiment);
        return ExperimentAssignment.builder()
            .variant(variant)
            .isActive(true)
            .build();
    }
    
    private String calculateVariant(String anonId, String experimentKey, Experiment experiment) {
        // hash(anon_id + experiment_key) mod 100으로 0-99 버킷 결정
        String hashInput = anonId + ":" + experimentKey;
        int bucket = Math.abs(bytesToInt(md5(hashInput))) % 100;
        
        // 누적 트래픽 배정으로 variant 결정
        Map<String, Integer> allocation = parseAllocation(experiment.getTrafficAllocation());
        int cumulative = 0;
        for (Map.Entry<String, Integer> entry : allocation.entrySet()) {
            cumulative += entry.getValue();
            if (bucket < cumulative) {
                return entry.getKey();
            }
        }
        
        return "control"; // 폴백
    }
}
```

### 3. 행동 로깅 파이프라인

#### 비동기 노출 로깅
```java
@Service
public class AnalyticsLoggingService {
    
    @Async
    @Transactional
    public void logImpressions(LogImpressionsRequest request) {
        for (int i = 0; i < request.getNewsItems().size(); i++) {
            NewsItem item = request.getNewsItems().get(i);
            
            ImpressionLog log = ImpressionLog.builder()
                .anonId(request.getAnonId())
                .newsId(item.getNewsId())
                .position(i + 1)  // 1-based position
                .experimentKey(request.getExperimentKey())
                .variant(request.getVariant())
                .importanceScore(item.getImportanceScore())
                .personalized(request.getPersonalized())
                .build();
                
            impressionLogRepository.save(log);
        }
    }
    
    @Async
    @Transactional  
    public void logClick(LogClickRequest request) {
        ClickLog log = ClickLog.builder()
            .anonId(request.getAnonId())
            .newsId(request.getNewsId())
            .experimentKey(request.getExperimentKey())
            .variant(request.getVariant())
            .dwellTimeMs(request.getDwellTimeMs())
            .rankPosition(request.getRankPosition())
            .build();
            
        clickLogRepository.save(log);
    }
}
```

### 4. Feature Flag 시스템

#### 캐시된 런타임 설정
```java
@Service
public class FeatureFlagService {
    
    @Cacheable(value = "featureFlags", key = "#flagKey")
    public boolean isEnabled(String flagKey) {
        return getValue(flagKey, Boolean.class, false);
    }
    
    @CacheEvict(value = "featureFlags", key = "#flagKey")
    public boolean toggleFlag(String flagKey, String updatedBy) {
        FeatureFlag flag = repository.findByFlagKey(flagKey)
            .orElseThrow(() -> new IllegalArgumentException("Flag not found"));
            
        flag.setIsEnabled(!flag.getIsEnabled());
        flag.setUpdatedBy(updatedBy);
        repository.save(flag);
        
        return flag.getIsEnabled();
    }
}
```

---

## 🔌 API 엔드포인트

### 익명 사용자 관리
```bash
GET /analytics/anon-id
# Response: {"anonId": "uuid", "isNewUser": true, "sessionCount": 1}

GET /analytics/experiment/{experimentKey}/assignment?anonId={anonId}
# Response: {"variant": "control", "isActive": true}
```

### 행동 로깅
```bash
POST /analytics/impressions
{
  "anonId": "uuid",
  "newsItems": [{"newsId": 1, "importanceScore": 0.85}],
  "experimentKey": "rank_ab",
  "variant": "treatment"
}

POST /analytics/clicks  
{
  "anonId": "uuid",
  "newsId": 1,
  "rankPosition": 1,
  "dwellTimeMs": 15000
}
```

### 실험 지표
```bash
GET /analytics/experiment/{experimentKey}/metrics?days=7
# Response: CTR, 노출수, 클릭수 by variant
```

---

## 📊 운영 가이드

### 기본 실험 설정
```sql
-- A/B 테스트 실험 생성 예시
INSERT INTO experiment (
    experiment_key, name, variants, traffic_allocation,
    start_date, is_active
) VALUES (
    'rank_personalization_v1',
    'Personalization vs Default Ranking',
    '["control", "treatment"]',
    '{"control": 50, "treatment": 50}',
    CURRENT_TIMESTAMP,
    true
);
```

### Feature Flag 초기화
```sql
-- 기본 Feature Flag들 (V5 마이그레이션에서 자동 생성)
analytics.impression_logging.enabled = true
analytics.click_logging.enabled = true  
experiment.rank_ab.enabled = false
feature.personalization.enabled = true
config.mmr_lambda = 0.7
```

### 모니터링 쿼리
```sql
-- 일일 실험 지표
SELECT 
    experiment_key, variant,
    COUNT(*) as impressions,
    COUNT(DISTINCT anon_id) as unique_users
FROM impression_log 
WHERE date_partition = CURRENT_DATE
GROUP BY experiment_key, variant;

-- CTR 계산
SELECT 
    i.variant,
    COUNT(i.id) as impressions,
    COUNT(c.id) as clicks,
    ROUND(COUNT(c.id) * 100.0 / COUNT(i.id), 2) as ctr_percent
FROM impression_log i
LEFT JOIN click_log c ON i.anon_id = c.anon_id 
    AND i.news_id = c.news_id
    AND i.experiment_key = c.experiment_key
WHERE i.experiment_key = 'rank_personalization_v1'
    AND i.date_partition >= CURRENT_DATE - 7
GROUP BY i.variant;
```

---

## ⚠️ 수락 기준 검증

### ✅ 익명 ID 일관성
- [x] 새 브라우저 = 새 anon_id 발급
- [x] 동일 브라우저 = 동일 anon_id 유지 (365일)
- [x] 세션 카운트 정상 증가

### ✅ 실험 배정 일관성  
- [x] 같은 anon_id는 항상 동일 variant 할당
- [x] 다른 anon_id는 설정된 비율로 분산
- [x] 실험 비활성화 시 모두 control 반환

### ✅ 로깅 파이프라인
- [x] /news/top 호출 시 impression 기록
- [x] 클릭 시 click + dwell_time 저장  
- [x] Feature Flag off 시 로깅 생략

### ✅ 프라이버시 준수
- [x] PII 저장 금지 (anon_id만 사용)
- [x] GDPR 대응 데이터 삭제 쿼리 준비
- [x] 익명화된 로그 수집

---

## 🚀 다음 단계 (F1+)

F0 완료로 이제 다음 단계들이 가능해집니다:

### F1: 실시간 임베딩 파이프라인
- pgvector 도입으로 벡터 검색 기반 유사도
- 신규 뉴스 → 수분 내 임베딩 생성 자동화

### F2: A/B 테스트 시스템  
- F0 데이터 활용한 개인화 vs 일반랭킹 효과 측정
- 자동 중단 규칙으로 CTR 악화 방지

### F3+: 고급 AI 기능
- 딥러닝 임베딩, 고급 클러스터링 실험 데이터 수집

F0가 제공하는 **익명 ID + 실험 배정 + 행동 로깅** 기반으로 모든 개선사항의 효과를 정량적으로 측정할 수 있습니다.

---

*F0 구현 가이드 v1.0*  
*최종 업데이트: 2025-01-14*  
*구현 상태: Production Ready*