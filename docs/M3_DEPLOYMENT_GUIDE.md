# M3 배포 가이드

## 📋 개요

M3 마일스톤의 임베딩 기반 지능형 추천 시스템을 프로덕션 환경에 배포하기 위한 완전한 가이드입니다.

---

## 🏗️ 시스템 요구사항

### 최소 요구사항
- **CPU**: 4 cores (2.0GHz 이상)
- **Memory**: 8GB RAM
- **Storage**: 50GB SSD
- **Network**: 100Mbps 대역폭

### 권장 요구사항
- **CPU**: 8 cores (3.0GHz 이상)
- **Memory**: 16GB RAM
- **Storage**: 100GB NVMe SSD
- **Network**: 1Gbps 대역폭

### 소프트웨어 요구사항
```bash
# 필수
Docker 24.0+
Docker Compose 2.20+
Git 2.30+

# 권장 (모니터링)
curl, wget
htop, iotop
jq (JSON 처리)
```

---

## 🚀 빠른 시작

### 1. 저장소 클론
```bash
git clone https://github.com/your-org/stocks-news-agent.git
cd stocks-news-agent
git checkout main  # M3 마일스톤 포함
```

### 2. 환경 설정
```bash
# 환경 변수 파일 생성
cp .env.example .env

# 필요에 따라 수정
vim .env
```

### 3. 전체 스택 실행
```bash
# 빌드 및 시작
docker compose up --build -d

# 상태 확인
docker compose ps
```

### 4. 서비스 헬스체크
```bash
# API 서비스 확인
curl http://localhost:8000/healthz

# ML 서비스 확인  
curl http://localhost:8001/admin/health

# 전체 상태 확인
curl http://localhost:8000/admin/status
```

---

## ⚙️ 환경 설정

### 환경 변수 (.env)

```bash
# === 기본 설정 ===
COMPOSE_PROJECT_NAME=stocks-news-agent
NODE_ENV=production

# === 데이터베이스 설정 ===
DATABASE_URL=jdbc:postgresql://postgres:5432/newsagent
DATABASE_USERNAME=newsagent
DATABASE_PASSWORD=your_secure_password
DATABASE_DRIVER=org.postgresql.Driver

# === ML 서비스 설정 ===
ML_SERVICE_URL=http://ml-service:8001
ENABLE_IMPORTANCE=true
ENABLE_SUMMARIZE=true
ENABLE_EMBED=true

# === RSS 수집 설정 ===
RSS_COLLECTION_ENABLED=true
RSS_COLLECTION_CRON=0 */10 * * * *
RSS_COLLECTION_SCHEDULED=true

# === 토픽 클러스터링 설정 ===
TOPIC_CLUSTERING_ENABLED=true
TOPIC_CLUSTERING_CRON=0 0 */6 * * *

# === 로깅 설정 ===
LOG_LEVEL=INFO
WEB_LOG_LEVEL=WARN
SQL_LOG_LEVEL=WARN

# === 성능 튜닝 ===
JAVA_OPTS=-Xmx1g -Xms512m
MAX_BATCH_SIZE=50
REQUEST_TIMEOUT=60
```

### Docker Compose 프로덕션 설정

