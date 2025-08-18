"""
F3: Advanced Clustering Service
Provides HDBSCAN, K-means mini-batch, and clustering quality metrics
"""

import numpy as np
import logging
from typing import List, Dict, Tuple, Any, Optional
from dataclasses import dataclass
import time

try:
    from sklearn.cluster import HDBSCAN, MiniBatchKMeans
    from sklearn.metrics import silhouette_score, davies_bouldin_score, calinski_harabasz_score
    from sklearn.preprocessing import StandardScaler
    SKLEARN_AVAILABLE = True
except ImportError:
    SKLEARN_AVAILABLE = False
    logging.warning("scikit-learn not available, clustering services will be disabled")

logger = logging.getLogger(__name__)


@dataclass
class ClusteringRequest:
    """Request for clustering operations"""
    embeddings: List[List[float]]
    ids: List[str]
    algorithm_params: Dict[str, Any]


@dataclass
class ClusteringResponse:
    """Response from clustering operations"""
    success: bool
    message: str
    cluster_labels: List[int]
    metadata: Dict[str, Any]


@dataclass
class QualityMetricsResponse:
    """Response for clustering quality metrics"""
    silhouette_score: float
    davies_bouldin_index: float
    calinski_harabasz_index: float


class AdvancedClusteringService:
    """Service for advanced clustering algorithms"""
    
    def __init__(self):
        self.available = SKLEARN_AVAILABLE
        if not self.available:
            logger.warning("Clustering service unavailable: scikit-learn not installed")
    
    def check_availability(self) -> bool:
        """Check if clustering services are available"""
        return self.available
    
    def hdbscan_clustering(
        self,
        embeddings: List[List[float]],
        ids: List[str],
        min_cluster_size: int = 3,
        min_samples: int = 2,
        cluster_selection_epsilon: float = 0.3,
        **kwargs
    ) -> ClusteringResponse:
        """
        Perform HDBSCAN clustering on embeddings
        
        Args:
            embeddings: List of embedding vectors
            ids: List of corresponding item IDs
            min_cluster_size: Minimum size of clusters
            min_samples: Minimum samples in cluster core
            cluster_selection_epsilon: Distance threshold for cluster selection
        """
        if not self.available:
            return ClusteringResponse(
                success=False,
                message="HDBSCAN clustering unavailable: scikit-learn not installed",
                cluster_labels=[],
                metadata={}
            )
        
        try:
            start_time = time.time()
            
            # Validate input
            if len(embeddings) != len(ids):
                return ClusteringResponse(
                    success=False,
                    message="Embeddings and IDs length mismatch",
                    cluster_labels=[],
                    metadata={}
                )
            
            if len(embeddings) < min_cluster_size:
                return ClusteringResponse(
                    success=False,
                    message=f"Insufficient data points: {len(embeddings)} < {min_cluster_size}",
                    cluster_labels=[],
                    metadata={}
                )
            
            # Convert to numpy array and normalize
            X = np.array(embeddings, dtype=np.float32)
            
            # Standardize features for better clustering
            scaler = StandardScaler()
            X_scaled = scaler.fit_transform(X)
            
            # Perform HDBSCAN clustering
            logger.info(f"Starting HDBSCAN clustering for {len(embeddings)} items")
            
            clusterer = HDBSCAN(
                min_cluster_size=min_cluster_size,
                min_samples=min_samples,
                cluster_selection_epsilon=cluster_selection_epsilon,
                metric='euclidean',
                cluster_selection_method='eom',  # Excess of Mass
                **kwargs
            )
            
            cluster_labels = clusterer.fit_predict(X_scaled)
            
            # Calculate statistics
            unique_labels = np.unique(cluster_labels)
            n_clusters = len(unique_labels) - (1 if -1 in unique_labels else 0)
            n_noise = np.sum(cluster_labels == -1)
            
            # Get cluster probabilities if available
            cluster_probabilities = []
            if hasattr(clusterer, 'probabilities_'):
                cluster_probabilities = clusterer.probabilities_.tolist()
            
            processing_time = time.time() - start_time
            
            metadata = {
                "n_clusters": int(n_clusters),
                "n_noise_points": int(n_noise),
                "processing_time_ms": round(processing_time * 1000, 2),
                "algorithm": "HDBSCAN",
                "parameters": {
                    "min_cluster_size": int(min_cluster_size),
                    "min_samples": int(min_samples),
                    "cluster_selection_epsilon": float(cluster_selection_epsilon)
                },
                "cluster_probabilities": cluster_probabilities
            }
            
            logger.info(f"HDBSCAN completed: {n_clusters} clusters, {n_noise} noise points in {processing_time:.3f}s")
            
            return ClusteringResponse(
                success=True,
                message="HDBSCAN clustering completed successfully",
                cluster_labels=[int(label) for label in cluster_labels],
                metadata=metadata
            )
            
        except Exception as e:
            logger.error(f"HDBSCAN clustering failed: {str(e)}")
            return ClusteringResponse(
                success=False,
                message=f"HDBSCAN clustering failed: {str(e)}",
                cluster_labels=[],
                metadata={}
            )
    
    def kmeans_minibatch_clustering(
        self,
        embeddings: List[List[float]],
        ids: List[str],
        n_clusters: int,
        batch_size: int = 1000,
        max_iter: int = 100,
        random_state: int = 42,
        **kwargs
    ) -> ClusteringResponse:
        """
        Perform K-means mini-batch clustering on embeddings
        
        Args:
            embeddings: List of embedding vectors
            ids: List of corresponding item IDs
            n_clusters: Number of clusters to form
            batch_size: Size of mini-batches
            max_iter: Maximum number of iterations
            random_state: Random seed for reproducibility
        """
        if not self.available:
            return ClusteringResponse(
                success=False,
                message="K-means clustering unavailable: scikit-learn not installed",
                cluster_labels=[],
                metadata={}
            )
        
        try:
            start_time = time.time()
            
            # Validate input
            if len(embeddings) != len(ids):
                return ClusteringResponse(
                    success=False,
                    message="Embeddings and IDs length mismatch",
                    cluster_labels=[],
                    metadata={}
                )
            
            if len(embeddings) < n_clusters:
                return ClusteringResponse(
                    success=False,
                    message=f"Insufficient data points: {len(embeddings)} < {n_clusters}",
                    cluster_labels=[],
                    metadata={}
                )
            
            # Convert to numpy array and normalize
            X = np.array(embeddings, dtype=np.float32)
            
            # Standardize features for better clustering
            scaler = StandardScaler()
            X_scaled = scaler.fit_transform(X)
            
            # Perform K-means mini-batch clustering
            logger.info(f"Starting K-means mini-batch clustering for {len(embeddings)} items into {n_clusters} clusters")
            
            clusterer = MiniBatchKMeans(
                n_clusters=n_clusters,
                batch_size=batch_size,
                max_iter=max_iter,
                random_state=random_state,
                n_init=3,
                reassignment_ratio=0.01,
                **kwargs
            )
            
            cluster_labels = clusterer.fit_predict(X_scaled)
            
            # Calculate statistics
            inertia = clusterer.inertia_
            cluster_centers = clusterer.cluster_centers_.tolist()
            
            processing_time = time.time() - start_time
            
            metadata = {
                "n_clusters": int(n_clusters),
                "inertia": float(inertia),
                "processing_time_ms": round(processing_time * 1000, 2),
                "algorithm": "K-means-MiniBatch",
                "parameters": {
                    "n_clusters": int(n_clusters),
                    "batch_size": int(batch_size),
                    "max_iter": int(max_iter),
                    "random_state": int(random_state)
                },
                "cluster_centers": cluster_centers,
                "n_iter": int(clusterer.n_iter_)
            }
            
            logger.info(f"K-means completed: {n_clusters} clusters, inertia={inertia:.3f} in {processing_time:.3f}s")
            
            return ClusteringResponse(
                success=True,
                message="K-means mini-batch clustering completed successfully",
                cluster_labels=[int(label) for label in cluster_labels],
                metadata=metadata
            )
            
        except Exception as e:
            logger.error(f"K-means clustering failed: {str(e)}")
            return ClusteringResponse(
                success=False,
                message=f"K-means clustering failed: {str(e)}",
                cluster_labels=[],
                metadata={}
            )
    
    def calculate_quality_metrics(
        self,
        embeddings: List[List[float]],
        labels: List[int]
    ) -> QualityMetricsResponse:
        """
        Calculate clustering quality metrics
        
        Args:
            embeddings: List of embedding vectors
            labels: Cluster labels
        """
        if not self.available:
            return QualityMetricsResponse(
                silhouette_score=0.0,
                davies_bouldin_index=float('inf'),
                calinski_harabasz_index=0.0
            )
        
        try:
            # Convert to numpy arrays
            X = np.array(embeddings, dtype=np.float32)
            labels_array = np.array(labels)
            
            # Remove noise points for quality metrics (label = -1)
            valid_mask = labels_array >= 0
            if np.sum(valid_mask) < 2:
                logger.warning("Insufficient non-noise points for quality metrics")
                return QualityMetricsResponse(
                    silhouette_score=0.0,
                    davies_bouldin_index=float('inf'),
                    calinski_harabasz_index=0.0
                )
            
            X_valid = X[valid_mask]
            labels_valid = labels_array[valid_mask]
            
            # Check if we have at least 2 clusters
            unique_labels = np.unique(labels_valid)
            if len(unique_labels) < 2:
                logger.warning("Need at least 2 clusters for quality metrics")
                return QualityMetricsResponse(
                    silhouette_score=0.0,
                    davies_bouldin_index=float('inf'),
                    calinski_harabasz_index=0.0
                )
            
            # Calculate quality metrics
            sil_score = silhouette_score(X_valid, labels_valid)
            db_index = davies_bouldin_score(X_valid, labels_valid)
            ch_index = calinski_harabasz_score(X_valid, labels_valid)
            
            logger.info(f"Quality metrics: silhouette={sil_score:.3f}, DB={db_index:.3f}, CH={ch_index:.3f}")
            
            return QualityMetricsResponse(
                silhouette_score=sil_score,
                davies_bouldin_index=db_index,
                calinski_harabasz_index=ch_index
            )
            
        except Exception as e:
            logger.error(f"Failed to calculate quality metrics: {str(e)}")
            return QualityMetricsResponse(
                silhouette_score=0.0,
                davies_bouldin_index=float('inf'),
                calinski_harabasz_index=0.0
            )
    
    def health_check(self) -> Dict[str, Any]:
        """Health check for clustering service"""
        return {
            "service": "clustering",
            "available": self.available,
            "algorithms": ["HDBSCAN", "K-means-MiniBatch"] if self.available else [],
            "dependencies": {
                "scikit-learn": SKLEARN_AVAILABLE
            }
        }


# Global service instance
clustering_service = AdvancedClusteringService()