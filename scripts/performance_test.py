#!/usr/bin/env python3
"""
Performance testing script for ML pipeline
Target: 50 RPS with p95 < 300ms
"""

import asyncio
import aiohttp
import time
import json
import statistics
from typing import List, Dict, Any
from dataclasses import dataclass
import argparse
import sys

@dataclass
class TestResult:
    url: str
    status_code: int
    response_time: float
    success: bool
    error: str = None

class PerformanceTester:
    def __init__(self, base_url: str = "http://localhost:8001"):
        self.base_url = base_url
        self.results: List[TestResult] = []
        
    async def test_endpoint(self, session: aiohttp.ClientSession, url: str, payload: Dict[str, Any] = None) -> TestResult:
        """Test a single endpoint and measure response time"""
        start_time = time.perf_counter()
        
        try:
            if payload:
                async with session.post(url, json=payload, timeout=aiohttp.ClientTimeout(total=5)) as response:
                    await response.text()  # Consume response
                    end_time = time.perf_counter()
                    return TestResult(
                        url=url,
                        status_code=response.status,
                        response_time=(end_time - start_time) * 1000,  # Convert to ms
                        success=response.status == 200
                    )
            else:
                async with session.get(url, timeout=aiohttp.ClientTimeout(total=5)) as response:
                    await response.text()  # Consume response
                    end_time = time.perf_counter()
                    return TestResult(
                        url=url,
                        status_code=response.status,
                        response_time=(end_time - start_time) * 1000,  # Convert to ms
                        success=response.status == 200
                    )
        except Exception as e:
            end_time = time.perf_counter()
            return TestResult(
                url=url,
                status_code=0,
                response_time=(end_time - start_time) * 1000,
                success=False,
                error=str(e)
            )

    async def load_test(self, endpoint: str, payload: Dict[str, Any] = None, 
                       rps: int = 50, duration: int = 30) -> List[TestResult]:
        """Perform load test at specified RPS for given duration"""
        print(f"🚀 Starting load test: {endpoint}")
        print(f"   Target RPS: {rps}")
        print(f"   Duration: {duration}s")
        print(f"   Expected requests: {rps * duration}")
        
        url = f"{self.base_url}{endpoint}"
        interval = 1.0 / rps  # Time between requests
        
        async with aiohttp.ClientSession() as session:
            tasks = []
            start_time = time.time()
            
            for i in range(rps * duration):
                # Schedule request at precise interval
                scheduled_time = start_time + (i * interval)
                wait_time = scheduled_time - time.time()
                
                if wait_time > 0:
                    await asyncio.sleep(wait_time)
                
                task = asyncio.create_task(self.test_endpoint(session, url, payload))
                tasks.append(task)
                
                # Print progress every 100 requests
                if (i + 1) % 100 == 0:
                    print(f"   Sent {i + 1} requests...")
            
            print("   Waiting for all responses...")
            results = await asyncio.gather(*tasks)
            
        return results

    def analyze_results(self, results: List[TestResult], test_name: str) -> Dict[str, Any]:
        """Analyze test results and calculate metrics"""
        if not results:
            return {"error": "No results to analyze"}
        
        successful_results = [r for r in results if r.success]
        failed_results = [r for r in results if not r.success]
        
        if not successful_results:
            return {"error": "All requests failed"}
        
        response_times = [r.response_time for r in successful_results]
        
        # Calculate percentiles
        p50 = statistics.median(response_times)
        p95 = statistics.quantiles(response_times, n=20)[18] if len(response_times) > 20 else max(response_times)
        p99 = statistics.quantiles(response_times, n=100)[98] if len(response_times) > 100 else max(response_times)
        
        analysis = {
            "test_name": test_name,
            "total_requests": len(results),
            "successful_requests": len(successful_results),
            "failed_requests": len(failed_results),
            "success_rate": len(successful_results) / len(results) * 100,
            "response_times": {
                "min": min(response_times),
                "max": max(response_times),
                "mean": statistics.mean(response_times),
                "median": p50,
                "p95": p95,
                "p99": p99
            },
            "target_p95_met": p95 < 300,  # Target: p95 < 300ms
            "status_codes": {}
        }
        
        # Count status codes
        for result in results:
            code = result.status_code
            analysis["status_codes"][code] = analysis["status_codes"].get(code, 0) + 1
        
        return analysis

    def print_analysis(self, analysis: Dict[str, Any]):
        """Print formatted analysis results"""
        if "error" in analysis:
            print(f"❌ {analysis['error']}")
            return
        
        print(f"\n📊 Results for {analysis['test_name']}:")
        print(f"   Total requests: {analysis['total_requests']}")
        print(f"   Success rate: {analysis['success_rate']:.1f}%")
        print(f"   Failed requests: {analysis['failed_requests']}")
        
        rt = analysis['response_times']
        print(f"\n⏱️  Response Times (ms):")
        print(f"   Min: {rt['min']:.1f}")
        print(f"   Mean: {rt['mean']:.1f}")
        print(f"   Median: {rt['median']:.1f}")
        print(f"   P95: {rt['p95']:.1f}")
        print(f"   P99: {rt['p99']:.1f}")
        print(f"   Max: {rt['max']:.1f}")
        
        # Check targets
        target_met = "✅" if analysis['target_p95_met'] else "❌"
        print(f"\n🎯 Target P95 < 300ms: {target_met} ({rt['p95']:.1f}ms)")
        
        if analysis['status_codes']:
            print(f"\n📈 Status Codes:")
            for code, count in sorted(analysis['status_codes'].items()):
                print(f"   {code}: {count}")

