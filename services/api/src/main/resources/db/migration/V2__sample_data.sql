-- Sample news data for development

INSERT INTO news (id, source, url, published_at, title, body, dedup_key, lang, created_at) VALUES
(1, 'Yonhap Economy', 'https://example.com/news/1', CURRENT_TIMESTAMP - INTERVAL '1' HOUR, 
 '삼성전자, 3분기 영업이익 전년비 277% 증가', 
 '삼성전자가 3분기 영업이익이 전년 동기 대비 277% 증가한 10조 4000억원을 기록했다고 발표했다. 메모리 반도체 업황 회복과 AI 수요 증가가 주요 요인으로 분석된다. 특히 HBM(고대역폭메모리) 매출이 크게 증가하며 실적 개선을 견인했다. 회사 측은 "AI 서버용 메모리 수요가 예상보다 빠르게 증가하고 있다"고 설명했다.',
 'samsung_q3_2024', 'ko', CURRENT_TIMESTAMP - INTERVAL '1' HOUR),

(2, 'Maeil Economy', 'https://example.com/news/2', CURRENT_TIMESTAMP - INTERVAL '2' HOUR,
 'LG화학, 전기차 배터리 수주 확대로 성장 동력 확보',
 'LG화학이 글로벌 완성차 업체들과 전기차 배터리 공급 계약을 연달아 체결하며 중장기 성장 동력을 확보했다. 회사는 올해 말까지 총 100GWh 규모의 추가 수주를 목표로 하고 있다. 특히 테슬라, BMW, 현대차 등 주요 고객사와의 파트너십 강화가 주요 성과로 평가된다.',
 'lgchem_battery_2024', 'ko', CURRENT_TIMESTAMP - INTERVAL '2' HOUR),

(3, 'Hankyung', 'https://example.com/news/3', CURRENT_TIMESTAMP - INTERVAL '3' HOUR,
 'SK하이닉스, AI 메모리 특허 포트폴리오 강화',
 'SK하이닉스가 AI 전용 메모리 관련 핵심 특허 50여건을 확보하며 기술 경쟁력을 강화했다고 발표했다. 이번 특허들은 차세대 HBM과 CXL 메모리 기술에 집중되어 있으며, AI 데이터센터 시장 공략에 활용될 예정이다. 회사는 2025년까지 AI 메모리 분야에서 100억달러 매출 달성을 목표로 하고 있다.',
 'skhynix_ai_memory_2024', 'ko', CURRENT_TIMESTAMP - INTERVAL '3' HOUR),

(4, 'Yonhap Economy', 'https://example.com/news/4', CURRENT_TIMESTAMP - INTERVAL '4' HOUR,
 'NAVER, 클라우드 서비스 매출 전년비 35% 성장',
 'NAVER가 클라우드 플랫폼 서비스 매출이 전년 동기 대비 35% 증가했다고 발표했다. 특히 AI 기반 클라우드 솔루션에 대한 기업 고객 수요가 급증하며 성장을 견인했다. 회사는 하이퍼클로바X를 활용한 엔터프라이즈 AI 서비스를 본격 출시하며 B2B 사업 확장에 나서고 있다.',
 'naver_cloud_2024', 'ko', CURRENT_TIMESTAMP - INTERVAL '4' HOUR),

(5, 'Maeil Economy', 'https://example.com/news/5', CURRENT_TIMESTAMP - INTERVAL '5' HOUR,
 '카카오, 모빌리티 플랫폼 해외 진출 본격화',
 '카카오가 동남아시아 모빌리티 시장 진출을 본격화한다고 발표했다. 싱가포르와 태국을 시작으로 카카오T 서비스를 현지화해 출시할 예정이다. 회사는 현지 파트너사와의 협력을 통해 3년 내 동남아 주요 도시 10곳에 서비스를 확대할 계획이라고 밝혔다.',
 'kakao_mobility_sea_2024', 'ko', CURRENT_TIMESTAMP - INTERVAL '5' HOUR);

-- Sample news scores
INSERT INTO news_score (news_id, importance, reason_json, rank_score, created_at, updated_at) VALUES
(1, 8.5, '{"source_weight": 0.9, "tickers_hit": 0.8, "keywords_hit": 0.7, "freshness": 1.0, "tickers_found": ["005930"], "ticker_count": 1}', 8.2, CURRENT_TIMESTAMP - INTERVAL '1' HOUR, CURRENT_TIMESTAMP - INTERVAL '1' HOUR),
(2, 7.3, '{"source_weight": 0.8, "tickers_hit": 0.6, "keywords_hit": 0.8, "freshness": 0.9, "tickers_found": ["051910"], "ticker_count": 1}', 7.1, CURRENT_TIMESTAMP - INTERVAL '2' HOUR, CURRENT_TIMESTAMP - INTERVAL '2' HOUR),
(3, 7.8, '{"source_weight": 0.7, "tickers_hit": 0.7, "keywords_hit": 0.6, "freshness": 0.8, "tickers_found": ["000660"], "ticker_count": 1}', 7.5, CURRENT_TIMESTAMP - INTERVAL '3' HOUR, CURRENT_TIMESTAMP - INTERVAL '3' HOUR),
(4, 6.9, '{"source_weight": 0.9, "tickers_hit": 0.5, "keywords_hit": 0.6, "freshness": 0.7, "tickers_found": ["035420"], "ticker_count": 1}', 6.7, CURRENT_TIMESTAMP - INTERVAL '4' HOUR, CURRENT_TIMESTAMP - INTERVAL '4' HOUR),
(5, 6.2, '{"source_weight": 0.8, "tickers_hit": 0.4, "keywords_hit": 0.5, "freshness": 0.6, "tickers_found": ["035720"], "ticker_count": 1}', 6.0, CURRENT_TIMESTAMP - INTERVAL '5' HOUR, CURRENT_TIMESTAMP - INTERVAL '5' HOUR);