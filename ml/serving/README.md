# ML Serving Service

FastAPI 기반 머신러닝 서비스로 뉴스 중요도, 요약, 임베딩 기능을 제공합니다.

## 🚀 빠른 시작

### Docker로 실행
```bash
# 전체 서비스 실행 (root 디렉토리에서)
docker-compose up --build

# ML 서비스만 실행
docker-compose up ml-service
```

### 로컬 개발
```bash
# 의존성 설치
pip install -r requirements.txt

# 서비스 실행
uvicorn ml_service.main:app --host 0.0.0.0 --port 8001 --reload
```

## 📋 API 엔드포인트

### 헬스체크
```bash
# 서비스 상태 확인
curl http://localhost:8001/admin/health

# 커스텀 메트릭
curl http://localhost:8001/admin/metrics

# Prometheus 메트릭
curl http://localhost:8001/metrics
```

### 중요도 스코어링
```bash
curl -X POST http://localhost:8001/v1/importance:score \
  -H "Content-Type: application/json" \
  -d '{
    "items": [{
      "id": "1",
      "title": "삼성전자 주가 상승",
      "body": "삼성전자가 오늘 크게 상승했습니다.",
      "source": "테스트",
      "published_at": "2024-01-01T00:00:00Z"
    }]
  }'
```

### 요약 생성
```bash
curl -X POST http://localhost:8001/v1/summarize \
  -H "Content-Type: application/json" \
  -d '{
    "id": "1",
    "title": "삼성전자 실적 발표",
    "body": "삼성전자가 분기 실적을 발표했습니다. 매출이 크게 증가했습니다.",
    "tickers": ["005930"],
    "options": {"style": "extractive", "max_length": 240}
  }'
```

### 임베딩 생성
```bash
curl -X POST http://localhost:8001/v1/embed \
  -H "Content-Type: application/json" \
  -d '{
    "items": [{
      "id": "1", 
      "text": "삼성전자 주가 상승 뉴스"
    }]
  }'
```

## ⚙️ 설정

환경변수를 통해 설정을 변경할 수 있습니다:

```bash
# 기본 설정 복사
cp .env.example .env

# 설정 수정
vim .env
```

주요 설정:
- `ENABLE_IMPORTANCE`: 중요도 서비스 활성화
- `ENABLE_SUMMARIZE`: 요약 서비스 활성화  
- `ENABLE_EMBED`: 임베딩 서비스 활성화
- `LOG_LEVEL`: 로그 레벨 (DEBUG, INFO, WARN, ERROR)
- `ML_MODELS_DIR`: 모델 파일 저장 경로

## 📊 모니터링

### Prometheus 메트릭
- `ml_requests_total`: 총 요청 수
- `ml_request_duration_seconds`: 요청 처리 시간
- `ml_model_inference_duration_seconds`: 모델 추론 시간
- `ml_cache_hits_total`: 캐시 히트 수
- `ml_errors_total`: 에러 발생 수

### 로그
구조화된 JSON 로그를 출력합니다:
```json
{
  "timestamp": "2024-01-01T00:00:00Z",
  "level": "info", 
  "message": "Processing importance request",
  "item_count": 5
}
```

## 🧪 테스트

```bash
# 단위 테스트 실행
pytest

# 커버리지 포함
pytest --cov=ml_service

# 특정 테스트
pytest tests/test_importance.py
```

## 🔧 개발

### 코드 포맷팅
```bash
# Black 포맷터
black ml_service/

# Flake8 린터  
flake8 ml_service/
```

### 모델 리로드
```bash
# 새 버전으로 모델 리로드
curl -X POST "http://localhost:8001/admin/reload?version=v1.2.0"
```

## 📁 프로젝트 구조

```
ml_service/
├── main.py              # FastAPI 애플리케이션
├── core/
│   ├── config.py        # 설정 관리
│   ├── logging.py       # 로깅 설정
│   └── metrics.py       # Prometheus 메트릭
├── api/
│   ├── admin.py         # 관리자 API
│   └── v1.py           # ML API v1
└── services/
    ├── model_manager.py # 모델 매니저
    ├── importance_service.py  # 중요도 서비스
    ├── summarize_service.py   # 요약 서비스  
    └── embed_service.py       # 임베딩 서비스
```

## 🚨 문제 해결

### 일반적인 문제
1. **모델 로딩 실패**: `ML_MODELS_DIR` 경로와 파일 권한 확인
2. **메모리 부족**: 배치 크기(`MAX_BATCH_SIZE`) 줄이기
3. **타임아웃**: `REQUEST_TIMEOUT` 값 증가

### 로그 확인
```bash
# Docker 로그
docker-compose logs ml-service

# 실시간 로그
docker-compose logs -f ml-service
```

## 📈 성능

### 목표 지표
- 중요도 스코어링: p95 < 50ms (단건)
- 요약 생성: p95 < 1.5s (캐시 제외)  
- 임베딩: p95 < 30ms (단건, CPU)

### 최적화 팁
1. 배치 처리 활용 (`max_batch_size`)
2. 캐시 TTL 조정
3. 모델 경량화 (ONNX 등)
4. GPU 사용 (임베딩 모델)