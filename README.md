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

## 실행 방법

### 🐳 Docker Compose로 한번에 실행 (권장)

```bash
# 모든 서비스 빌드 및 실행
docker compose up --build

# 백그라운드 실행
docker compose up -d --build

# 로그 확인
docker compose logs -f

# 서비스 중지
docker compose down
```

> **참고**: Docker가 설치되지 않았거나 권한 문제가 있다면 아래 개발 모드로 실행하세요.

접근:
- **Web UI**: http://localhost:3000
- **API Server**: http://localhost:8000  
- **API 문서**: http://localhost:8000/docs

### 🛠️ 개발 모드 (로컬 실행)

#### 1. API 서버 실행 (FastAPI)

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

#### 2. 웹 UI 실행 (Next.js)

```bash
cd web

# 의존성 설치
npm install

# 개발 서버 시작
npm run dev
```

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
| `NEXT_PUBLIC_API_URL` | `http://localhost:8000` | API 서버 URL (웹에서 접근) |
| `NODE_ENV` | `development` | Node.js 환경 모드 |
| `PYTHONPATH` | `/app` | Python 모듈 경로 |

### Docker Compose 환경 변수

Docker Compose 실행 시 자동으로 설정되는 환경 변수들:
- API 서버: `PYTHONPATH=/app`
- Web 서버: `NODE_ENV=production`, `NEXT_PUBLIC_API_URL=http://localhost:8000`

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
- [x] Docker Compose로 한번에 실행 가능한 환경 구성

**다음 단계:**
- 뉴스 수집 파이프라인 구현
- AI 요약 및 스코어링 시스템
- 데이터베이스 연동
- 배포 환경 설정

## 기술 스택

- **Backend**: FastAPI, Python 3.11+
- **Frontend**: Next.js, TypeScript, Tailwind CSS
- **API Documentation**: OpenAPI 3.0
- **Containerization**: Docker, Docker Compose
- **Development**: uvicorn, npm

## 포트 정보

| 서비스 | 포트 | 설명 |
|--------|------|------|
| Web UI | 3000 | Next.js 프론트엔드 |
| API Server | 8000 | FastAPI 백엔드 |
| Health Check | 8000/healthz | API 상태 확인 |

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

### 개발 환경에서 실행

Docker가 작동하지 않는 경우 개발 모드로 실행:

```bash
# Terminal 1: API 서버
cd services/api
python -m venv venv
source venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --reload --port 8000

# Terminal 2: 웹 서버  
cd web
npm install
npm run dev
```