```yaml
# docker-compose.prod.yml
version: '3.8'

services:
  postgres:
    image: postgres:15-alpine
    container_name: news-agent-postgres
    environment:
      POSTGRES_DB: newsagent
      POSTGRES_USER: newsagent
      POSTGRES_PASSWORD: ${DATABASE_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    ports:
      - "5432:5432"
    restart: unless-stopped
    networks:
      - news-agent-network

  api:
    build:
      context: ./services/api
      dockerfile: Dockerfile
    container_name: news-agent-api
    ports:
      - "8000:8000"
    environment:
      - JAVA_OPTS=${JAVA_OPTS:-"-Xmx1g -Xms512m"}
      - DATABASE_URL=jdbc:postgresql://postgres:5432/newsagent
      - DATABASE_USERNAME=${DATABASE_USERNAME}
      - DATABASE_PASSWORD=${DATABASE_PASSWORD}
      - DATABASE_DRIVER=org.postgresql.Driver
      - ML_SERVICE_URL=http://ml-service:8001
    healthcheck:
      test: ["CMD-SHELL", "wget -q --spider http://localhost:8000/healthz || exit 1"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 90s
    restart: unless-stopped
    depends_on:
      postgres:
        condition: service_started
      ml-service:
        condition: service_healthy
    networks:
      - news-agent-network

  ml-service:
    build:
      context: ./ml/serving
      dockerfile: Dockerfile
    container_name: news-agent-ml
    ports:
      - "8001:8001"
    environment:
      - ML_DEBUG=false
      - ML_MODELS_DIR=/app/models
      - ENABLE_IMPORTANCE=true
      - ENABLE_SUMMARIZE=true
      - ENABLE_EMBED=true
      - LOG_LEVEL=INFO
      - LOG_FORMAT=json
      - MAX_BATCH_SIZE=50
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:8001/admin/health || exit 1"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 45s
    restart: unless-stopped
    networks:
      - news-agent-network

  web:
    build:
      context: ./web
      dockerfile: Dockerfile
    container_name: news-agent-web
    ports:
      - "3000:3000"
    environment:
      - NODE_ENV=production
      - NEXT_PUBLIC_API_URL=http://localhost:8000
    depends_on:
      api:
        condition: service_healthy
    restart: unless-stopped
    networks:
      - news-agent-network

  # Redis for caching (선택사항)
  redis:
    image: redis:7-alpine
    container_name: news-agent-redis
    ports:
      - "6379:6379"
    command: redis-server --appendonly yes
    volumes:
      - redis_data:/data
    restart: unless-stopped
    networks:
      - news-agent-network

volumes:
  postgres_data:
  redis_data:

networks:
  news-agent-network:
    driver: bridge
```

---

## 📊 프로덕션 배포

### 1. 프로덕션 환경 준비
```bash
# 프로덕션 설정으로 실행
docker compose -f docker-compose.prod.yml up --build -d

# 데이터베이스 초기화 확인
docker compose -f docker-compose.prod.yml logs api | grep -i flyway
```

### 2. 초기 데이터 설정
```bash
# 뉴스 수집 테스트
curl -X POST http://localhost:8000/admin/ingest

# 임베딩 생성 확인
curl -X POST http://localhost:8001/v1/embed \
  -H "Content-Type: application/json" \
  -d '{"items":[{"id":"test","text":"삼성전자 실적"}]}'

# 토픽 클러스터링 실행
curl -X POST http://localhost:8000/admin/clustering
```

### 3. 로드 밸런싱 설정 (Nginx)

```nginx
# /etc/nginx/sites-available/newsagent
upstream api_backend {
    server localhost:8000;
    # 추가 API 인스턴스
    # server localhost:8001;
}

upstream web_backend {
    server localhost:3000;
}

server {
    listen 80;
    server_name your-domain.com;

    # API 요청
    location /api/ {
        proxy_pass http://api_backend/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_connect_timeout 60s;
        proxy_read_timeout 60s;
    }

    # 웹 앱
    location / {
        proxy_pass http://web_backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        
        # WebSocket support for Next.js dev
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_cache_bypass $http_upgrade;
    }

    # 정적 파일 캐싱
    location ~* \.(jpg|jpeg|png|gif|ico|css|js)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }
}
```

### 4. SSL 인증서 설정 (Let's Encrypt)
```bash
# Certbot 설치
sudo apt install certbot python3-certbot-nginx

# SSL 인증서 발급
sudo certbot --nginx -d your-domain.com

# 자동 갱신 설정
sudo crontab -e
# 추가: 0 12 * * * /usr/bin/certbot renew --quiet
```

---

## 📈 모니터링

### 1. 기본 상태 모니터링
```bash
#!/bin/bash
# scripts/health_check.sh

echo "=== 서비스 상태 확인 ==="
docker compose ps

echo -e "\n=== API 헬스체크 ==="
curl -s http://localhost:8000/healthz | jq '.'

echo -e "\n=== ML 서비스 상태 ==="
curl -s http://localhost:8001/admin/health | jq '.status'

echo -e "\n=== 시스템 리소스 ==="
docker stats --no-stream --format "table {{.Container}}\t{{.CPUPerc}}\t{{.MemUsage}}"

echo -e "\n=== 디스크 사용량 ==="
df -h | grep -E "(/$|/var/lib/docker)"
```

### 2. Prometheus + Grafana (선택사항)

```yaml
# monitoring/docker-compose.monitoring.yml
version: '3.8'

services:
  prometheus:
    image: prom/prometheus:latest
    container_name: prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
      - prometheus_data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
      - '--web.console.libraries=/etc/prometheus/console_libraries'
      - '--web.console.templates=/etc/prometheus/consoles'

  grafana:
    image: grafana/grafana:latest
    container_name: grafana
    ports:
      - "3001:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
    volumes:
      - grafana_data:/var/lib/grafana

volumes:
  prometheus_data:
  grafana_data:
```

