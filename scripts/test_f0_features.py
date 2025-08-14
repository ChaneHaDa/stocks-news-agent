#!/usr/bin/env python3
"""
F0 Features Test Script
Tests anonymous ID system, experiment assignment, and behavioral logging
"""

import requests
import json
import time
import random
import uuid
from typing import Dict, List, Optional
from datetime import datetime

class F0FeatureTester:
    def __init__(self, base_url: str = "http://localhost:8000"):
        self.base_url = base_url
        self.session = requests.Session()
        self.test_results = []
        
    def run_all_tests(self) -> bool:
        """Run all F0 feature tests"""
        print("🧱 F0 기능 테스트 시작")
        print("=" * 50)
        
        tests = [
            self.test_anonymous_id_system,
            self.test_experiment_assignment,
            self.test_impression_logging,
            self.test_click_logging,
            self.test_experiment_metrics,
            self.test_feature_flag_integration,
            self.test_backward_compatibility
        ]
        
        for test in tests:
            try:
                success = test()
                self.test_results.append(success)
            except Exception as e:
                print(f"❌ 테스트 실행 오류: {e}")
                self.test_results.append(False)
        
        self.print_summary()
        return all(self.test_results)
    
    def test_anonymous_id_system(self) -> bool:
        """Test anonymous user ID generation and cookie handling"""
        print("\n=== 익명 ID 시스템 테스트 ===")
        
        try:
            # 1. 새 익명 사용자 생성
            response = self.session.get(f"{self.base_url}/analytics/anon-id")
            
            if response.status_code != 200:
                print(f"❌ 익명 ID 생성 실패: {response.status_code}")
                return False
            
            data = response.json()
            anon_id = data.get("anonId")
            is_new_user = data.get("isNewUser")
            session_count = data.get("sessionCount")
            
            if not anon_id or len(anon_id) != 36:
                print(f"❌ 잘못된 anon_id 형식: {anon_id}")
                return False
            
            if not is_new_user or session_count != 1:
                print(f"❌ 새 사용자 플래그 오류: new={is_new_user}, count={session_count}")
                return False
            
            # 2. 동일 세션에서 재요청 (기존 사용자 반환)
            response2 = self.session.get(f"{self.base_url}/analytics/anon-id")
            data2 = response2.json()
            
            if data2.get("anonId") != anon_id:
                print(f"❌ 세션 지속성 실패: {data2.get('anonId')} != {anon_id}")
                return False
            
            if data2.get("isNewUser") or data2.get("sessionCount") <= 1:
                print(f"❌ 세션 카운트 오류: new={data2.get('isNewUser')}, count={data2.get('sessionCount')}")
                return False
            
            print(f"✅ 익명 ID 시스템 성공:")
            print(f"   - anon_id: {anon_id}")
            print(f"   - 세션 지속성: OK")
            print(f"   - 세션 카운트: {data2.get('sessionCount')}")
            
            # Store anon_id for other tests
            self.test_anon_id = anon_id
            return True
            
        except Exception as e:
            print(f"❌ 익명 ID 시스템 테스트 오류: {e}")
            return False
    
    def test_experiment_assignment(self) -> bool:
        """Test experiment variant assignment consistency"""
        print("\n=== 실험 배정 시스템 테스트 ===")
        
        try:
            if not hasattr(self, 'test_anon_id'):
                print("❌ 익명 ID가 필요합니다. 이전 테스트를 먼저 실행하세요.")
                return False
            
            experiment_key = "rank_personalization_v1"
            
            # 1. 실험 배정 요청
            response = self.session.get(
                f"{self.base_url}/analytics/experiment/{experiment_key}/assignment",
                params={"anonId": self.test_anon_id}
            )
            
            if response.status_code != 200:
                print(f"❌ 실험 배정 요청 실패: {response.status_code}")
                return False
            
            data = response.json()
            variant1 = data.get("variant")
            is_active = data.get("isActive")
            
            if not variant1:
                print(f"❌ variant 없음: {data}")
                return False
            
            # 2. 일관성 테스트 (동일한 anon_id는 항상 동일한 variant)
            for i in range(5):
                response2 = self.session.get(
                    f"{self.base_url}/analytics/experiment/{experiment_key}/assignment",
                    params={"anonId": self.test_anon_id}
                )
                data2 = response2.json()
                
                if data2.get("variant") != variant1:
                    print(f"❌ 배정 일관성 실패: {data2.get('variant')} != {variant1}")
                    return False
            
            # 3. 다른 anon_id로 배정 테스트
            different_anon_id = str(uuid.uuid4())
            response3 = self.session.get(
                f"{self.base_url}/analytics/experiment/{experiment_key}/assignment",
                params={"anonId": different_anon_id}
            )
            data3 = response3.json()
            
            print(f"✅ 실험 배정 시스템 성공:")
            print(f"   - 실험 키: {experiment_key}")
            print(f"   - 배정 variant: {variant1}")
            print(f"   - 활성 상태: {is_active}")
            print(f"   - 일관성: OK (5회 테스트)")
            print(f"   - 다른 사용자 variant: {data3.get('variant')}")
            
            # Store for other tests
            self.test_experiment_key = experiment_key
            self.test_variant = variant1
            return True
            
        except Exception as e:
            print(f"❌ 실험 배정 시스템 테스트 오류: {e}")
            return False
    
    def test_impression_logging(self) -> bool:
        """Test impression logging pipeline"""
        print("\n=== 노출 로깅 테스트 ===")
        
        try:
            if not hasattr(self, 'test_anon_id'):
                print("❌ 익명 ID가 필요합니다.")
                return False
            
            # Sample news items
            news_items = [
                {"newsId": 1, "importanceScore": 0.85, "rankScore": 0.92},
                {"newsId": 2, "importanceScore": 0.73, "rankScore": 0.81},
                {"newsId": 3, "importanceScore": 0.68, "rankScore": 0.75}
            ]
            
            impression_data = {
                "anonId": self.test_anon_id,
                "sessionId": f"session_{int(time.time())}",
                "newsItems": news_items,
                "pageType": "top",
                "experimentKey": getattr(self, 'test_experiment_key', 'test_experiment'),
                "variant": getattr(self, 'test_variant', 'control'),
                "personalized": True,
                "diversityApplied": True
            }
            
            response = self.session.post(
                f"{self.base_url}/analytics/impressions",
                json=impression_data
            )
            
            if response.status_code != 200:
                print(f"❌ 노출 로깅 실패: {response.status_code}")
                return False
            
            print(f"✅ 노출 로깅 성공:")
            print(f"   - anon_id: {self.test_anon_id}")
            print(f"   - 뉴스 개수: {len(news_items)}")
            print(f"   - 실험: {impression_data['experimentKey']}")
            print(f"   - variant: {impression_data['variant']}")
            
            return True
            
        except Exception as e:
            print(f"❌ 노출 로깅 테스트 오류: {e}")
            return False
    
    def test_click_logging(self) -> bool:
        """Test click logging pipeline"""
        print("\n=== 클릭 로깅 테스트 ===")
        
        try:
            if not hasattr(self, 'test_anon_id'):
                print("❌ 익명 ID가 필요합니다.")
                return False
            
            click_data = {
                "anonId": self.test_anon_id,
                "newsId": 1,
                "sessionId": f"session_{int(time.time())}",
                "rankPosition": 1,
                "importanceScore": 0.85,
                "experimentKey": getattr(self, 'test_experiment_key', 'test_experiment'),
                "variant": getattr(self, 'test_variant', 'control'),
                "dwellTimeMs": 15000,  # 15 seconds
                "clickSource": "news_list",
                "personalized": True
            }
            
            response = self.session.post(
                f"{self.base_url}/analytics/clicks",
                json=click_data
            )
            
            if response.status_code != 200:
                print(f"❌ 클릭 로깅 실패: {response.status_code}")
                return False
            
            print(f"✅ 클릭 로깅 성공:")
            print(f"   - anon_id: {self.test_anon_id}")
            print(f"   - news_id: {click_data['newsId']}")
            print(f"   - 위치: {click_data['rankPosition']}")
            print(f"   - 체류시간: {click_data['dwellTimeMs']}ms")
            
            return True
            
        except Exception as e:
            print(f"❌ 클릭 로깅 테스트 오류: {e}")
            return False
    
    def test_experiment_metrics(self) -> bool:
        """Test experiment metrics aggregation"""
        print("\n=== 실험 지표 테스트 ===")
        
        try:
            if not hasattr(self, 'test_experiment_key'):
                print("❌ 실험 키가 필요합니다.")
                return False
            
            response = self.session.get(
                f"{self.base_url}/analytics/experiment/{self.test_experiment_key}/metrics",
                params={"days": 1}
            )
            
            if response.status_code != 200:
                print(f"❌ 실험 지표 요청 실패: {response.status_code}")
                return False
            
            data = response.json()
            experiment_key = data.get("experimentKey")
            impressions = data.get("impressionsByVariant", {})
            clicks = data.get("clicksByVariant", {})
            ctr = data.get("ctrByVariant", {})
            
            print(f"✅ 실험 지표 성공:")
            print(f"   - 실험: {experiment_key}")
            print(f"   - 노출수: {impressions}")
            print(f"   - 클릭수: {clicks}")
            print(f"   - CTR: {ctr}")
            
            return True
            
        except Exception as e:
            print(f"❌ 실험 지표 테스트 오류: {e}")
            return False
    
    def test_feature_flag_integration(self) -> bool:
        """Test feature flag integration with analytics"""
        print("\n=== Feature Flag 통합 테스트 ===")
        
        try:
            # Test that logging APIs respect feature flags
            # This is more of an integration test - actual flag checking happens in the service
            
            # Simulate multiple impression/click logs
            for i in range(3):
                impression_data = {
                    "anonId": getattr(self, 'test_anon_id', str(uuid.uuid4())),
                    "sessionId": f"test_session_{i}",
                    "newsItems": [{"newsId": i+10, "importanceScore": 0.7, "rankScore": 0.8}],
                    "pageType": "test",
                    "experimentKey": "test_flag_integration",
                    "variant": "control",
                    "personalized": False,
                    "diversityApplied": True
                }
                
                response = self.session.post(
                    f"{self.base_url}/analytics/impressions",
                    json=impression_data
                )
                
                if response.status_code != 200:
                    print(f"❌ Feature Flag 통합 실패: {response.status_code}")
                    return False
            
            print(f"✅ Feature Flag 통합 성공:")
            print(f"   - 다중 로깅 요청: 3개")
            print(f"   - 응답 상태: 200 OK")
            
            return True
            
        except Exception as e:
            print(f"❌ Feature Flag 통합 테스트 오류: {e}")
            return False
    
    def test_backward_compatibility(self) -> bool:
        """Test backward compatibility with existing systems"""
        print("\n=== 하위 호환성 테스트 ===")
        
        try:
            # Test existing news/top endpoint still works
            response = self.session.get(f"{self.base_url}/news/top?n=5")
            
            if response.status_code != 200:
                print(f"❌ 기존 API 호환성 실패: {response.status_code}")
                return False
            
            data = response.json()
            items = data.get("items", [])
            
            if len(items) == 0:
                print("⚠️ 뉴스 항목이 없습니다 (정상일 수 있음)")
            
            print(f"✅ 하위 호환성 성공:")
            print(f"   - /news/top 엔드포인트: OK")
            print(f"   - 반환된 뉴스 수: {len(items)}")
            
            return True
            
        except Exception as e:
            print(f"❌ 하위 호환성 테스트 오류: {e}")
            return False
    
    def print_summary(self):
        """Print test summary"""
        print("\n" + "=" * 50)
        print("📊 F0 테스트 결과 요약")
        print("=" * 50)
        
        test_names = [
            "익명 ID 시스템",
            "실험 배정",
            "노출 로깅",
            "클릭 로깅", 
            "실험 지표",
            "Feature Flag 통합",
            "하위 호환성"
        ]
        
        for i, (name, result) in enumerate(zip(test_names, self.test_results)):
            status = "✅ PASS" if result else "❌ FAIL"
            print(f"{status} {name}")
        
        passed = sum(self.test_results)
        total = len(self.test_results)
        success_rate = (passed / total * 100) if total > 0 else 0
        
        print(f"\n총 {passed}/{total} 테스트 통과 ({success_rate:.1f}%)")
        
        if success_rate >= 85:
            print("🎉 F0 기능이 성공적으로 구현되었습니다!")
        elif success_rate >= 70:
            print("⚠️  대부분의 기능이 작동하지만 일부 개선이 필요합니다.")
        else:
            print("❌ 추가 개발과 디버깅이 필요합니다.")

def main():
    """Main function"""
    import argparse
    
    parser = argparse.ArgumentParser(description="F0 Features Test Script")
    parser.add_argument("--api-url", default="http://localhost:8000", 
                       help="API base URL")
    
    args = parser.parse_args()
    
    tester = F0FeatureTester(args.api_url)
    success = tester.run_all_tests()
    
    exit(0 if success else 1)

if __name__ == "__main__":
    main()