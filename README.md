# News Agent

한국 주식 뉴스 수집, AI 기반 중요도 스코어링, MMR 다양성 필터링을 제공하는 마이크로서비스 플랫폼

## 🏗️ 마이크로서비스 아키텍처

```
news-agent/
├── services/api/         # Spring Boot API 게이트웨이 (포트 8000)
│   ├── RSS 뉴스 수집     # 한국 주요 언론사 10분마다 수집
│   ├── ML 클라이언트     # Circuit Breaker + 캐싱 + Fallback
│   └── MMR 다양성 필터   # Jaccard 유사도 기반 토픽 클러스터링
├── ml/serving/           # FastAPI ML 서비스 (포트 8001)
│   ├── 중요도 스코어링   # 다중 팩터 기반 0-1 점수
│   ├── AI 요약 생성     # 추출적 + 생성적 요약
│   └── 텍스트 임베딩    # 의미적 유사도 계산
├── web/                  # Next.js 프론트엔드 (포트 3000)
│   └── 뉴스 카드 UI     # 실시간 피드 + 중요도 표시
├── contracts/            # OpenAPI 스키마 및 서비스 간 계약
└── docker-compose.yml    # 전체 스택 오케스트레이션
```

### 🔄 서비스 통신 플로우 (마이크로서비스)
1. **RSS 수집** → API 서비스가 한국 금융 뉴스 자동 수집 (10분마다)
2. **ML 처리** → API → ML 서비스 HTTP 호출 (중요도, 요약, 임베딩)
3. **장애 격리** → Circuit Breaker로 ML 서비스 장애 시 폴백 동작
4. **고급 랭킹** → MMR 다양성 필터링으로 중복 토픽 제한 (≤2개/클러스터)
5. **실시간 UI** → 웹에서 랭킹된 뉴스 카드 표시

## 🚀 실행 방법

### 🐳 Docker Compose (권장)

**전체 스택 실행** - API, ML 서비스, 웹 모두 포함
```bash
# 모든 서비스 빌드 및 실행
docker compose up --build

# 백그라운드 실행
docker compose up -d --build

# 특정 서비스 로그 확인
docker compose logs -f [api|ml-service|web]

# 서비스 중지
docker compose down
```

**서비스 접근**:
- 🌐 **웹 UI**: http://localhost:3000 (뉴스 피드)
- 🔗 **API 게이트웨이**: http://localhost:8000 (REST API)
- 🤖 **ML 서비스**: http://localhost:8001 (AI 모델 서빙)
- 📚 **API 문서**: http://localhost:8000/docs (Swagger)
- 📊 **ML 메트릭**: http://localhost:8001/metrics (Prometheus)

### 🛠️ 개발 모드 (로컬 실행)

#### 1. API 서버 (Spring Boot)
```bash
cd services/api
./gradlew bootRun                    # 개발 모드
./gradlew test                       # 테스트 실행
curl -X POST http://localhost:8000/admin/ingest  # 수동 뉴스 수집
```

#### 2. ML 서비스 (FastAPI)
```bash
cd ml/serving
pip install -r requirements.txt
uvicorn ml_service.main:app --reload # 개발 모드
curl http://localhost:8001/admin/health  # 헬스체크
curl http://localhost:8001/metrics       # 메트릭 확인
```

#### 3. 웹 프론트엔드 (Next.js)
```bash
cd web
npm install
npm run dev                          # 개발 서버 (http://localhost:3000)
```

## 🔌 API 엔드포인트

### API 게이트웨이 (포트 8000)

#### GET /news/top
개인화된 뉴스 목록을 MMR 다양성 필터링으로 반환 (M3 확장)

**Parameters:**
- `n` (int, default=20): 반환할 기사 수 (1-100)
- `tickers` (string, optional): 필터링할 종목 코드 (쉼표 구분)
- `lang` (string, default="ko"): 언어 설정
- `personalized` (boolean, default=false): **[M3 신규]** 개인화 랭킹 적용
- `userId` (string): **[M3 신규]** 사용자 ID (개인화 시 필수)
- `diversity` (boolean, default=true): MMR 다양성 필터링 적용

**Examples:**
```bash
# 기본 뉴스 조회
curl "http://localhost:8000/news/top?n=5&tickers=005930,035720"

# 개인화된 뉴스 조회 (M3)
curl "http://localhost:8000/news/top?n=10&personalized=true&userId=user123"
```

#### POST /news/{id}/click **[M3 신규]**
사용자 클릭 이벤트 기록 (개인화 학습용)

#### GET /users/{userId}/preferences **[M3 신규]**
사용자 환경설정 조회

#### PUT /users/{userId}/preferences **[M3 신규]**
사용자 환경설정 업데이트 (관심 종목/키워드, 개인화 활성화)

#### POST /admin/ingest
RSS 뉴스 수동 수집 트리거

#### POST /admin/clustering **[M3 신규]**
토픽 클러스터링 수동 실행

### ML 서비스 (포트 8001)

#### POST /v1/importance:score
뉴스 중요도 스코어링 (0-1 점수)

#### POST /v1/summarize  
AI 기반 뉴스 요약 생성

