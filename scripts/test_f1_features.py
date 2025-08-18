#!/usr/bin/env python3
"""
F1 Features Test Script
Tests real-time embedding pipeline and vector similarity search capabilities
"""

import requests
import json
import time
import sys
from typing import Dict, List, Optional

class F1FeatureTester:
    def __init__(self, api_base_url: str = "http://localhost:8000"):
        self.api_url = api_base_url
        self.session = requests.Session()
        self.session.headers.update({
            'Content-Type': 'application/json',
            'User-Agent': 'F1-Feature-Tester/1.0'
        })
        
    def test_vector_search_health(self) -> bool:
        """Test vector search service health"""
        try:
            response = self.session.get(f"{self.api_url}/api/v1/vector/health")
            if response.status_code == 200:
                health_data = response.json()
                print(f"✅ Vector search health: {health_data.get('status', 'unknown')}")
                return health_data.get('status') == 'healthy'
            else:
                print(f"❌ Vector search health check failed: {response.status_code}")
                return False
        except Exception as e:
            print(f"❌ Vector search health check error: {e}")
            return False
    
    def test_embedding_backlog_processing(self) -> bool:
        """Test embedding backlog processing"""
        try:
            response = self.session.post(
                f"{self.api_url}/api/v1/vector/embeddings/backlog",
                params={'batchSize': 10}
            )
            
            if response.status_code == 200:
                result = response.json()
                processed = result.get('processed', 0)
                print(f"✅ Embedding backlog processed: {processed} items")
                return True
            else:
                print(f"❌ Embedding backlog processing failed: {response.status_code}")
                print(f"Response: {response.text}")
                return False
        except Exception as e:
            print(f"❌ Embedding backlog processing error: {e}")
            return False
    
    def get_news_list(self) -> List[Dict]:
        """Get list of news articles for testing"""
        try:
            response = self.session.get(f"{self.api_url}/api/news/top?limit=20")
            if response.status_code == 200:
                news_list = response.json()
                print(f"📰 Retrieved {len(news_list)} news articles for testing")
                return news_list
            else:
                print(f"❌ Failed to get news list: {response.status_code}")
                return []
        except Exception as e:
            print(f"❌ Error getting news list: {e}")
            return []
    
    def test_similarity_search(self, news_id: int, limit: int = 5) -> bool:
        """Test similarity search for a specific news article"""
        try:
            response = self.session.get(
                f"{self.api_url}/api/v1/vector/similar/{news_id}",
                params={'limit': limit}
            )
            
            if response.status_code == 200:
                similar_news = response.json()
                print(f"✅ Found {len(similar_news)} similar articles for news ID {news_id}")
                
                # Display similarity results
                for i, news in enumerate(similar_news[:3], 1):
                    title = news.get('title', 'No title')[:60] + "..."
                    similarity = news.get('rankScore', 0)
                    print(f"   {i}. {title} (similarity: {similarity:.3f})")
                
                return len(similar_news) > 0
            elif response.status_code == 404:
                print(f"⚠️  News ID {news_id} not found or no embedding available")
                return False
            else:
                print(f"❌ Similarity search failed for news ID {news_id}: {response.status_code}")
                return False
        except Exception as e:
            print(f"❌ Similarity search error for news ID {news_id}: {e}")
            return False
    
    def test_text_similarity_search(self, query_text: str, limit: int = 5) -> bool:
        """Test text-based similarity search"""
        try:
            payload = {
                'query': query_text,
                'limit': limit
            }
            
            response = self.session.post(
                f"{self.api_url}/api/v1/vector/similar/text",
                json=payload
            )
            
            if response.status_code == 200:
                similar_news = response.json()
                print(f"✅ Text search for '{query_text}' returned {len(similar_news)} results")
                return True
            else:
                print(f"❌ Text similarity search failed: {response.status_code}")
                print(f"Response: {response.text}")
                return False
        except Exception as e:
            print(f"❌ Text similarity search error: {e}")
            return False
    
    def test_ml_service_embedding(self) -> bool:
        """Test ML service embedding generation"""
        try:
            payload = {
                "items": [
                    {
                        "id": "test_1",
                        "text": "삼성전자 3분기 실적 발표 예정"
                    },
                    {
                        "id": "test_2", 
                        "text": "카카오 주가 상승세 지속"
                    }
                ]
            }
            
            response = self.session.post(
                "http://localhost:8001/v1/embed",
                json=payload
            )
            
            if response.status_code == 200:
                result = response.json()
                results = result.get('results', [])
                print(f"✅ ML service generated {len(results)} embeddings")
                
                if results:
                    first_result = results[0]
                    vector_dim = len(first_result.get('vector', []))
                    print(f"   Dimension: {vector_dim}, Model: {result.get('model_version', 'unknown')}")
                
                return len(results) > 0
            else:
                print(f"❌ ML service embedding test failed: {response.status_code}")
                return False
        except Exception as e:
            print(f"❌ ML service embedding test error: {e}")
            return False
    
    def run_comprehensive_test(self) -> Dict[str, bool]:
        """Run all F1 feature tests"""
        print("🚀 Starting F1 Feature Tests")
        print("=" * 50)
        
        results = {}
        
        # Test 1: Vector search health
        print("\n1. Testing vector search health...")
        results['vector_health'] = self.test_vector_search_health()
        
        # Test 2: ML service embedding
        print("\n2. Testing ML service embedding generation...")
        results['ml_embedding'] = self.test_ml_service_embedding()
        
        # Test 3: Embedding backlog processing
        print("\n3. Testing embedding backlog processing...")
        results['embedding_backlog'] = self.test_embedding_backlog_processing()
        
        # Test 4: Get news list
        print("\n4. Getting news list for similarity tests...")
        news_list = self.get_news_list()
        
        # Test 5: Similarity search
        print("\n5. Testing similarity search...")
        if news_list:
            # Test with first few news articles
            similarity_results = []
            for news in news_list[:5]:
                news_id = news.get('id')
                if news_id:
                    result = self.test_similarity_search(news_id, limit=3)
                    similarity_results.append(result)
                    time.sleep(0.1)  # Small delay between requests
            
            results['similarity_search'] = any(similarity_results)
            print(f"   Summary: {sum(similarity_results)}/{len(similarity_results)} similarity searches successful")
        else:
            results['similarity_search'] = False
            print("   No news articles available for similarity testing")
        
        # Test 6: Text-based similarity search
        print("\n6. Testing text-based similarity search...")
        results['text_similarity'] = self.test_text_similarity_search("삼성전자 실적", limit=3)
        
        return results
    
    def print_summary(self, results: Dict[str, bool]):
        """Print test summary"""
        print("\n" + "=" * 50)
        print("📊 F1 Feature Test Summary")
        print("=" * 50)
        
        total_tests = len(results)
        passed_tests = sum(results.values())
        
        for test_name, passed in results.items():
            status = "✅ PASS" if passed else "❌ FAIL"
            print(f"{test_name:20} : {status}")
        
        print("-" * 50)
        print(f"Overall Result: {passed_tests}/{total_tests} tests passed")
        
        if passed_tests == total_tests:
            print("🎉 All F1 features are working correctly!")
            return True
        else:
            print("⚠️  Some F1 features need attention")
            return False


def main():
    """Main test runner"""
    import argparse
    
    parser = argparse.ArgumentParser(description='Test F1 vector search features')
    parser.add_argument('--api-url', default='http://localhost:8000', 
                       help='API base URL (default: http://localhost:8000)')
    parser.add_argument('--test', choices=['health', 'embedding', 'similarity', 'text', 'all'],
                       default='all', help='Specific test to run')
    
    args = parser.parse_args()
    
    tester = F1FeatureTester(args.api_url)
    
    if args.test == 'health':
        success = tester.test_vector_search_health()
    elif args.test == 'embedding':
        success = tester.test_ml_service_embedding()
    elif args.test == 'similarity':
        news_list = tester.get_news_list()
        if news_list:
            success = tester.test_similarity_search(news_list[0]['id'])
        else:
            print("No news available for similarity test")
            success = False
    elif args.test == 'text':
        success = tester.test_text_similarity_search("삼성전자")
    else:  # all
        results = tester.run_comprehensive_test()
        success = tester.print_summary(results)
    
    sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()