```yaml
# monitoring/prometheus.yml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'news-agent-api'
    static_configs:
      - targets: ['host.docker.internal:8000']
    metrics_path: '/actuator/prometheus'
    
  - job_name: 'news-agent-ml'
    static_configs:
      - targets: ['host.docker.internal:8001']
    metrics_path: '/metrics'
```

### 3. 로그 관리

```bash
# 로그 로테이션 설정
# /etc/docker/daemon.json
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "3"
  }
}

# 로그 확인 스크립트
#!/bin/bash
# scripts/logs.sh

SERVICE=${1:-"all"}

case $SERVICE in
  "api")
    docker compose logs -f --tail=100 api
    ;;
  "ml")
    docker compose logs -f --tail=100 ml-service
    ;;
  "web")
    docker compose logs -f --tail=100 web
    ;;
  "errors")
    docker compose logs --tail=500 | grep -i error
    ;;
  *)
    docker compose logs -f --tail=50
    ;;
esac
```

---

## 🔧 백업 및 복구

### 1. 데이터베이스 백업
```bash
#!/bin/bash
# scripts/backup_db.sh

BACKUP_DIR="/backup/newsagent"
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="$BACKUP_DIR/newsagent_$DATE.sql"

mkdir -p $BACKUP_DIR

# PostgreSQL 백업
docker exec news-agent-postgres pg_dump \
  -U newsagent \
  -d newsagent \
  --no-owner \
  --no-privileges \
  --clean \
  --if-exists > $BACKUP_FILE

# 압축
gzip $BACKUP_FILE

echo "백업 완료: $BACKUP_FILE.gz"

# 오래된 백업 정리 (7일 이상)
find $BACKUP_DIR -name "*.sql.gz" -mtime +7 -delete
```

### 2. 전체 시스템 백업
```bash
#!/bin/bash
# scripts/backup_full.sh

BACKUP_DIR="/backup/newsagent"
DATE=$(date +%Y%m%d_%H%M%S)

# 애플리케이션 코드
tar -czf "$BACKUP_DIR/app_$DATE.tar.gz" \
  --exclude='node_modules' \
  --exclude='.git' \
  --exclude='target' \
  --exclude='build' \
  .

# Docker 볼륨 백업
docker run --rm \
  -v stocks-news-agent_postgres_data:/source \
  -v $BACKUP_DIR:/backup \
  alpine tar -czf /backup/postgres_volumes_$DATE.tar.gz -C /source .

echo "전체 백업 완료: $BACKUP_DIR"
```

### 3. 복구 절차
```bash
#!/bin/bash
# scripts/restore.sh

BACKUP_FILE=$1

if [ -z "$BACKUP_FILE" ]; then
  echo "Usage: $0 <backup_file>"
  exit 1
fi

# 서비스 중지
docker compose down

# 데이터베이스 복구
zcat $BACKUP_FILE | docker exec -i news-agent-postgres \
  psql -U newsagent -d newsagent

# 서비스 재시작
docker compose up -d

echo "복구 완료"
```

---

## 🔄 업데이트 및 롤백

### 1. 무중단 업데이트
```bash
#!/bin/bash
# scripts/rolling_update.sh

echo "=== 롤링 업데이트 시작 ==="

# 새 버전 빌드
docker compose build

# API 서비스 업데이트
echo "API 서비스 업데이트 중..."
docker compose up -d --no-deps api

# 헬스체크
echo "헬스체크 대기 중..."
for i in {1..30}; do
  if curl -s http://localhost:8000/healthz > /dev/null; then
    echo "API 서비스 정상 구동 확인"
    break
  fi
  sleep 2
done

# ML 서비스 업데이트
echo "ML 서비스 업데이트 중..."
docker compose up -d --no-deps ml-service

# 웹 서비스 업데이트
echo "웹 서비스 업데이트 중..."
docker compose up -d --no-deps web

echo "=== 롤링 업데이트 완료 ==="
```

