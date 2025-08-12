from fastapi import FastAPI, Query
from fastapi.middleware.cors import CORSMiddleware
from typing import List, Optional
from datetime import datetime, timezone

app = FastAPI(title="News Agent API", version="0.1.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # 개발용 와일드카드
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.get("/healthz")
def healthz():
    return {"ok": True}

@app.get("/news/top")
def get_top_news(n: int = 20,
                 tickers: Optional[str] = Query(None, description="comma-separated"),
                 lang: str = "ko"):
    items = [
        {
            "id": "mock-1",
            "source": "Yonhap",
            "title": "삼성전자 2분기 실적 요약",
            "url": "https://example.com/a",
            "published_at": datetime.now(timezone.utc).isoformat(),
            "tickers": ["005930"],
            "summary": "목업 요약: 실적 발표 핵심만 두세 문장.",
            "importance": 0.83,
            "reason": {"source_weight":1.0,"tickers_hit":0.5,"keywords_hit":0.6,"freshness":1.0}
        },
        {
            "id": "mock-2",
            "source": "MK",
            "title": "카카오 사업 업데이트",
            "url": "https://example.com/b",
            "published_at": datetime.now(timezone.utc).isoformat(),
            "tickers": ["035720"],
            "summary": "목업 요약: 서비스 개편과 비용 구조 이슈.",
            "importance": 0.72,
            "reason": {"source_weight":0.9,"tickers_hit":0.5,"keywords_hit":0.3,"freshness":0.5}
        },
        {
            "id": "mock-3", 
            "source": "KED",
            "title": "반도체 업계 동향 분석",
            "url": "https://example.com/c",
            "published_at": datetime.now(timezone.utc).isoformat(),
            "tickers": ["000660"],
            "summary": "목업 요약: 글로벌 수요 회복세와 메모리 가격 전망.",
            "importance": 0.78,
            "reason": {"source_weight":0.8,"tickers_hit":0.7,"keywords_hit":0.8,"freshness":0.9}
        },
        {
            "id": "mock-4",
            "source": "ET News",
            "title": "네이버 클라우드 확장 계획",
            "url": "https://example.com/d", 
            "published_at": datetime.now(timezone.utc).isoformat(),
            "tickers": ["035420"],
            "summary": "목업 요약: AI 서비스 강화와 해외 진출 로드맵.",
            "importance": 0.65,
            "reason": {"source_weight":0.7,"tickers_hit":0.4,"keywords_hit":0.5,"freshness":0.8}
        },
        {
            "id": "mock-5",
            "source": "Money Today",
            "title": "LG에너지솔루션 배터리 신기술",
            "url": "https://example.com/e",
            "published_at": datetime.now(timezone.utc).isoformat(),
            "tickers": ["373220"],
            "summary": "목업 요약: 차세대 배터리 기술과 양산 일정 공개.",
            "importance": 0.91,
            "reason": {"source_weight":0.6,"tickers_hit":0.9,"keywords_hit":0.7,"freshness":1.0}
        }
    ]
    return {"items": items[:n], "next_cursor": None}