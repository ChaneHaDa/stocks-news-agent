"""F4: Model A/B testing and comparison service for embedding models."""

import asyncio
import time
import statistics
from typing import List, Dict, Any, Optional, Tuple
from dataclasses import dataclass, asdict
from enum import Enum
import hashlib
import json

import numpy as np
from sklearn.metrics.pairwise import cosine_similarity
from sklearn.cluster import KMeans
from sklearn.metrics import silhouette_score

from .embed_service import EmbedService
from .deep_embed_service import DeepEmbedService, ModelType, get_deep_embed_service
from .onnx_embed_service import ONNXEmbedService, get_onnx_embed_service
from ..core.config import Settings
from ..core.logging import get_logger
from ..core.metrics import MODEL_INFERENCE_DURATION, MODEL_INFERENCE_COUNT

logger = get_logger(__name__)


class ComparisonMode(Enum):
    """Model comparison modes."""
    PERFORMANCE = "performance"
    QUALITY = "quality"
    COMPREHENSIVE = "comprehensive"


@dataclass
class ModelPerformanceResult:
    """Performance benchmark result for a model."""
    model_name: str
    model_type: str
    mean_latency_ms: float
    p50_latency_ms: float
    p95_latency_ms: float
    p99_latency_ms: float
    std_latency_ms: float
    throughput_per_sec: float
    success_rate: float
    error_count: int
    total_requests: int
    embedding_dimension: int


@dataclass
class ModelQualityResult:
    """Quality assessment result for a model."""
    model_name: str
    model_type: str
    embedding_dimension: int
    cosine_similarity_mean: float
    cosine_similarity_std: float
    clustering_silhouette_score: float
    semantic_consistency_score: float
    noise_robustness_score: float
    sample_embeddings: List[List[float]]


@dataclass
class ABTestResult:
    """A/B test comparison result."""
    test_id: str
    model_a: str
    model_b: str
    winner: Optional[str]
    confidence_level: float
    performance_comparison: Dict[str, Any]
    quality_comparison: Dict[str, Any]
    recommendation: str
    test_duration_sec: float


