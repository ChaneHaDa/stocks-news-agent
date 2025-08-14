# M3 아키텍처 가이드

## 📋 개요

M3 마일스톤에서는 한국 주식 뉴스 플랫폼이 **임베딩 기반 지능형 추천 시스템**으로 진화했습니다. 이 문서는 M3에서 도입된 핵심 아키텍처와 구현 방식을 상세히 설명합니다.

---

## 🏗️ 전체 아키텍처

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Next.js Web   │    │ Spring Boot API │    │  FastAPI ML     │
│   (Frontend)    │◄──►│   (Backend)     │◄──►│   (ML Service)  │
│                 │    │                 │    │                 │
│ • 뉴스 조회      │    │ • 개인화 랭킹    │    │ • 임베딩 생성    │
│ • 클릭 추적      │    │ • 다양성 필터링  │    │ • 중요도 스코어링 │
│ • 환경설정       │    │ • 토픽 클러스터링 │    │ • 요약 생성      │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                                │
                        ┌─────────────────┐
                        │   Database      │
                        │   (H2/Postgres) │
                        │                 │
                        │ • 뉴스 + 스코어  │
                        │ • 임베딩 벡터    │
                        │ • 토픽 클러스터  │
                        │ • 사용자 환경설정 │
                        │ • 클릭 로그      │
                        └─────────────────┘
```

---

## 🔗 임베딩 시스템

### 아키텍처 개요
```
뉴스 수집 → 컨텐츠 정규화 → 임베딩 생성 → 벡터 저장
     │              │             │           │
  RSS Feeds    ContentNormalizer  ML Service  NewsEmbedding
```

### 핵심 컴포넌트

#### 1. EmbeddingService
```java
@Service
public class EmbeddingService {
    // 단건 임베딩 생성
    Optional<NewsEmbedding> generateEmbedding(News news)
    
    // 배치 임베딩 생성 (최대 50건)
    int generateEmbeddingsBatch(List<News> newsList)
    