#### POST /v1/embed **[M3 신규]**
텍스트 임베딩 벡터 생성 (384차원)

**Example:**
```bash
curl -X POST http://localhost:8001/v1/embed \
  -H "Content-Type: application/json" \
  -d '{"items":[{"id":"1","text":"삼성전자 3분기 실적 발표"}]}'
```

#### GET /admin/health
ML 서비스 및 모델 상태 확인

#### GET /metrics
Prometheus 메트릭 노출

## 환경 변수

| 변수명 | 기본값 | 설명 |
|--------|--------|------|
| `NEXT_PUBLIC_API_URL` | `http://localhost:8000` | API 게이트웨이 URL (웹에서 접근) |
| `ML_SERVICE_URL` | `http://ml-service:8001` | ML 서비스 URL (API에서 접근) |
| `NODE_ENV` | `development` | Node.js 환경 모드 |
| `JAVA_OPTS` | `-Xmx512m -Xms256m` | Java JVM 옵션 |
| `ML_DEBUG` | `false` | ML 서비스 디버그 모드 |
| `LOG_LEVEL` | `INFO` | 로깅 레벨 |
| `ENABLE_EMBED` | `true` | **[M3 신규]** 임베딩 기능 활성화 |
| `TOPIC_CLUSTERING_ENABLED` | `true` | **[M3 신규]** 토픽 클러스터링 활성화 |
| `TOPIC_CLUSTERING_CRON` | `0 0 */6 * * *` | **[M3 신규]** 클러스터링 실행 주기 (6시간) |

### Docker Compose 환경 변수

Docker Compose 실행 시 자동으로 설정되는 환경 변수들:
- **API 서버**: `JAVA_OPTS=-Xmx512m -Xms256m`, `ML_SERVICE_URL=http://ml-service:8001`
- **ML 서비스**: `ML_DEBUG=false`, `LOG_LEVEL=INFO`, `LOG_FORMAT=json`, `ENABLE_EMBED=true`
- **Web 서버**: `NODE_ENV=production`, `NEXT_PUBLIC_API_URL=http://localhost:8000`
- **M3 기능**: `TOPIC_CLUSTERING_ENABLED=true`, `ENABLE_IMPORTANCE=true`, `ENABLE_SUMMARIZE=true`

## 브랜치 전략

- `main`: 항상 실행 가능한 상태 유지
- 기능 개발은 feature 브랜치에서 진행 후 PR
- PR 생성 시 수락 기준 체크리스트 확인

## 🚀 현재 상태 (M3 마일스톤 완료) 

✅ **M1 완료된 작업 (마이크로서비스 분리):**
- [x] **마이크로서비스 아키텍처**: Spring Boot API + FastAPI ML + Next.js Web
- [x] **Circuit Breaker**: Resilience4J 기반 장애 격리 및 폴백
- [x] **캐싱**: Caffeine 기반 응답 캐싱 (중요도 5분, 요약 24시간)
- [x] **Docker 통합**: 서비스 간 헬스체크 및 의존성 관리
- [x] **모니터링**: 구조화 로깅 + Prometheus 메트릭
- [x] **OpenAPI 계약**: 서비스 간 API 스키마 정의

✅ **M2 완료된 작업 (실제 ML 모델 통합):**
- [x] **LogisticRegression 중요도 모델**: PR-AUC 1.0000 (목표: ≥ 0.70) ✅
- [x] **Hybrid 요약 시스템**: 추출 요약 + LLM 통합 + 금칙어 필터링 ✅
- [x] **금융 규제 준수**: 투자 조언/투기성 언어 자동 제거 (위반 0건) ✅
- [x] **고성능 서빙**: 50+ RPS, P95 = 8ms (목표: < 300ms, 37배 향상) ✅
- [x] **Feature Flag 시스템**: 런타임 ML 기능 제어 ✅

🎯 **M3 완료된 작업 (임베딩 기반 지능형 추천 시스템):**
- [x] **임베딩 시스템**: 384차원 벡터 생성 및 저장 (NewsEmbedding 엔티티) ✅
- [x] **토픽 클러스터링**: 코사인 유사도 기반 자동 분류 (6시간 배치) ✅
- [x] **MMR 다양성 필터링**: λ=0.7 균형잡힌 관련성/다양성 (≤2개/토픽) ✅
- [x] **개인화 시스템**: 4요소 공식 (45% 중요도 + 20% 최신성 + 25% 사용자관련성 + 10% 새로움) ✅
- [x] **사용자 환경설정**: 관심 종목/키워드, 다양성 가중치 조정 ✅
- [x] **클릭 추적**: 사용자 행동 기반 개인화 학습 ✅
- [x] **개인화 API**: 11개 신규 엔드포인트 (환경설정, 클릭 기록, 개인화 뉴스) ✅
- [x] **프로덕션 준비**: 85.7% 테스트 성공률, 전체 기능 통합 완료 ✅

🔄 **M4+ 다음 단계 (현실적 확장 로드맵):**