### 2. 롤백 절차
```bash
#!/bin/bash
# scripts/rollback.sh

PREVIOUS_TAG=${1:-"previous"}

echo "=== 롤백 시작: $PREVIOUS_TAG ==="

# 이전 이미지로 롤백
docker tag stocks-news-agent-api:$PREVIOUS_TAG stocks-news-agent-api:latest
docker tag stocks-news-agent-ml-service:$PREVIOUS_TAG stocks-news-agent-ml-service:latest
docker tag stocks-news-agent-web:$PREVIOUS_TAG stocks-news-agent-web:latest

# 서비스 재시작
docker compose up -d

echo "=== 롤백 완료 ==="
```

---

## 🚨 트러블슈팅

### 1. 공통 문제 해결

#### 메모리 부족
```bash
# 메모리 사용량 확인
docker stats

# 불필요한 컨테이너 정리
docker system prune -f

# JVM 힙 크기 조정
export JAVA_OPTS="-Xmx2g -Xms1g"
```

#### 디스크 공간 부족
```bash
# Docker 정리
docker system prune -a -f --volumes

# 로그 정리
docker compose logs --since 24h > /tmp/recent_logs.txt
docker compose down && docker compose up -d
```

#### 네트워크 문제
```bash
# 네트워크 재생성
docker network rm stocks-news-agent_news-agent-network
docker compose up -d
```

### 2. 서비스별 트러블슈팅

#### API 서비스 오류
```bash
# 로그 확인
docker compose logs api | tail -100

# 데이터베이스 연결 테스트
docker exec news-agent-postgres psql -U newsagent -c "SELECT version();"

# JVM 힙 덤프 (메모리 분석)
docker exec news-agent-api jmap -dump:format=b,file=/tmp/heap.hprof 1
```

#### ML 서비스 오류
```bash
# 모델 로딩 상태 확인
curl http://localhost:8001/admin/health | jq '.models'

# 메모리 사용량 확인
docker exec news-agent-ml cat /proc/meminfo

# Python 프로세스 상태
docker exec news-agent-ml ps aux
```

#### 웹 서비스 오류
```bash
# Next.js 빌드 로그 확인
docker compose logs web | grep -i error

# Node.js 메모리 확인
docker exec news-agent-web node -e "console.log(process.memoryUsage())"
```

---

## 📊 성능 최적화

### 1. 데이터베이스 최적화
```sql
-- 인덱스 사용량 확인
SELECT schemaname, tablename, indexname, idx_scan, idx_tup_read, idx_tup_fetch 
FROM pg_stat_user_indexes 
ORDER BY idx_scan DESC;

-- 슬로우 쿼리 확인 (postgresql.conf 설정 필요)
SELECT query, calls, total_time, mean_time 
FROM pg_stat_statements 
ORDER BY total_time DESC 
LIMIT 10;

-- 커넥션 풀 모니터링
SELECT state, count(*) 
FROM pg_stat_activity 
GROUP BY state;
```

### 2. JVM 튜닝
```bash
# application.yml 또는 환경변수
JAVA_OPTS="-Xmx2g -Xms1g \
           -XX:+UseG1GC \
           -XX:MaxGCPauseMillis=200 \
           -XX:+HeapDumpOnOutOfMemoryError \
           -XX:HeapDumpPath=/tmp/heap.hprof"
```

### 3. 캐싱 설정
```yaml
# Redis 캐시 설정 추가
spring:
  cache:
    type: redis
  redis:
    host: redis
    port: 6379
    timeout: 2000
    lettuce:
      pool:
        max-active: 8
        max-idle: 8
        min-idle: 0
```

---

## 🔐 보안 설정

### 1. 방화벽 설정
```bash
# UFW 기본 설정
sudo ufw default deny incoming
sudo ufw default allow outgoing

# 필요한 포트만 개방
sudo ufw allow 22          # SSH
sudo ufw allow 80          # HTTP
sudo ufw allow 443         # HTTPS
sudo ufw allow 8000        # API (개발용)

sudo ufw enable
```

### 2. Docker 보안
```bash
# 비root 사용자로 Docker 실행
sudo usermod -aG docker $USER

# Docker daemon 보안 설정
# /etc/docker/daemon.json
{
  "icc": false,
  "userland-proxy": false,
  "no-new-privileges": true
}
```

### 3. 애플리케이션 보안
```yaml
# 민감 정보 분리
secrets:
  db_password:
    external: true
  jwt_secret:
    external: true

services:
  api:
    secrets:
      - db_password
      - jwt_secret
```

---

*배포 가이드 버전: M3-v1.0*  
*최종 업데이트: 2025-01-14*  
*대상 환경: Production Ready*