async def main():
    parser = argparse.ArgumentParser(description='ML Pipeline Performance Test')
    parser.add_argument('--ml-url', default='http://localhost:8001', help='ML service base URL')
    parser.add_argument('--api-url', default='http://localhost:8000', help='API service base URL')
    parser.add_argument('--rps', type=int, default=50, help='Requests per second')
    parser.add_argument('--duration', type=int, default=30, help='Test duration in seconds')
    parser.add_argument('--endpoints', nargs='+', default=['ml', 'api', 'all'], 
                       help='Endpoints to test: ml, api, all')
    
    args = parser.parse_args()
    
    print("🧪 ML Pipeline Performance Testing")
    print("=" * 50)
    
    # Test payloads
    importance_payload = {
        "items": [{
            "id": "test1",
            "title": "삼성전자 3분기 실적 발표",
            "body": "삼성전자가 3분기 매출 76조원을 기록했다고 발표했습니다. 메모리 반도체 부문의 실적 개선이 주효했습니다.",
            "source": "연합뉴스",
            "published_at": "2025-01-15T09:00:00Z"
        }]
    }
    
    summarize_payload = {
        "id": "test2",
        "title": "LG에너지솔루션 북미 공장 증설",
        "body": "LG에너지솔루션이 북미 지역에 새로운 배터리 공장을 증설한다고 발표했습니다. 총 5조원을 투자해 전기차 배터리 생산능력을 확대할 예정입니다. 이번 투자로 연간 100GWh 규모의 배터리를 생산할 수 있을 것으로 예상됩니다.",
        "tickers": ["373220"],
        "options": {
            "max_length": 200,
            "style": "extractive"
        }
    }
    
    all_results = []
    
    # Test ML service endpoints
    if 'ml' in args.endpoints or 'all' in args.endpoints:
        ml_tester = PerformanceTester(args.ml_url)
        
        # Test root endpoint (health)
        health_results = await ml_tester.load_test("/", rps=args.rps, duration=10)
        analysis = ml_tester.analyze_results(health_results, "ML Health Check")
        ml_tester.print_analysis(analysis)
        all_results.append(analysis)
        
        # Test importance endpoint
        importance_results = await ml_tester.load_test("/v1/importance:score", importance_payload, 
                                                     rps=args.rps, duration=args.duration)
        analysis = ml_tester.analyze_results(importance_results, "ML Importance Scoring")
        ml_tester.print_analysis(analysis)
        all_results.append(analysis)
        
        # Test summarize endpoint  
        summarize_results = await ml_tester.load_test("/v1/summarize", summarize_payload,
                                                     rps=args.rps//2, duration=args.duration)  # Lower RPS for LLM
        analysis = ml_tester.analyze_results(summarize_results, "ML Summarization")
        ml_tester.print_analysis(analysis)
        all_results.append(analysis)
    
    # Test API service endpoints
    if 'api' in args.endpoints or 'all' in args.endpoints:
        api_tester = PerformanceTester(args.api_url)
        
        # Test news endpoint
        news_results = await api_tester.load_test("/news/top?n=20", rps=args.rps, duration=args.duration)
        analysis = api_tester.analyze_results(news_results, "API News Endpoint")
        api_tester.print_analysis(analysis)
        all_results.append(analysis)
        
        # Test admin status
        status_results = await api_tester.load_test("/admin/status", rps=args.rps//4, duration=10)
        analysis = api_tester.analyze_results(status_results, "API Admin Status")
        api_tester.print_analysis(analysis)
        all_results.append(analysis)
    
    # Overall summary
    print("\n🏆 OVERALL PERFORMANCE SUMMARY")
    print("=" * 50)
    
    passed_tests = 0
    total_tests = len(all_results)
    
    for result in all_results:
        if "error" not in result:
            status = "✅ PASS" if result['target_p95_met'] else "❌ FAIL"
            p95 = result['response_times']['p95']
            success_rate = result['success_rate']
            print(f"{status} {result['test_name']}: P95={p95:.1f}ms, Success={success_rate:.1f}%")
            if result['target_p95_met']:
                passed_tests += 1
        else:
            print(f"❌ FAIL {result.get('test_name', 'Unknown')}: {result['error']}")
    
    print(f"\n📝 Test Results: {passed_tests}/{total_tests} tests passed")
    
    if passed_tests == total_tests:
        print("🎉 All performance targets met!")
        sys.exit(0)
    else:
        print("⚠️  Some performance targets not met. Consider optimization.")
        sys.exit(1)

if __name__ == "__main__":
    asyncio.run(main())