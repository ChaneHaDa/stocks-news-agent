#!/usr/bin/env python3
"""
뉴스 데이터 추출 및 학습 데이터셋 생성
TICKET-101: 중요도 모델 데이터셋 구축
"""

import os
import pandas as pd
import numpy as np
from datetime import datetime, timedelta
from sqlalchemy import create_engine, text
from dotenv import load_dotenv
import json
import re
from typing import Dict, List, Tuple
import logging

# 로깅 설정
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

load_dotenv()

class NewsDataExtractor:
    def __init__(self, db_url: str = None):
        # Docker 환경에서는 직접 연결, 로컬에서는 환경변수 사용
        if db_url:
            self.engine = create_engine(db_url)
        else:
            # 기본적으로 H2 메모리 DB를 사용하는 것 같으니 PostgreSQL 연결은 나중에
            # 지금은 mock 데이터로 시작
            self.engine = None
            logger.warning("DB 연결 없음 - mock 데이터로 시뮬레이션")
    
    def extract_recent_news(self, days: int = 14) -> pd.DataFrame:
        """최근 N일간 뉴스 데이터 추출"""
        if self.engine is None:
            # Mock 데이터 생성 (실제 DB 연결 전까지)
            return self._generate_mock_data(days)
        
        cutoff_date = datetime.now() - timedelta(days=days)
        
        query = text("""
            SELECT 
                n.id,
                n.source,
                n.title,
                n.body,
                n.published_at,
                n.created_at,
                ns.importance,
                ns.reason_json,
                ns.rank_score
            FROM news n
            LEFT JOIN news_score ns ON n.id = ns.news_id
            WHERE n.published_at >= :cutoff_date
            AND n.lang = 'ko'
            ORDER BY n.published_at DESC
        """)
        
        df = pd.read_sql(query, self.engine, params={'cutoff_date': cutoff_date})
        logger.info(f"추출된 뉴스 건수: {len(df)}")
        return df
    
    def _generate_mock_data(self, days: int) -> pd.DataFrame:
        """Mock 학습 데이터 생성 (실제 DB 연결 전까지)"""
        logger.info("Mock 데이터 생성 중...")
        
        # 한국 주요 언론사
        sources = [
            "조선일보", "중앙일보", "동아일보", "한국경제", "매일경제", 
            "연합뉴스", "뉴스1", "헤럴드경제", "파이낸셜뉴스", "이투데이"
        ]
        
        # 샘플 뉴스 제목 패턴
        title_patterns = [
            "삼성전자, 3분기 실적 발표... 영업이익 전년 대비 {}% 증가",
            "SK하이닉스 메모리 시장 점유율 확대, 주가 {}% 상승",
            "현대자동차 전기차 판매량 급증, 목표치 {}% 달성",
            "LG에너지솔루션 배터리 수주 확대, 글로벌 시장서 {}위",
            "네이버 AI 기술 투자 확대, 클라우드 매출 {}% 성장",
            "카카오 플랫폼 이용자 증가, MAU {}만명 돌파",
            "포스코 철강 가격 상승으로 수익성 개선, 영업이익률 {}%",
            "신한은행 디지털 전환 가속화, IT 투자 {}억 증액",
            "KB금융 ESG 경영 강화, 친환경 금융상품 {}개 출시",
            "셀트리온 바이오시밀러 해외 진출, 유럽 시장서 {}% 점유율"
        ]
        
        # 종목 코드 매핑
        ticker_mapping = {
            "삼성전자": "005930", "SK하이닉스": "000660", "현대자동차": "005380",
            "LG에너지솔루션": "373220", "네이버": "035420", "카카오": "035720",
            "포스코": "005490", "신한은행": "055550", "KB금융": "105560", "셀트리온": "068270"
        }
        
        np.random.seed(42)  # 재현 가능한 결과
        
        data = []
        base_time = datetime.now() - timedelta(days=days)
        
        for i in range(1500):  # 충분한 학습 데이터 생성
            # 랜덤 시간 생성
            random_hours = np.random.randint(0, days * 24)
            published_at = base_time + timedelta(hours=random_hours)
            
            # 랜덤 소스 선택
            source = np.random.choice(sources)
            
            # 랜덤 제목 생성
            title_template = np.random.choice(title_patterns)
            random_number = np.random.randint(1, 50)
            title = title_template.format(random_number)
            
            # 종목 추출
            tickers = []
            for company, ticker in ticker_mapping.items():
                if company in title:
                    tickers.append(ticker)
            
            # 본문 생성 (간단한 템플릿)
            body_length = np.random.randint(200, 1000)
            body = f"{title}. " + "관련 상세 내용입니다. " * (body_length // 20)
            
            # 규칙 기반 중요도 계산 (현재 로직 시뮬레이션)
            importance = self._calculate_rule_based_importance(
                source, title, body, tickers, published_at
            )
            
            # reason_json 생성
            reason = {
                "source_weight": self._get_source_weight(source),
                "tickers_hit": len(tickers),
                "keywords_hit": self._count_keywords(title + " " + body),
                "freshness": (datetime.now() - published_at).total_seconds() / 3600
            }
            
            data.append({
                'id': i + 1,
                'source': source,
                'title': title,
                'body': body,
                'published_at': published_at,
                'created_at': published_at + timedelta(minutes=5),
                'importance': importance,
                'reason_json': json.dumps(reason),
                'rank_score': importance * 0.8 + np.random.normal(0, 0.1),
                'tickers': ','.join(tickers) if tickers else None
            })
        
        df = pd.DataFrame(data)
        logger.info(f"Mock 데이터 생성 완료: {len(df)}건")
        return df
    
    def _calculate_rule_based_importance(self, source: str, title: str, body: str, 
                                       tickers: List[str], published_at: datetime) -> float:
        """규칙 기반 중요도 계산 (현재 ImportanceService 로직 시뮬레이션)"""
        score = 0.0
        
        # 소스 가중치
        score += self._get_source_weight(source) * 0.3
        
        # 키워드 매칭
        keywords_hit = self._count_keywords(title + " " + body)
        score += min(keywords_hit / 5.0, 1.0) * 0.25
        
        # 종목 매칭
        score += min(len(tickers) / 3.0, 1.0) * 0.2
        
        # 신속성 (최근일수록 높은 점수)
        hours_old = (datetime.now() - published_at).total_seconds() / 3600
        freshness = max(0, 1.0 - hours_old / 72.0)  # 72시간 기준
        score += freshness * 0.15
        
        # 본문 길이
        body_score = min(len(body) / 1000.0, 1.0) * 0.1
        score += body_score
        
        # 노이즈 추가 (실제 환경 시뮬레이션)
        score += np.random.normal(0, 0.05)
        
        return max(0.0, min(1.0, score))
    
    def _get_source_weight(self, source: str) -> float:
        """소스별 가중치"""
        weights = {
            "조선일보": 0.9, "중앙일보": 0.9, "동아일보": 0.8,
            "한국경제": 0.95, "매일경제": 0.9, "연합뉴스": 0.95,
            "뉴스1": 0.7, "헤럴드경제": 0.8, "파이낸셜뉴스": 0.85, "이투데이": 0.8
        }
        return weights.get(source, 0.6)
    
    def _count_keywords(self, text: str) -> int:
        """중요 키워드 카운트"""
        keywords = [
            "실적", "영업이익", "매출", "주가", "상승", "하락", "투자", "인수", "합병",
            "IPO", "상장", "배당", "증자", "감자", "분할", "경영권", "지배구조"
        ]
        count = 0
        text_lower = text.lower()
        for keyword in keywords:
            count += text_lower.count(keyword)
        return count
    
    def create_weak_labels(self, df: pd.DataFrame) -> pd.DataFrame:
        """Weak labeling: 상위 20% = 1, 하위 20% = 0, 중간 60% 제외"""
        df_sorted = df.sort_values('importance', ascending=False)
        
        n_total = len(df_sorted)
        n_top = int(n_total * 0.2)
        n_bottom = int(n_total * 0.2)
        
        # 라벨 초기화
        df_sorted['label'] = -1  # 미사용 데이터
        
        # 상위 20% -> 1 (중요)
        df_sorted.iloc[:n_top, df_sorted.columns.get_loc('label')] = 1
        
        # 하위 20% -> 0 (덜 중요)
        df_sorted.iloc[-n_bottom:, df_sorted.columns.get_loc('label')] = 0
        
        # 라벨링된 데이터만 반환
        labeled_df = df_sorted[df_sorted['label'] != -1].copy()
        
        logger.info(f"라벨링 완료: 총 {len(labeled_df)}건 (긍정: {(labeled_df['label']==1).sum()}, 부정: {(labeled_df['label']==0).sum()})")
        
        return labeled_df
    
    def feature_engineering(self, df: pd.DataFrame) -> Tuple[np.ndarray, np.ndarray]:
        """피처 엔지니어링"""
        from sklearn.feature_extraction.text import TfidfVectorizer
        from sklearn.preprocessing import StandardScaler
        
        logger.info("피처 엔지니어링 시작...")
        
        # 텍스트 피처 (TF-IDF)
        text_features = df['title'] + " " + df['body'].fillna("")
        
        # 한국어 불용어 (간단한 버전)
        korean_stopwords = ['의', '가', '이', '은', '는', '을', '를', '에', '와', '과', '로', '으로', '에서']
        
        tfidf = TfidfVectorizer(
            max_features=1000,
            ngram_range=(1, 2),
            stop_words=korean_stopwords,
            min_df=2,
            max_df=0.8
        )
        
        tfidf_features = tfidf.fit_transform(text_features).toarray()
        
        # 수치 피처
        numerical_features = []
        
        # reason_json 파싱해서 피처 추출
        for reason_json in df['reason_json']:
            try:
                reason = json.loads(reason_json) if reason_json else {}
                numerical_features.append([
                    reason.get('source_weight', 0),
                    reason.get('tickers_hit', 0),
                    reason.get('keywords_hit', 0),
                    reason.get('freshness', 0),
                    len(df.loc[df['reason_json'] == reason_json, 'title'].iloc[0]) if not df.empty else 0,  # 제목 길이
                    len(df.loc[df['reason_json'] == reason_json, 'body'].iloc[0] or "") if not df.empty else 0,  # 본문 길이
                ])
            except:
                numerical_features.append([0, 0, 0, 0, 0, 0])
        
        numerical_features = np.array(numerical_features)
        
        # 수치 피처 정규화
        scaler = StandardScaler()
        numerical_features_scaled = scaler.fit_transform(numerical_features)
        
        # 텍스트 + 수치 피처 결합
        features = np.hstack([tfidf_features, numerical_features_scaled])
        
        labels = df['label'].values
        
        logger.info(f"피처 행렬 크기: {features.shape}")
        logger.info(f"라벨 분포: {np.bincount(labels)}")
        
        return features, labels
    
    def save_dataset(self, df: pd.DataFrame, features: np.ndarray, labels: np.ndarray, 
                    output_dir: str = "data"):
        """데이터셋 저장"""
        os.makedirs(output_dir, exist_ok=True)
        
        # 원본 데이터프레임 저장
        df.to_csv(f"{output_dir}/news_dataset.csv", index=False)
        
        # 피처/라벨 저장
        np.save(f"{output_dir}/features.npy", features)
        np.save(f"{output_dir}/labels.npy", labels)
        
        # 메타데이터 저장
        metadata = {
            "created_at": datetime.now().isoformat(),
            "total_samples": len(df),
            "positive_samples": int((labels == 1).sum()),
            "negative_samples": int((labels == 0).sum()),
            "feature_dim": features.shape[1],
            "tfidf_features": 1000,
            "numerical_features": 6
        }
        
        with open(f"{output_dir}/metadata.json", 'w') as f:
            json.dump(metadata, f, indent=2, ensure_ascii=False)
        
        logger.info(f"데이터셋 저장 완료: {output_dir}/")
        return metadata

def main():
    """메인 실행 함수"""
    logger.info("=== 뉴스 중요도 모델 데이터셋 구축 시작 ===")
    
    # 1. 데이터 추출
    extractor = NewsDataExtractor()
    df = extractor.extract_recent_news(days=14)
    
    # 2. Weak labeling
    labeled_df = extractor.create_weak_labels(df)
    
    # 3. 피처 엔지니어링
    features, labels = extractor.feature_engineering(labeled_df)
    
    # 4. 데이터셋 저장
    metadata = extractor.save_dataset(labeled_df, features, labels)
    
    logger.info("=== 데이터셋 구축 완료 ===")
    logger.info(f"총 샘플: {metadata['total_samples']}")
    logger.info(f"긍정 샘플: {metadata['positive_samples']}")
    logger.info(f"부정 샘플: {metadata['negative_samples']}")
    logger.info(f"피처 차원: {metadata['feature_dim']}")
    
    return metadata

if __name__ == "__main__":
    main()