class ModelComparisonService:
    """Service for comparing and A/B testing different embedding models."""
    
    def __init__(self, settings: Settings):
        self.settings = settings
        
        # Initialize different embedding services
        self.mock_service = EmbedService(settings)
        self.deep_service = get_deep_embed_service(settings)
        self.onnx_service = get_onnx_embed_service(settings)
        
        # Test configurations
        self.performance_test_config = {
            "iterations": 50,
            "batch_sizes": [1, 4, 8, 16, 32],
            "timeout_sec": 30
        }
        
        self.quality_test_config = {
            "test_text_count": 100,
            "similarity_threshold": 0.7,
            "clustering_n_clusters": 5
        }
        
        # Korean financial news test texts for quality assessment
        self.test_texts = [
            "삼성전자 3분기 영업이익이 전년 동기 대비 277% 증가했다",
            "SK하이닉스가 HBM 메모리 매출 급증으로 실적 개선을 이뤘다",
            "LG화학이 전기차 배터리 수주를 확대하며 성장 동력을 확보했다",
            "현대자동차가 전기차 글로벌 판매량에서 3위를 차지했다",
            "NAVER 클라우드 플랫폼이 기업 고객 확보에 성공했다",
            "카카오가 동남아 모빌리티 시장 진출을 본격화한다",
            "포스코가 2차전지 소재 사업 확대를 위해 투자를 늘린다",
            "아모레퍼시픽이 중국 화장품 시장에서 회복세를 보인다",
            "한국전력공사가 전력요금 조정 계획을 발표했다",
            "KB금융그룹이 디지털 금융 서비스 투자를 확대한다"
        ]
        
        self.last_test_results = {}
        
        logger.info("ModelComparisonService initialized",
                   performance_iterations=self.performance_test_config["iterations"],
                   quality_test_count=self.quality_test_config["test_text_count"])
    
    async def run_ab_test(self, model_a: str, model_b: str, 
                         mode: ComparisonMode = ComparisonMode.COMPREHENSIVE) -> ABTestResult:
        """Run comprehensive A/B test between two models."""
        test_id = self._generate_test_id(model_a, model_b)
        logger.info(f"Starting A/B test: {test_id}", 
                   model_a=model_a, model_b=model_b, mode=mode.value)
        
        start_time = time.time()
        
        try:
            performance_results = {}
            quality_results = {}
            
            # Performance comparison
            if mode in [ComparisonMode.PERFORMANCE, ComparisonMode.COMPREHENSIVE]:
                logger.info("Running performance benchmarks")
                perf_a = await self.benchmark_model_performance(model_a)
                perf_b = await self.benchmark_model_performance(model_b)
                performance_results = {
                    model_a: asdict(perf_a),
                    model_b: asdict(perf_b)
                }
            
            # Quality comparison  
            if mode in [ComparisonMode.QUALITY, ComparisonMode.COMPREHENSIVE]:
                logger.info("Running quality assessments")
                qual_a = await self.assess_model_quality(model_a)
                qual_b = await self.assess_model_quality(model_b)
                quality_results = {
                    model_a: asdict(qual_a),
                    model_b: asdict(qual_b)
                }
            
            # Determine winner and generate recommendation
            winner, confidence, recommendation = self._analyze_test_results(
                model_a, model_b, performance_results, quality_results, mode
            )
            
            test_duration = time.time() - start_time
            
            result = ABTestResult(
                test_id=test_id,
                model_a=model_a,
                model_b=model_b,
                winner=winner,
                confidence_level=confidence,
                performance_comparison=performance_results,
                quality_comparison=quality_results,
                recommendation=recommendation,
                test_duration_sec=test_duration
            )
            
            # Store results
            self.last_test_results[test_id] = result
            
            logger.info(f"A/B test completed: {test_id}",
                       winner=winner,
                       confidence=confidence,
                       duration_sec=test_duration)
            
            return result
            
        except Exception as e:
            logger.error(f"A/B test failed: {test_id}", exc_info=e)
            raise
    
    async def benchmark_model_performance(self, model_name: str) -> ModelPerformanceResult:
        """Benchmark model performance across different scenarios."""
        logger.info(f"Benchmarking performance: {model_name}")
        
        latencies = []
        errors = 0
        total_requests = 0
        
        # Test different batch sizes
        for batch_size in self.performance_test_config["batch_sizes"]:
            for iteration in range(self.performance_test_config["iterations"] // len(self.performance_test_config["batch_sizes"])):
                try:
                    # Prepare test batch
                    test_batch = self._create_test_batch(batch_size)
                    
                    start_time = time.time()
                    await self._run_embedding_inference(model_name, test_batch)
                    latency = (time.time() - start_time) * 1000  # Convert to ms
                    
                    latencies.append(latency)
                    total_requests += 1
                    
                except Exception as e:
                    errors += 1
                    total_requests += 1
                    logger.warning(f"Performance test error for {model_name}", exc_info=e)
        
        # Calculate statistics
        if latencies:
            mean_latency = statistics.mean(latencies)
            p50_latency = statistics.median(latencies)
            p95_latency = np.percentile(latencies, 95)
            p99_latency = np.percentile(latencies, 99)
            std_latency = statistics.stdev(latencies) if len(latencies) > 1 else 0
            throughput = 1000 / mean_latency if mean_latency > 0 else 0
        else:
            mean_latency = p50_latency = p95_latency = p99_latency = std_latency = throughput = 0
        
        success_rate = (total_requests - errors) / total_requests if total_requests > 0 else 0
        
        # Get embedding dimension
        dimension = await self._get_model_dimension(model_name)
        
        result = ModelPerformanceResult(
            model_name=model_name,
            model_type=self._get_model_type(model_name),
            mean_latency_ms=mean_latency,
            p50_latency_ms=p50_latency,
            p95_latency_ms=p95_latency,
            p99_latency_ms=p99_latency,
            std_latency_ms=std_latency,
            throughput_per_sec=throughput,
            success_rate=success_rate,
            error_count=errors,
            total_requests=total_requests,
            embedding_dimension=dimension
        )
        
        logger.info(f"Performance benchmark completed: {model_name}",
                   mean_latency_ms=mean_latency,
                   p95_latency_ms=p95_latency,
                   success_rate=success_rate)
        
        return result
    
    async def assess_model_quality(self, model_name: str) -> ModelQualityResult:
        """Assess embedding quality using various metrics."""
        logger.info(f"Assessing quality: {model_name}")
        
        try:
            # Generate embeddings for test texts
            test_batch = self._create_test_batch(len(self.test_texts))
            embeddings_result = await self._run_embedding_inference(model_name, test_batch)
            
            # Extract embedding vectors
            embeddings = [result["vector"] for result in embeddings_result]
            embeddings_array = np.array(embeddings)
            
            # Calculate cosine similarity statistics
            similarity_matrix = cosine_similarity(embeddings_array)
            # Remove diagonal (self-similarity)
            similarity_values = similarity_matrix[np.triu_indices_from(similarity_matrix, k=1)]
            
            cosine_mean = float(np.mean(similarity_values))
            cosine_std = float(np.std(similarity_values))
            
            # Clustering quality assessment
            if len(embeddings) >= self.quality_test_config["clustering_n_clusters"]:
                kmeans = KMeans(n_clusters=self.quality_test_config["clustering_n_clusters"], random_state=42, n_init=10)
                cluster_labels = kmeans.fit_predict(embeddings_array)
                silhouette = silhouette_score(embeddings_array, cluster_labels)
            else:
                silhouette = 0.0
            
            # Semantic consistency (similar texts should have high similarity)
            semantic_consistency = self._calculate_semantic_consistency(embeddings_array)
            
            # Noise robustness (small perturbations shouldn't change embeddings much)
            noise_robustness = await self._calculate_noise_robustness(model_name, test_batch[:5])
            
            result = ModelQualityResult(
                model_name=model_name,
                model_type=self._get_model_type(model_name),
                embedding_dimension=len(embeddings[0]) if embeddings else 0,
                cosine_similarity_mean=cosine_mean,
                cosine_similarity_std=cosine_std,
                clustering_silhouette_score=silhouette,
                semantic_consistency_score=semantic_consistency,
                noise_robustness_score=noise_robustness,
                sample_embeddings=embeddings[:5]  # Store first 5 for analysis
            )
            
            logger.info(f"Quality assessment completed: {model_name}",
                       cosine_similarity_mean=cosine_mean,
                       silhouette_score=silhouette,
                       semantic_consistency=semantic_consistency)
            
            return result
            
        except Exception as e:
            logger.error(f"Quality assessment failed: {model_name}", exc_info=e)
            raise
    
    def _create_test_batch(self, batch_size: int) -> List[Any]:
        """Create test batch with Korean financial texts."""
        texts = (self.test_texts * ((batch_size // len(self.test_texts)) + 1))[:batch_size]
        
        return [type('TestItem', (), {
            'id': f'test_{i}',
            'text': text
        })() for i, text in enumerate(texts)]
    
    async def _run_embedding_inference(self, model_name: str, test_batch: List[Any]) -> List[Dict[str, Any]]:
        """Run embedding inference using appropriate service."""
        if model_name == "mock":
            results = await self.mock_service.embed_batch(test_batch)
        elif model_name.startswith("onnx_"):
            actual_model = model_name[5:]  # Remove "onnx_" prefix
            onnx_results = await self.onnx_service.embed_batch_onnx(test_batch, actual_model)
            results = [{"id": r.id, "vector": r.vector, "norm": r.norm} for r in onnx_results]
        else:
            # Use deep learning service
            model_type = ModelType.KOBERT if "kobert" in model_name.lower() else ModelType.ROBERTA_KOREAN
            deep_results = await self.deep_service.embed_batch(test_batch, model_type)
            results = [{"id": r.id, "vector": r.vector, "norm": r.norm} for r in deep_results]
        
        return results
    
    async def _get_model_dimension(self, model_name: str) -> int:
        """Get embedding dimension for a model."""
        if model_name == "mock":
            return 384
        elif "kobert" in model_name.lower():
            return 768
        elif "roberta" in model_name.lower():
            return 1024
        elif "sentence" in model_name.lower():
            return 384
        else:
            return 384  # Default
    
    def _get_model_type(self, model_name: str) -> str:
        """Get model type classification."""
        if model_name == "mock":
            return "mock"
        elif model_name.startswith("onnx_"):
            return "onnx_optimized"
        elif "kobert" in model_name.lower():
            return "deep_learning_kobert"
        elif "roberta" in model_name.lower():
            return "deep_learning_roberta"
        else:
            return "unknown"
    
    def _calculate_semantic_consistency(self, embeddings: np.ndarray) -> float:
        """Calculate semantic consistency score."""
        # In a real implementation, this would use domain-specific similarity rules
        # For now, we'll use a simple variance-based metric
        similarities = cosine_similarity(embeddings)
        # Higher consistency = lower variance in similarities
        variance = np.var(similarities)
        consistency = max(0.0, 1.0 - variance)
        return float(consistency)
    
    async def _calculate_noise_robustness(self, model_name: str, test_batch: List[Any]) -> float:
        """Calculate robustness to input noise."""
        try:
            # Generate original embeddings
            original_results = await self._run_embedding_inference(model_name, test_batch)
            original_embeddings = np.array([r["vector"] for r in original_results])
            
            # Generate noisy versions (add small character variations)
            noisy_batch = []
            for item in test_batch:
                # Add minor text variations
                noisy_text = item.text.replace('다', '다.').replace('했다', '했습니다')
                noisy_item = type('NoisyItem', (), {
                    'id': f'noisy_{item.id}',
                    'text': noisy_text
                })()
                noisy_batch.append(noisy_item)
            
            # Generate noisy embeddings
            noisy_results = await self._run_embedding_inference(model_name, noisy_batch)
            noisy_embeddings = np.array([r["vector"] for r in noisy_results])
            
            # Calculate similarity between original and noisy
            similarities = []
            for i in range(len(original_embeddings)):
                sim = cosine_similarity([original_embeddings[i]], [noisy_embeddings[i]])[0][0]
                similarities.append(sim)
            
            # Higher robustness = higher average similarity
            robustness = float(np.mean(similarities))
            return robustness
            
        except Exception as e:
            logger.warning(f"Noise robustness calculation failed for {model_name}", exc_info=e)
            return 0.5  # Default moderate robustness
    
    def _analyze_test_results(self, model_a: str, model_b: str, 
                            performance_results: Dict, quality_results: Dict,
                            mode: ComparisonMode) -> Tuple[Optional[str], float, str]:
        """Analyze test results and determine winner."""
        scores = {model_a: 0.0, model_b: 0.0}
        
        # Performance scoring (if available)
        if performance_results:
            perf_a = performance_results[model_a]
            perf_b = performance_results[model_b]
            
            # Lower latency is better
            if perf_a["p95_latency_ms"] < perf_b["p95_latency_ms"]:
                scores[model_a] += 2.0
            else:
                scores[model_b] += 2.0
            
            # Higher success rate is better
            if perf_a["success_rate"] > perf_b["success_rate"]:
                scores[model_a] += 1.0
            else:
                scores[model_b] += 1.0
        
        # Quality scoring (if available)
        if quality_results:
            qual_a = quality_results[model_a]
            qual_b = quality_results[model_b]
            
            # Higher silhouette score is better
            if qual_a["clustering_silhouette_score"] > qual_b["clustering_silhouette_score"]:
                scores[model_a] += 2.0
            else:
                scores[model_b] += 2.0
            
            # Higher semantic consistency is better
            if qual_a["semantic_consistency_score"] > qual_b["semantic_consistency_score"]:
                scores[model_a] += 1.0
            else:
                scores[model_b] += 1.0
        
        # Determine winner
        if scores[model_a] > scores[model_b]:
            winner = model_a
            confidence = min(0.95, 0.6 + (scores[model_a] - scores[model_b]) / 10)
        elif scores[model_b] > scores[model_a]:
            winner = model_b
            confidence = min(0.95, 0.6 + (scores[model_b] - scores[model_a]) / 10)
        else:
            winner = None
            confidence = 0.5
        
        # Generate recommendation
        if winner:
            recommendation = f"Recommend {winner} based on {mode.value} testing. "
            if performance_results and winner in performance_results:
                p95 = performance_results[winner]["p95_latency_ms"]
                recommendation += f"P95 latency: {p95:.1f}ms. "
            if quality_results and winner in quality_results:
                sil = quality_results[winner]["clustering_silhouette_score"]
                recommendation += f"Silhouette score: {sil:.3f}."
        else:
            recommendation = f"Models show similar performance in {mode.value} testing. Consider other factors for selection."
        
        return winner, confidence, recommendation
    
    def _generate_test_id(self, model_a: str, model_b: str) -> str:
        """Generate unique test ID."""
        timestamp = str(int(time.time()))
        content = f"{model_a}_vs_{model_b}_{timestamp}"
        return hashlib.md5(content.encode()).hexdigest()[:12]
    
    async def get_test_history(self) -> Dict[str, Any]:
        """Get A/B test history and summary statistics."""
        return {
            "total_tests": len(self.last_test_results),
            "recent_tests": [asdict(result) for result in list(self.last_test_results.values())[-10:]],
            "model_win_rates": self._calculate_win_rates(),
            "average_test_duration": self._calculate_avg_duration()
        }
    
    def _calculate_win_rates(self) -> Dict[str, float]:
        """Calculate win rates for each model."""
        wins = {}
        total = {}
        
        for result in self.last_test_results.values():
            models = [result.model_a, result.model_b]
            for model in models:
                total[model] = total.get(model, 0) + 1
                if result.winner == model:
                    wins[model] = wins.get(model, 0) + 1
        
        return {model: wins.get(model, 0) / total[model] for model in total}
    
    def _calculate_avg_duration(self) -> float:
        """Calculate average test duration."""
        if not self.last_test_results:
            return 0.0
        
        durations = [result.test_duration_sec for result in self.last_test_results.values()]
        return statistics.mean(durations)
    
    async def cleanup(self):
        """Cleanup resources."""
        logger.info("Cleaning up ModelComparisonService")
        await self.deep_service.cleanup()
        await self.onnx_service.cleanup()


# Global instance
model_comparison_service = None


def get_model_comparison_service(settings: Settings) -> ModelComparisonService:
    """Get or create model comparison service instance."""
    global model_comparison_service
    if model_comparison_service is None:
        model_comparison_service = ModelComparisonService(settings)
    return model_comparison_service