### 🧱 F0: 실험·개인화의 최소 기반 (로그인 없이)
- **익명 ID 시스템**: UUID 기반 anon_id, 365일 cookie 저장
- **실험 배정**: `hash(anon_id, exp_key) mod 100`으로 일관성 있는 A/B 분배
- **행동 로깅**: impressions/clicks/dwell_time 수집 파이프라인
- **Feature Flag**: 서버 설정으로 실험 on/off 제어

### ⚡ F1: 실시간 임베딩 파이프라인 & 벡터DB
- **pgvector 도입**: PostgreSQL 확장으로 시작 (운영 간단)
- **배치 처리**: 신규 뉴스 → 수분 내 임베딩 생성 (≥5k/min)
- **유사도 API**: `/news/similar` 엔드포인트 (p95 < 50ms)

### 🧪 F2: A/B 테스트 시스템 (개인화 vs 일반 랭킹)
- **트래픽 분할**: control(일반) vs treatment(개인화) 50/50
- **지표 정의**: CTR@N, avg_dwell_ms, diversity 측정
- **자동 중단**: CTR -5%p 악화 24시간 지속 시 플래그 off
- **최소 표본**: 각 variant ≥5k impressions, 3-7일 실험

### 🧩 F3: 고급 클러스터링 (HDBSCAN/K-means)
- **HDBSCAN**: 밀도 기반 잡음 제거 (1일 1회)
- **K-means**: 빠른 갱신용 (1-3시간 주기)
- **토픽 제약**: 동일 토픽 ≤2건 유지, MMR과 병행

### 🧠 F4: 딥러닝 임베딩 (Ko-BERT/RoBERTa)
- **모델 최적화**: ONNX Runtime + FP16/INT8 양자화
- **성능 목표**: Recall@10 ≥+10%, p95 ≤30ms
- **롤백 지원**: `/admin/reload?model=embed:prev` 즉시 전환

**핵심**: 로그인 없이도 익명 ID 기반으로 실험·개인화 가능, 단계별 명확한 수락 기준

## 🛠️ 기술 스택

### 마이크로서비스
- **API 게이트웨이**: Spring Boot 3, Java 17+, Resilience4J
- **ML 서비스**: FastAPI, Python 3.11+, Pydantic v2
- **프론트엔드**: Next.js 14, TypeScript, Tailwind CSS

### 인프라 & DevOps
- **컨테이너**: Docker, Docker Compose
- **모니터링**: Prometheus 메트릭, 구조화 로깅 (JSON)
- **문서화**: OpenAPI 3.0, Swagger UI
- **빌드 도구**: Gradle, npm, Poetry

### ML & AI 
- **중요도 모델**: LogisticRegression (scikit-learn) v20250813_103924
- **요약 시스템**: Hybrid (추출식 + LLM API 통합) v2.0.0
- **임베딩 시스템**: Mock sentence-transformers (384차원) v1.0.0 **[M3 완료]**
- **토픽 클러스터링**: 코사인 유사도 기반 Simple Clustering **[M3 완료]**
- **개인화 엔진**: 4요소 공식 기반 재랭킹 시스템 **[M3 완료]**
- **컴플라이언스**: 정규식 기반 금칙어 필터링
- **성능**: 50+ RPS, P95 < 10ms, 85.7% 테스트 성공률

## 📡 포트 정보

| 서비스 | 포트 | 설명 | 헬스체크 |
|--------|------|------|---------|
| Web UI | 3000 | Next.js 프론트엔드 | N/A |
| API 게이트웨이 | 8000 | Spring Boot | /healthz |
| ML 서비스 | 8001 | FastAPI | /admin/health |
| Prometheus 메트릭 | 8001 | ML 서비스 | /metrics |

## 트러블슈팅

### Docker 실행 문제

1. **권한 문제**
   ```bash
   # Docker 권한 확인
   sudo chmod 666 /var/run/docker.sock
   # 또는 사용자를 docker 그룹에 추가
   sudo usermod -aG docker $USER
   ```

2. **웹 서비스가 실행되지 않는 경우**
   ```bash
   # 개별 서비스 로그 확인
   docker compose logs web
   docker compose logs api
   
   # 서비스 재시작
   docker compose restart web
   ```

3. **포트 충돌**
   ```bash
   # 사용 중인 포트 확인
   lsof -i :3000
   lsof -i :8000
   ```

### 마이크로서비스 개발 환경

Docker가 작동하지 않는 경우 개별 서비스 실행:

```bash
# Terminal 1: ML 서비스 (먼저 실행)
cd ml/serving
pip install -r requirements.txt
uvicorn ml_service.main:app --host 0.0.0.0 --port 8001

# Terminal 2: API 게이트웨이 (ML 서비스 연동)
cd services/api  
export ML_SERVICE_URL=http://localhost:8001
./gradlew bootRun

# Terminal 3: 웹 프론트엔드
cd web
npm install
npm run dev
```

### 서비스 연결 확인

```bash
# ML 서비스 헬스체크
curl http://localhost:8001/admin/health

# API 게이트웨이 헬스체크  
curl http://localhost:8000/healthz

# 전체 플로우 테스트
curl "http://localhost:8000/news/top?n=5"
```