# News Agent

주식 뉴스 수집 및 중요도 평가 서비스

## 프로젝트 구조

```
news-agent/
├── services/api/         # FastAPI 백엔드
├── web/                  # Next.js 프론트엔드
├── contracts/            # OpenAPI 스키마 및 계약
└── README.md
```

## 로컬 실행 방법

### 1. API 서버 실행 (FastAPI)

```bash
cd services/api

# Python 가상환경 생성 및 활성화
python -m venv venv
source venv/bin/activate  # Linux/Mac
# venv\Scripts\activate   # Windows

# 의존성 설치
pip install -r requirements.txt

# 서버 시작
uvicorn main:app --reload --port 8000
```

서버 접근: http://localhost:8000
- Health check: `GET /healthz`
- Top news: `GET /news/top?n=10`
- API 문서: http://localhost:8000/docs

### 2. 웹 UI 실행 (Next.js)

```bash
cd web

# 의존성 설치
npm install

# 개발 서버 시작
npm run dev
```

웹 접근: http://localhost:3000

## API 엔드포인트

### GET /news/top

주요 뉴스 목록을 반환합니다.

**Parameters:**
- `n` (int, default=20): 반환할 기사 수 (1-100)
- `tickers` (string, optional): 필터링할 종목 코드 (쉼표 구분)
- `lang` (string, default="ko"): 언어 설정

**Example:**
```bash
curl "http://localhost:8000/news/top?n=5&tickers=005930,035720"
```

## 환경 변수

| 변수명 | 기본값 | 설명 |
|--------|--------|------|
| `API_HOST` | `localhost` | API 서버 호스트 |
| `API_PORT` | `8000` | API 서버 포트 |
| `WEB_HOST` | `localhost` | 웹 서버 호스트 |
| `WEB_PORT` | `3000` | 웹 서버 포트 |

## 브랜치 전략

- `main`: 항상 실행 가능한 상태 유지
- 기능 개발은 feature 브랜치에서 진행 후 PR
- PR 생성 시 수락 기준 체크리스트 확인

## 현재 상태 (1일차 완료)

✅ **완료된 작업:**
- [x] API 서버 기동, `/healthz`, `/news/top` 정상 응답
- [x] UI에서 목업 기사 카드 5개 렌더링
- [x] OpenAPI & JSON 스키마 초안 커밋
- [x] README에 실행 방법 및 포트/환경값 명시

**다음 단계:**
- 뉴스 수집 파이프라인 구현
- AI 요약 및 스코어링 시스템
- 데이터베이스 연동
- 배포 환경 설정

## 기술 스택

- **Backend**: FastAPI, Python 3.11+
- **Frontend**: Next.js, TypeScript, Tailwind CSS
- **API Documentation**: OpenAPI 3.0
- **Development**: uvicorn, npm