    // 코사인 유사도 계산
    Optional<Double> calculateSimilarity(Long newsId1, Long newsId2)
}
```

#### 2. NewsEmbedding Entity
```sql
CREATE TABLE news_embedding (
    id BIGINT PRIMARY KEY,
    news_id BIGINT NOT NULL UNIQUE,
    vector TEXT NOT NULL,           -- JSON 배열: [0.1, 0.2, ...]
    dimension INTEGER NOT NULL,     -- 384
    model_version VARCHAR(50),      -- "mock-embed-v1.0.0"
    l2_norm DOUBLE PRECISION,       -- 벡터 정규화 값
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
```

### 데이터 플로우
1. **뉴스 수집**: RSS → ContentNormalizer → 512자 이내 텍스트
2. **ML 호출**: `/v1/embed` → 384차원 벡터 반환
3. **저장**: JSON 형태로 database 저장
4. **활용**: 유사도 계산, 클러스터링, 다양성 필터링

---

## 🎯 토픽 클러스터링

### 클러스터링 알고리즘
```
임베딩 로드 → 유사도 매트릭스 → 클러스터 형성 → 키워드 추출
      │              │               │            │
  NewsEmbedding  Cosine Similarity  Simple Clustering  TF-IDF
```

#### TopicClusteringService 구조
```java
@Service
public class TopicClusteringService {
    // 메인 클러스터링 실행
    ClusteringResult performClustering()
    
    // 유사도 기반 클러스터 형성
    Map<String, List<Long>> performSimpleClustering(embeddings)
    
    // 중복 기사 탐지 (유사도 ≥ 0.9)
    Map<String, List<Long>> detectDuplicates(newsList, embeddings)
    
    // 토픽 키워드 추출
    Map<String, List<String>> extractTopicKeywords(clusters, newsList)
}
```

### 클러스터링 파라미터
```yaml
SIMILARITY_THRESHOLD: 0.75    # 클러스터링 임계값
DUPLICATE_THRESHOLD: 0.9      # 중복 탐지 임계값
MIN_CLUSTER_SIZE: 2           # 최소 클러스터 크기
MAX_RECENT_HOURS: 48          # 처리 대상 뉴스 범위
```

### NewsTopic Entity
```sql
CREATE TABLE news_topic (
    id BIGINT PRIMARY KEY,
    news_id BIGINT NOT NULL UNIQUE,
    topic_id VARCHAR(100) NOT NULL,     -- "topic_001"
    group_id VARCHAR(100),              -- "dup_001" (중복 그룹)
    topic_keywords VARCHAR(500),        -- ["삼성전자", "실적", "반도체"]
    similarity_score DOUBLE PRECISION,  -- 토픽 중심점과의 유사도
    clustering_method VARCHAR(50) DEFAULT 'simple_cosine'
);
```

---

## 🎲 MMR 다양성 필터링

### Maximal Marginal Relevance 공식
```
MMR = λ × Relevance(d, Q) - (1-λ) × max Similarity(d, S)

λ = 0.7  (70% 관련성, 30% 다양성)
```

### DiversityService 강화
```java
@Service
public class DiversityService {
    // 3단계 유사도 계산
    double calculateSimilarity(News news1, News news2) {
        // 1순위: 임베딩 기반 코사인 유사도
        Optional<Double> embeddingSim = calculateEmbeddingSimilarity(news1, news2);
        if (embeddingSim.isPresent()) return embeddingSim.get();
        
        // 2순위: 토픽 기반 유사도 (같은 토픽 = 0.9)
        double topicSim = calculateTopicSimilarity(news1, news2);
        if (topicSim > 0) return topicSim;
        
        // 3순위: 텍스트 기반 Jaccard 유사도
        return calculateTextSimilarity(news1.getTitle(), news2.getTitle());
    }
    
    // 고급 MMR + 토픽 다양성
    List<News> applyAdvancedDiversityFilter(newsList, targetSize, mmrLambda, maxPerTopic)
}
```

### 다양성 필터링 파이프라인
1. **토픽 그룹핑**: 동일 토픽별로 뉴스 분류
2. **토픽별 제한**: 각 토픽에서 최대 2개만 선택
3. **MMR 적용**: λ=0.7로 최종 랭킹
4. **다양성 점수**: 0-1 스케일로 품질 측정

---

## 👤 개인화 시스템

### 개인화 공식
```
PersonalizedRank = 0.45×importance + 0.20×recency + 0.25×user_relevance + 0.10×novelty
```

### PersonalizationService 아키텍처
```java
@Service
public class PersonalizationService {
    // 개인화 관련성 계산
    double calculatePersonalizedRelevance(News news, String userId) {
        double relevance = 0.0;
        relevance += calculateInterestRelevance(news, userPrefs);     // 관심 종목/키워드
        relevance += calculateClickHistoryRelevance(news, userId);   // 클릭 이력 (7일)
        relevance += calculateTopicRelevance(news, userId);          // 토픽 선호도
        return relevance;
    }
    
    // 개인화 재랭킹
    List<News> applyPersonalizedRanking(List<News> newsList, String userId)
}
```

### 사용자 데이터 모델

#### UserPreference
```sql
CREATE TABLE user_preference (
    user_id VARCHAR(100) NOT NULL,
    interested_tickers VARCHAR(1000),    -- ["005930", "035720"]
    interested_keywords VARCHAR(1000),   -- ["반도체", "AI", "전기차"]
    diversity_weight DOUBLE PRECISION DEFAULT 0.7,  -- MMR λ 파라미터
    personalization_enabled BOOLEAN DEFAULT FALSE
);
```

#### ClickLog
```sql
CREATE TABLE click_log (
    user_id VARCHAR(100) NOT NULL,
    news_id BIGINT NOT NULL,
    clicked_at TIMESTAMP WITH TIME ZONE,
    rank_position INTEGER,              -- 클릭 시점의 위치
    importance_score DOUBLE PRECISION   -- 클릭 시점의 중요도
);
```

### 개인화 데이터 플로우
1. **클릭 수집**: 프론트엔드 → `POST /news/{id}/click`
2. **패턴 분석**: 최근 7일 클릭 → 종목/토픽 선호도 계산
3. **가중치 적용**: 관심사 매칭 → 0.25 factor로 반영
4. **재랭킹**: 4요소 공식으로 최종 점수 계산

---

## 🔄 통합 데이터 파이프라인

### 실시간 파이프라인
```
RSS 수집 (10분) → 임베딩 생성 → 중요도 스코어링 → 저장
```

### 배치 파이프라인
```
토픽 클러스터링 (6시간) → 중복 그룹핑 → 키워드 추출 → 업데이트
```

### 서빙 파이프라인
```
뉴스 조회 → 개인화 랭킹 → MMR 다양성 필터링 → 결과 반환
```

---

## 📊 성능 지표

### 임베딩 성능
- **처리 속도**: 단건 p95 < 30ms
- **배치 처리**: 최대 50건 동시 처리
- **벡터 차원**: 384차원
- **저장 효율**: JSON 압축 저장

### 클러스터링 성능
- **처리 주기**: 6시간마다 자동 실행
- **처리 범위**: 최근 48시간 뉴스
- **클러스터 품질**: 동일 토픽 ≤ 2개 노출
- **중복 감소**: 30%↓ (예상)

### 개인화 성능
- **반응 속도**: 클릭 즉시 반영
- **학습 기간**: 최근 7일 이력
- **가중치 범위**: 0-0.25 (user_relevance)
- **개인화 효과**: CTR 향상 (A/B 테스트 필요)

---

## 🛠️ 운영 가이드

### 배포 및 실행
```bash
# 전체 스택 시작
docker compose up --build

# 서비스별 스케일링
docker compose up --scale api=2

# 개별 서비스 재시작
docker compose restart api
```

### 모니터링
```bash
# 서비스 상태 확인
curl http://localhost:8000/admin/status

# ML 서비스 헬스체크
curl http://localhost:8001/admin/health

# 임베딩 생성 테스트
curl -X POST http://localhost:8001/v1/embed \
  -H "Content-Type: application/json" \
  -d '{"items":[{"id":"test","text":"삼성전자 실적"}]}'
```

### 관리자 작업
```bash
# 수동 뉴스 수집
curl -X POST http://localhost:8000/admin/ingest

# 수동 토픽 클러스터링
curl -X POST http://localhost:8000/admin/clustering

# 기능 플래그 토글
curl -X POST "http://localhost:8000/admin/features/embed?enabled=true"
```

---

## 🔧 설정 및 튜닝

### 환경 변수
```bash
# ML 서비스 기능 플래그
ENABLE_EMBED=true
ENABLE_IMPORTANCE=true
ENABLE_SUMMARIZE=true

# 클러스터링 설정
TOPIC_CLUSTERING_ENABLED=true
TOPIC_CLUSTERING_CRON="0 0 */6 * * *"  # 6시간마다

# 성능 튜닝
MAX_BATCH_SIZE=50
REQUEST_TIMEOUT=60
```

### 클러스터링 파라미터 튜닝
```java
// TopicClusteringService.java
private static final double SIMILARITY_THRESHOLD = 0.75;  // 클러스터링 민감도
private static final double DUPLICATE_THRESHOLD = 0.9;    // 중복 탐지 민감도
private static final int MIN_CLUSTER_SIZE = 2;            // 최소 클러스터 크기
```

### 개인화 파라미터 튜닝
```java
// PersonalizationService.java
private static final int CLICK_HISTORY_DAYS = 7;          // 클릭 이력 기간
private static final double MAX_CLICK_WEIGHT = 0.3;       // 최대 클릭 가중치

// 개인화 공식 가중치 조정
double personalizedScore = 0.45 * importance +     // 중요도
                          0.20 * recency +          // 최신성
                          0.25 * user_relevance +   // 개인화
                          0.10 * novelty;           // 새로움
```

---

## 🚀 확장 계획

### M4+ 로드맵
- **실시간 임베딩**: 스트리밍 기반 즉시 처리
- **고급 클러스터링**: HDBSCAN, K-means 도입
- **딥러닝 임베딩**: Ko-BERT, RoBERTa 적용
- **A/B 테스트**: 다양성 vs 관련성 최적화
- **벡터 DB**: Pinecone, Weaviate 연동

### 성능 최적화 방향
- **분산 처리**: Apache Spark 클러스터링
- **캐싱 강화**: Redis 클러스터 + 분산 캐시
- **실시간 추천**: Kafka + Stream Processing
- **하이브리드 랭킹**: 협업 필터링 + 컨텐츠 기반

---

*문서 버전: M3-v1.0*  
*최종 업데이트: 2025-01-14*  
*작성자: Claude Code Assistant*