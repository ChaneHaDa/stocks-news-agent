package com.newsagent.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.newsagent.api.entity.News;
import com.newsagent.api.entity.NewsEmbedding;
import com.newsagent.api.entity.NewsTopic;
import com.newsagent.api.repository.NewsEmbeddingRepository;
import com.newsagent.api.repository.NewsRepository;
import com.newsagent.api.repository.NewsTopicRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * F3: Advanced Clustering Service
 * Provides HDBSCAN, K-means mini-batch, and other advanced clustering algorithms
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdvancedClusteringService {
    
    private final NewsRepository newsRepository;
    private final NewsEmbeddingRepository newsEmbeddingRepository;
    private final NewsTopicRepository newsTopicRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final FeatureFlagService featureFlagService;
    
    @Value("${ml.service.url:http://localhost:8001}")
    private String mlServiceUrl;
    
    // Advanced clustering parameters
    private static final int MIN_CLUSTER_SIZE = 3;           // Minimum articles per cluster for HDBSCAN
    private static final int MIN_SAMPLES = 2;               // HDBSCAN min_samples parameter
    private static final double EPS = 0.3;                  // DBSCAN/HDBSCAN distance threshold
    private static final int MAX_RECENT_HOURS = 48;         // Only cluster news from last 48 hours
    private static final int BATCH_SIZE = 1000;             // Mini-batch size for K-means
    private static final int MAX_ITERATIONS = 100;          // Max iterations for clustering algorithms
    
    /**
     * Perform advanced clustering using HDBSCAN algorithm
     */
    @Transactional
    public AdvancedClusteringResult performHDBSCANClustering() {
        if (!featureFlagService.isEnabled("clustering.hdbscan.enabled", true)) {
            log.debug("HDBSCAN clustering is disabled");
            return AdvancedClusteringResult.builder()
                .algorithm("HDBSCAN")
                .success(false)
                .message("HDBSCAN clustering disabled via feature flag")
                .build();
        }
        
        log.info("Starting HDBSCAN clustering for recent news");
        
        AdvancedClusteringResult result = AdvancedClusteringResult.builder()
            .algorithm("HDBSCAN")
            .startTime(OffsetDateTime.now())
            .build();
        
        try {
            // Get recent news articles with embeddings
            List<NewsWithEmbedding> newsWithEmbeddings = getRecentNewsWithEmbeddings();
            
            if (newsWithEmbeddings.size() < MIN_CLUSTER_SIZE) {
                log.warn("Insufficient news articles for HDBSCAN clustering: {}", newsWithEmbeddings.size());
                return result.toBuilder()
                    .success(false)
                    .message("Insufficient articles for clustering")
                    .articlesProcessed(newsWithEmbeddings.size())
                    .build();
            }
            
            // Prepare embeddings for ML service
            List<List<Double>> embeddings = newsWithEmbeddings.stream()
                .map(nwe -> nwe.getEmbedding().getEmbeddingVector())
                .collect(Collectors.toList());
            
            List<String> newsIds = newsWithEmbeddings.stream()
                .map(nwe -> nwe.getNews().getId().toString())
                .collect(Collectors.toList());
            
            // Call ML service for HDBSCAN clustering
            HDBSCANClusterResponse clusterResponse = callHDBSCANService(embeddings, newsIds);
            
            if (!clusterResponse.isSuccess()) {
                log.error("HDBSCAN clustering failed: {}", clusterResponse.getMessage());
                return result.toBuilder()
                    .success(false)
                    .message("ML service clustering failed: " + clusterResponse.getMessage())
                    .articlesProcessed(newsWithEmbeddings.size())
                    .build();
            }
            
            // Process clustering results
            int clustersCreated = processClusteringResults(
                newsWithEmbeddings, 
                clusterResponse.getClusterLabels(),
                "HDBSCAN"
            );
            
            // Calculate clustering quality metrics
            ClusterQualityMetrics qualityMetrics = calculateQualityMetrics(
                embeddings, 
                clusterResponse.getClusterLabels()
            );
            
            result = result.toBuilder()
                .success(true)
                .endTime(OffsetDateTime.now())
                .articlesProcessed(newsWithEmbeddings.size())
                .clustersCreated(clustersCreated)
                .noisePoints(clusterResponse.getNoisePoints())
                .silhouetteScore(qualityMetrics.getSilhouetteScore())
                .daviesBouldinIndex(qualityMetrics.getDaviesBouldinIndex())
                .calinskiHarabaszIndex(qualityMetrics.getCalinskiHarabaszIndex())
                .message("HDBSCAN clustering completed successfully")
                .build();
            
            log.info("HDBSCAN clustering completed: {} articles, {} clusters, silhouette={:.3f}", 
                newsWithEmbeddings.size(), clustersCreated, qualityMetrics.getSilhouetteScore());
            
            return result;
            
        } catch (Exception e) {
            log.error("Error during HDBSCAN clustering", e);
            return result.toBuilder()
                .success(false)
                .endTime(OffsetDateTime.now())
                .message("Clustering failed: " + e.getMessage())
                .build();
        }
    }
    
    /**
     * Perform K-means mini-batch clustering for large datasets
     */
    @Transactional
    public AdvancedClusteringResult performKMeansMiniBatchClustering(int numClusters) {
        if (!featureFlagService.isEnabled("clustering.kmeans.enabled", true)) {
            log.debug("K-means clustering is disabled");
            return AdvancedClusteringResult.builder()
                .algorithm("K-means-MiniBatch")
                .success(false)
                .message("K-means clustering disabled via feature flag")
                .build();
        }
        
        log.info("Starting K-means mini-batch clustering with {} clusters", numClusters);
        
        AdvancedClusteringResult result = AdvancedClusteringResult.builder()
            .algorithm("K-means-MiniBatch")
            .startTime(OffsetDateTime.now())
            .build();
        
        try {
            // Get recent news articles with embeddings
            List<NewsWithEmbedding> newsWithEmbeddings = getRecentNewsWithEmbeddings();
            
            if (newsWithEmbeddings.size() < numClusters) {
                log.warn("Insufficient news articles for K-means clustering: {} < {}", 
                    newsWithEmbeddings.size(), numClusters);
                return result.toBuilder()
                    .success(false)
                    .message("Insufficient articles for " + numClusters + " clusters")
                    .articlesProcessed(newsWithEmbeddings.size())
                    .build();
            }
            
            // Prepare embeddings for ML service
            List<List<Double>> embeddings = newsWithEmbeddings.stream()
                .map(nwe -> nwe.getEmbedding().getEmbeddingVector())
                .collect(Collectors.toList());
            
            List<String> newsIds = newsWithEmbeddings.stream()
                .map(nwe -> nwe.getNews().getId().toString())
                .collect(Collectors.toList());
            
            // Call ML service for K-means clustering
            KMeansClusterResponse clusterResponse = callKMeansService(embeddings, newsIds, numClusters);
            
            if (!clusterResponse.isSuccess()) {
                log.error("K-means clustering failed: {}", clusterResponse.getMessage());
                return result.toBuilder()
                    .success(false)
                    .message("ML service clustering failed: " + clusterResponse.getMessage())
                    .articlesProcessed(newsWithEmbeddings.size())
                    .build();
            }
            
            // Process clustering results
            int clustersCreated = processClusteringResults(
                newsWithEmbeddings, 
                clusterResponse.getClusterLabels(),
                "K-means-MiniBatch"
            );
            
            // Calculate clustering quality metrics
            ClusterQualityMetrics qualityMetrics = calculateQualityMetrics(
                embeddings, 
                clusterResponse.getClusterLabels()
            );
            
            result = result.toBuilder()
                .success(true)
                .endTime(OffsetDateTime.now())
                .articlesProcessed(newsWithEmbeddings.size())
                .clustersCreated(clustersCreated)
                .inertia(clusterResponse.getInertia())
                .silhouetteScore(qualityMetrics.getSilhouetteScore())
                .daviesBouldinIndex(qualityMetrics.getDaviesBouldinIndex())
                .calinskiHarabaszIndex(qualityMetrics.getCalinskiHarabaszIndex())
                .message("K-means mini-batch clustering completed successfully")
                .build();
            
            log.info("K-means clustering completed: {} articles, {} clusters, silhouette={:.3f}", 
                newsWithEmbeddings.size(), clustersCreated, qualityMetrics.getSilhouetteScore());
            
            return result;
            
        } catch (Exception e) {
            log.error("Error during K-means clustering", e);
            return result.toBuilder()
                .success(false)
                .endTime(OffsetDateTime.now())
                .message("Clustering failed: " + e.getMessage())
                .build();
        }
    }
    
    /**
     * Automatically determine optimal number of clusters using elbow method and silhouette analysis
     */
    public OptimalClusterResult findOptimalClusters() {
        log.info("Finding optimal number of clusters");
        
        try {
            List<NewsWithEmbedding> newsWithEmbeddings = getRecentNewsWithEmbeddings();
            
            if (newsWithEmbeddings.size() < 10) {
                return OptimalClusterResult.builder()
                    .success(false)
                    .message("Insufficient data for cluster optimization")
                    .build();
            }
            
            List<List<Double>> embeddings = newsWithEmbeddings.stream()
                .map(nwe -> nwe.getEmbedding().getEmbeddingVector())
                .collect(Collectors.toList());
            
            // Test different numbers of clusters (2 to sqrt(n))
            int maxClusters = Math.min(20, (int) Math.sqrt(newsWithEmbeddings.size()));
            List<ClusterEvaluation> evaluations = new ArrayList<>();
            
            for (int k = 2; k <= maxClusters; k++) {
                try {
                    KMeansClusterResponse response = callKMeansService(
                        embeddings, 
                        newsWithEmbeddings.stream()
                            .map(nwe -> nwe.getNews().getId().toString())
                            .collect(Collectors.toList()), 
                        k
                    );
                    
                    if (response.isSuccess()) {
                        ClusterQualityMetrics metrics = calculateQualityMetrics(
                            embeddings, 
                            response.getClusterLabels()
                        );
                        
                        evaluations.add(ClusterEvaluation.builder()
                            .numClusters(k)
                            .inertia(response.getInertia())
                            .silhouetteScore(metrics.getSilhouetteScore())
                            .daviesBouldinIndex(metrics.getDaviesBouldinIndex())
                            .calinskiHarabaszIndex(metrics.getCalinskiHarabaszIndex())
                            .build());
                    }
                } catch (Exception e) {
                    log.warn("Failed to evaluate {} clusters: {}", k, e.getMessage());
                }
            }
            
            if (evaluations.isEmpty()) {
                return OptimalClusterResult.builder()
                    .success(false)
                    .message("No valid cluster evaluations found")
                    .build();
            }
            
            // Find optimal number using combined scoring
            OptimalClusterResult optimal = findOptimalFromEvaluations(evaluations);
            
            log.info("Optimal cluster analysis: {} clusters recommended (silhouette={:.3f})", 
                optimal.getOptimalClusters(), optimal.getBestSilhouetteScore());
            
            return optimal;
            
        } catch (Exception e) {
            log.error("Error finding optimal clusters", e);
            return OptimalClusterResult.builder()
                .success(false)
                .message("Optimization failed: " + e.getMessage())
                .build();
        }
    }
    
    /**
     * Get recent news articles with their embeddings
     */
    private List<NewsWithEmbedding> getRecentNewsWithEmbeddings() {
        OffsetDateTime cutoffTime = OffsetDateTime.now().minusHours(MAX_RECENT_HOURS);
        
        List<News> recentNews = newsRepository.findRecentNewsWithEmbeddings(cutoffTime);
        
        return recentNews.stream()
            .map(news -> {
                Optional<NewsEmbedding> embeddingOpt = newsEmbeddingRepository.findByNewsId(news.getId());
                return embeddingOpt.map(embedding -> 
                    NewsWithEmbedding.builder()
                        .news(news)
                        .embedding(embedding)
                        .build()
                ).orElse(null);
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
    
    /**
     * Call ML service for HDBSCAN clustering
     */
    private HDBSCANClusterResponse callHDBSCANService(List<List<Double>> embeddings, List<String> ids) {
        try {
            String url = mlServiceUrl + "/v1/cluster/hdbscan";
            
            Map<String, Object> request = Map.of(
                "embeddings", embeddings,
                "ids", ids,
                "min_cluster_size", MIN_CLUSTER_SIZE,
                "min_samples", MIN_SAMPLES,
                "cluster_selection_epsilon", EPS
            );
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, String.class);
            
            return objectMapper.readValue(response.getBody(), HDBSCANClusterResponse.class);
            
        } catch (Exception e) {
            log.error("Failed to call HDBSCAN service", e);
            return HDBSCANClusterResponse.builder()
                .success(false)
                .message("Service call failed: " + e.getMessage())
                .build();
        }
    }
    
    /**
     * Call ML service for K-means clustering
     */
    private KMeansClusterResponse callKMeansService(List<List<Double>> embeddings, List<String> ids, int numClusters) {
        try {
            String url = mlServiceUrl + "/v1/cluster/kmeans";
            
            Map<String, Object> request = Map.of(
                "embeddings", embeddings,
                "ids", ids,
                "n_clusters", numClusters,
                "batch_size", BATCH_SIZE,
                "max_iter", MAX_ITERATIONS,
                "random_state", 42
            );
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, String.class);
            
            return objectMapper.readValue(response.getBody(), KMeansClusterResponse.class);
            
        } catch (Exception e) {
            log.error("Failed to call K-means service", e);
            return KMeansClusterResponse.builder()
                .success(false)
                .message("Service call failed: " + e.getMessage())
                .build();
        }
    }
    
    /**
     * Process clustering results and save to database
     */
    private int processClusteringResults(
            List<NewsWithEmbedding> newsWithEmbeddings, 
            List<Integer> clusterLabels,
            String algorithm) {
        
        // Clear existing topic assignments for these news articles
        List<Long> newsIds = newsWithEmbeddings.stream()
            .map(nwe -> nwe.getNews().getId())
            .collect(Collectors.toList());
        
        // Clear existing topic assignments for these news articles
        List<NewsTopic> existingTopics = newsTopicRepository.findByNewsIdIn(newsIds);
        if (!existingTopics.isEmpty()) {
            newsTopicRepository.deleteAll(existingTopics);
        }
        
        // Create new topic assignments
        Set<Integer> uniqueClusters = new HashSet<>();
        List<NewsTopic> newTopics = new ArrayList<>();
        
        for (int i = 0; i < newsWithEmbeddings.size(); i++) {
            int clusterLabel = clusterLabels.get(i);
            
            // Skip noise points (label = -1 in HDBSCAN)
            if (clusterLabel >= 0) {
                uniqueClusters.add(clusterLabel);
                
                NewsWithEmbedding nwe = newsWithEmbeddings.get(i);
                
                NewsTopic topic = NewsTopic.builder()
                    .news(nwe.getNews())
                    .topicId(generateTopicId(algorithm, clusterLabel))
                    .clusteringMethod(algorithm)
                    .similarityScore(1.0) // Will be improved with actual confidence scores
                    .createdAt(OffsetDateTime.now())
                    .build();
                
                newTopics.add(topic);
            }
        }
        
        newsTopicRepository.saveAll(newTopics);
        
        log.info("Saved {} topic assignments for {} clusters using {}", 
            newTopics.size(), uniqueClusters.size(), algorithm);
        
        return uniqueClusters.size();
    }
    
    /**
     * Calculate clustering quality metrics
     */
    private ClusterQualityMetrics calculateQualityMetrics(
            List<List<Double>> embeddings, 
            List<Integer> clusterLabels) {
        
        try {
            String url = mlServiceUrl + "/v1/cluster/quality-metrics";
            
            Map<String, Object> request = Map.of(
                "embeddings", embeddings,
                "labels", clusterLabels
            );
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, String.class);
            
            return objectMapper.readValue(response.getBody(), ClusterQualityMetrics.class);
            
        } catch (Exception e) {
            log.warn("Failed to calculate quality metrics", e);
            return ClusterQualityMetrics.builder()
                .silhouetteScore(0.0)
                .daviesBouldinIndex(Double.MAX_VALUE)
                .calinskiHarabaszIndex(0.0)
                .build();
        }
    }
    
    /**
     * Find optimal number of clusters from evaluations
     */
    private OptimalClusterResult findOptimalFromEvaluations(List<ClusterEvaluation> evaluations) {
        // Normalize scores for combined scoring
        double maxSilhouette = evaluations.stream()
            .mapToDouble(ClusterEvaluation::getSilhouetteScore)
            .max().orElse(1.0);
        
        double minDaviesBouldin = evaluations.stream()
            .mapToDouble(ClusterEvaluation::getDaviesBouldinIndex)
            .min().orElse(1.0);
        
        double maxCalinskiHarabasz = evaluations.stream()
            .mapToDouble(ClusterEvaluation::getCalinskiHarabaszIndex)
            .max().orElse(1.0);
        
        // Calculate combined score (higher is better)
        ClusterEvaluation best = evaluations.stream()
            .max((e1, e2) -> {
                double score1 = calculateCombinedScore(e1, maxSilhouette, minDaviesBouldin, maxCalinskiHarabasz);
                double score2 = calculateCombinedScore(e2, maxSilhouette, minDaviesBouldin, maxCalinskiHarabasz);
                return Double.compare(score1, score2);
            })
            .orElse(evaluations.get(0));
        
        return OptimalClusterResult.builder()
            .success(true)
            .optimalClusters(best.getNumClusters())
            .bestSilhouetteScore(best.getSilhouetteScore())
            .bestDaviesBouldinIndex(best.getDaviesBouldinIndex())
            .bestCalinskiHarabaszIndex(best.getCalinskiHarabaszIndex())
            .evaluations(evaluations)
            .message("Optimal cluster count determined using combined scoring")
            .build();
    }
    
    private double calculateCombinedScore(ClusterEvaluation eval, double maxSil, double minDB, double maxCH) {
        // Normalize and combine scores (silhouette: higher better, DB: lower better, CH: higher better)
        double silScore = eval.getSilhouetteScore() / maxSil;
        double dbScore = minDB / eval.getDaviesBouldinIndex();
        double chScore = eval.getCalinskiHarabaszIndex() / maxCH;
        
        return 0.5 * silScore + 0.3 * dbScore + 0.2 * chScore;
    }
    
    private String generateTopicId(String algorithm, int clusterLabel) {
        return String.format("%s_%d_%d", 
            algorithm.toLowerCase(), 
            clusterLabel, 
            System.currentTimeMillis() % 100000);
    }
    
    // Data classes
    @lombok.Builder
    @lombok.Data
    public static class NewsWithEmbedding {
        private News news;
        private NewsEmbedding embedding;
    }
    
    @lombok.Builder(toBuilder = true)
    @lombok.Data
    public static class AdvancedClusteringResult {
        private String algorithm;
        private boolean success;
        private String message;
        private OffsetDateTime startTime;
        private OffsetDateTime endTime;
        private int articlesProcessed;
        private int clustersCreated;
        private int noisePoints;
        private double inertia;
        private double silhouetteScore;
        private double daviesBouldinIndex;
        private double calinskiHarabaszIndex;
    }
    
    @lombok.Builder
    @lombok.Data
    public static class HDBSCANClusterResponse {
        private boolean success;
        private String message;
        private List<Integer> clusterLabels;
        private int noisePoints;
        private List<Double> clusterProbabilities;
    }
    
    @lombok.Builder
    @lombok.Data
    public static class KMeansClusterResponse {
        private boolean success;
        private String message;
        private List<Integer> clusterLabels;
        private double inertia;
        private List<List<Double>> clusterCenters;
    }
    
    @lombok.Builder
    @lombok.Data
    public static class ClusterQualityMetrics {
        private double silhouetteScore;
        private double daviesBouldinIndex;
        private double calinskiHarabaszIndex;
    }
    
    @lombok.Builder
    @lombok.Data
    public static class ClusterEvaluation {
        private int numClusters;
        private double inertia;
        private double silhouetteScore;
        private double daviesBouldinIndex;
        private double calinskiHarabaszIndex;
    }
    
    @lombok.Builder
    @lombok.Data
    public static class OptimalClusterResult {
        private boolean success;
        private String message;
        private int optimalClusters;
        private double bestSilhouetteScore;
        private double bestDaviesBouldinIndex;
        private double bestCalinskiHarabaszIndex;
        private List<ClusterEvaluation> evaluations;
    }
}