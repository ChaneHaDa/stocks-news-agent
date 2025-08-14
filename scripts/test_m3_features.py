#!/usr/bin/env python3
"""
M3 기능 테스트 스크립트
- 임베딩 생성
- 토픽 클러스터링
- 개인화 재랭킹
- MMR 다양성 필터링
"""

import requests
import json
import time
from typing import Dict, List, Any


class M3FeatureTester:
    def __init__(self, api_url: str = "http://localhost:8000", ml_url: str = "http://localhost:8001"):
        self.api_url = api_url.rstrip('/')
        self.ml_url = ml_url.rstrip('/')
        
    def test_ml_service_health(self) -> bool:
        """ML 서비스 상태 확인"""
        try:
            response = requests.get(f"{self.ml_url}/admin/health", timeout=10)
            if response.status_code == 200:
                health_data = response.json()
                print(f"✅ ML Service healthy: {health_data}")
                return True
            else:
                print(f"❌ ML Service unhealthy: {response.status_code}")
                return False
        except Exception as e:
            print(f"❌ ML Service connection failed: {e}")
            return False
    
    def test_embedding_generation(self) -> bool:
        """임베딩 생성 테스트"""
        print("\n=== 임베딩 생성 테스트 ===")
        
        try:
            # 임베딩 생성 요청
            test_data = {
                "items": [
                    {"id": "test1", "text": "삼성전자 3분기 실적 발표"},
                    {"id": "test2", "text": "네이버 클라우드 서비스 확장"}
                ]
            }
            
            response = requests.post(
                f"{self.ml_url}/v1/embed",
                json=test_data,
                timeout=30
            )
            
            if response.status_code == 200:
                embed_data = response.json()
                print(f"✅ 임베딩 생성 성공:")
                print(f"   - 모델 버전: {embed_data.get('model_version')}")
                print(f"   - 차원: {embed_data.get('dimension')}")
                print(f"   - 결과 수: {len(embed_data.get('results', []))}")
                
                # 벡터 차원 확인
                if embed_data.get('results'):
                    vector_len = len(embed_data['results'][0].get('vector', []))
                    print(f"   - 벡터 길이: {vector_len}")
                
                return True
            else:
                print(f"❌ 임베딩 생성 실패: {response.status_code} - {response.text}")
                return False
                
        except Exception as e:
            print(f"❌ 임베딩 테스트 오류: {e}")
            return False
    
    def test_news_ingestion(self) -> bool:
        """뉴스 수집 및 임베딩 파이프라인 테스트"""
        print("\n=== 뉴스 수집 & 임베딩 파이프라인 테스트 ===")
        
        try:
            response = requests.post(f"{self.api_url}/admin/ingest", timeout=120)
            
            if response.status_code == 200:
                ingest_data = response.json()
                print(f"✅ 뉴스 수집 성공:")
                print(f"   - 가져온 기사: {ingest_data.get('itemsFetched', 0)}")
                print(f"   - 저장된 기사: {ingest_data.get('itemsSaved', 0)}")
                print(f"   - 처리 시간: {ingest_data.get('durationMs', 0)}ms")
                return True
            else:
                print(f"❌ 뉴스 수집 실패: {response.status_code} - {response.text}")
                return False
                
        except Exception as e:
            print(f"❌ 뉴스 수집 테스트 오류: {e}")
            return False
    
    def test_topic_clustering(self) -> bool:
        """토픽 클러스터링 테스트"""
        print("\n=== 토픽 클러스터링 테스트 ===")
        
        try:
            response = requests.post(f"{self.api_url}/admin/clustering", timeout=180)
            
            if response.status_code == 200:
                cluster_data = response.json()
                print(f"✅ 토픽 클러스터링 성공:")
                print(f"   - 총 기사: {cluster_data.get('totalArticles', 0)}")
                print(f"   - 임베딩 보유 기사: {cluster_data.get('articlesWithEmbeddings', 0)}")
                print(f"   - 생성된 클러스터: {cluster_data.get('clustersGenerated', 0)}")
                print(f"   - 중복 그룹: {cluster_data.get('duplicateGroupsFound', 0)}")
                print(f"   - 토픽 할당: {cluster_data.get('topicsAssigned', 0)}")
                print(f"   - 처리 시간: {cluster_data.get('durationMs', 0)}ms")
                return True
            else:
                print(f"❌ 토픽 클러스터링 실패: {response.status_code} - {response.text}")
                return False
                
        except Exception as e:
            print(f"❌ 토픽 클러스터링 테스트 오류: {e}")
            return False
    
    def test_diversity_filtering(self) -> bool:
        """다양성 필터링 테스트"""
        print("\n=== MMR 다양성 필터링 테스트 ===")
        
        try:
            # 다양성 필터링 적용한 뉴스 조회
            response_div = requests.get(
                f"{self.api_url}/news/top",
                params={"n": 20, "diversity": "true"},
                timeout=30
            )
            
            # 다양성 필터링 미적용 뉴스 조회
            response_no_div = requests.get(
                f"{self.api_url}/news/top",
                params={"n": 20, "diversity": "false"},
                timeout=30
            )
            
            if response_div.status_code == 200 and response_no_div.status_code == 200:
                div_news = response_div.json().get('items', [])
                no_div_news = response_no_div.json().get('items', [])
                
                print(f"✅ 다양성 필터링 테스트 성공:")
                print(f"   - 다양성 적용: {len(div_news)}개 기사")
                print(f"   - 다양성 미적용: {len(no_div_news)}개 기사")
                
                # 제목 중복 확인
                div_titles = [item['title'] for item in div_news]
                no_div_titles = [item['title'] for item in no_div_news]
                
                div_unique = len(set(div_titles))
                no_div_unique = len(set(no_div_titles))
                
                print(f"   - 다양성 적용 시 고유 제목: {div_unique}/{len(div_titles)}")
                print(f"   - 다양성 미적용 시 고유 제목: {no_div_unique}/{len(no_div_titles)}")
                
                return True
            else:
                print(f"❌ 다양성 필터링 테스트 실패")
                return False
                
        except Exception as e:
            print(f"❌ 다양성 필터링 테스트 오류: {e}")
            return False
    
    def test_personalization(self) -> bool:
        """개인화 기능 테스트"""
        print("\n=== 개인화 기능 테스트 ===")
        
        test_user_id = "test_user_123"
        
        try:
            # 1. 사용자 환경설정 생성
            pref_response = requests.put(
                f"{self.api_url}/users/{test_user_id}/preferences/personalization",
                params={"enabled": "true"},
                timeout=30
            )
            
            if pref_response.status_code == 200:
                print(f"✅ 사용자 환경설정 생성 성공")
            
            # 2. 관심 종목 설정
            ticker_response = requests.put(
                f"{self.api_url}/users/{test_user_id}/preferences/tickers",
                json=["005930", "035720"],  # 삼성전자, 카카오
                timeout=30
            )
            
            if ticker_response.status_code == 200:
                print(f"✅ 관심 종목 설정 성공")
            
            # 3. 개인화된 뉴스 조회
            personal_response = requests.get(
                f"{self.api_url}/news/top",
                params={
                    "n": 10,
                    "personalized": "true",
                    "userId": test_user_id
                },
                timeout=30
            )
            
            # 4. 비개인화 뉴스 조회
            normal_response = requests.get(
                f"{self.api_url}/news/top",
                params={"n": 10, "personalized": "false"},
                timeout=30
            )
            
            if personal_response.status_code == 200 and normal_response.status_code == 200:
                personal_news = personal_response.json().get('items', [])
                normal_news = normal_response.json().get('items', [])
                
                print(f"✅ 개인화 뉴스 조회 성공:")
                print(f"   - 개인화: {len(personal_news)}개 기사")
                print(f"   - 비개인화: {len(normal_news)}개 기사")
                
                # 5. 클릭 이벤트 기록 테스트
                if personal_news:
                    first_news_id = personal_news[0]['id']
                    click_response = requests.post(
                        f"{self.api_url}/news/{first_news_id}/click",
                        params={
                            "userId": test_user_id,
                            "position": 1,
                            "importance": 0.8
                        },
                        timeout=30
                    )
                    
                    if click_response.status_code == 200:
                        print(f"✅ 클릭 이벤트 기록 성공")
                
                return True
            else:
                print(f"❌ 개인화 뉴스 조회 실패")
                return False
                
        except Exception as e:
            print(f"❌ 개인화 테스트 오류: {e}")
            return False
    
    def test_system_status(self) -> bool:
        """시스템 상태 확인"""
        print("\n=== 시스템 상태 확인 ===")
        
        try:
            response = requests.get(f"{self.api_url}/admin/status", timeout=30)
            
            if response.status_code == 200:
                status_data = response.json()
                print(f"✅ 시스템 상태 정상:")
                print(f"   - 서비스: {status_data.get('service')}")
                print(f"   - ML 서비스 상태: {status_data.get('ml_service', {}).get('healthy')}")
                
                features = status_data.get('ml_service', {}).get('features', {})
                print(f"   - ML 기능들:")
                for feature, enabled in features.items():
                    status = "✅" if enabled else "❌"
                    print(f"     {status} {feature}: {enabled}")
                
                return True
            else:
                print(f"❌ 시스템 상태 확인 실패: {response.status_code}")
                return False
                
        except Exception as e:
            print(f"❌ 시스템 상태 확인 오류: {e}")
            return False
    
    def run_all_tests(self) -> Dict[str, bool]:
        """모든 테스트 실행"""
        print("🚀 M3 기능 테스트 시작")
        print("=" * 50)
        
        results = {}
        
        # 테스트 순서 (의존성 고려)
        tests = [
            ("ML 서비스 상태", self.test_ml_service_health),
            ("임베딩 생성", self.test_embedding_generation),
            ("뉴스 수집 & 임베딩", self.test_news_ingestion),
            ("토픽 클러스터링", self.test_topic_clustering),
            ("다양성 필터링", self.test_diversity_filtering),
            ("개인화 기능", self.test_personalization),
            ("시스템 상태", self.test_system_status),
        ]
        
        for test_name, test_func in tests:
            try:
                results[test_name] = test_func()
                time.sleep(2)  # 테스트 간 간격
            except Exception as e:
                print(f"❌ {test_name} 테스트 중 예외 발생: {e}")
                results[test_name] = False
        
        # 결과 요약
        print("\n" + "=" * 50)
        print("📊 테스트 결과 요약")
        print("=" * 50)
        
        passed = 0
        total = len(results)
        
        for test_name, success in results.items():
            status = "✅ PASS" if success else "❌ FAIL"
            print(f"{status} {test_name}")
            if success:
                passed += 1
        
        print(f"\n총 {passed}/{total} 테스트 통과 ({passed/total*100:.1f}%)")
        
        if passed == total:
            print("🎉 모든 M3 기능이 정상적으로 작동합니다!")
        else:
            print("⚠️  일부 기능에 문제가 있습니다. 로그를 확인해주세요.")
        
        return results


if __name__ == "__main__":
    import argparse
    
    parser = argparse.ArgumentParser(description="M3 기능 테스트")
    parser.add_argument("--api-url", default="http://localhost:8000", help="API 서버 URL")
    parser.add_argument("--ml-url", default="http://localhost:8001", help="ML 서버 URL")
    
    args = parser.parse_args()
    
    tester = M3FeatureTester(api_url=args.api_url, ml_url=args.ml_url)
    results = tester.run_all_tests()
    
    # 실패한 테스트가 있으면 exit code 1
    if not all(results.values()):
        exit(1)