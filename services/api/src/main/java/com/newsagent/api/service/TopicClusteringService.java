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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TopicClusteringService {
    
    private final NewsRepository newsRepository;
    private final NewsEmbeddingRepository newsEmbeddingRepository;
    private final NewsTopicRepository newsTopicRepository;
    private final EmbeddingService embeddingService;
    private final AdvancedClusteringService advancedClusteringService;
    private final FeatureFlagService featureFlagService;
    private final ObjectMapper objectMapper;
    
    // Clustering parameters
    private static final double SIMILARITY_THRESHOLD = 0.75; // Cosine similarity threshold for clustering
    private static final double DUPLICATE_THRESHOLD = 0.9;   // Threshold for duplicate detection
    private static final int MIN_CLUSTER_SIZE = 2;           // Minimum articles per cluster
    private static final int MAX_RECENT_HOURS = 48;          // Only cluster news from last 48 hours
    
    /**
     * Perform topic clustering on recent news articles using advanced algorithms
     */
    @Transactional
    public ClusteringResult performClustering() {
        log.info("Starting advanced topic clustering for recent news");
        
        ClusteringResult result = ClusteringResult.builder()
            .startTime(OffsetDateTime.now())
            .build();
        
        try {
            // Check which clustering algorithm to use
            String algorithm = featureFlagService.getString("clustering.algorithm", "HDBSCAN");
            boolean useAdvancedClustering = featureFlagService.isEnabled("clustering.advanced.enabled", true);
            
            AdvancedClusteringService.AdvancedClusteringResult advancedResult;
            
            if (useAdvancedClustering) {
                // Use advanced clustering algorithms
                switch (algorithm.toUpperCase()) {
                    case "HDBSCAN":
                        advancedResult = advancedClusteringService.performHDBSCANClustering();
                        break;
                    case "KMEANS":
                        // Find optimal cluster count first
                        AdvancedClusteringService.OptimalClusterResult optimal = 
                            advancedClusteringService.findOptimalClusters();
                        int numClusters = optimal.isSuccess() ? optimal.getOptimalClusters() : 5;
                        
                        advancedResult = advancedClusteringService.performKMeansMiniBatchClustering(numClusters);
                        break;
                    default:
                        log.warn("Unknown clustering algorithm: {}, falling back to HDBSCAN", algorithm);
                        advancedResult = advancedClusteringService.performHDBSCANClustering();
                }
                
                // Convert advanced result to legacy format
                result = convertAdvancedResult(advancedResult);
                
            } else {
                // Fall back to legacy cosine similarity clustering
                result = performLegacyClustering();
            }
            
            log.info("Clustering completed: {} articles processed, {} clusters created", 
                result.getTotalArticles(), result.getClustersGenerated());
            
            return result;
            
        } catch (Exception e) {
            log.error("Error during clustering", e);
            return result.toBuilder()
                .endTime(OffsetDateTime.now())
                .success(false)
                .errorMessage("Clustering failed: " + e.getMessage())
                .build();
        }
    }
    
    /**
     * Legacy clustering using cosine similarity (fallback)
     */
    private ClusteringResult performLegacyClustering() {
        log.info("Using legacy cosine similarity clustering");
        
        ClusteringResult result = ClusteringResult.builder()
            .startTime(OffsetDateTime.now())
            .build();
        
        try {
            // Get recent news articles with embeddings
            OffsetDateTime cutoffTime = OffsetDateTime.now().minusHours(MAX_RECENT_HOURS);
            List<News> recentNews = newsRepository.findRecentNewsWithEmbeddings(cutoffTime);
            
            result.setTotalArticles(recentNews.size());
            
            if (recentNews.isEmpty()) {
                log.info("No recent news articles found for clustering");
                result.setEndTime(OffsetDateTime.now());
                return result;
            }
            
            log.info("Found {} recent news articles for clustering", recentNews.size());
            
            // Load embeddings for these articles
            Map<Long, List<Float>> embeddings = loadEmbeddings(recentNews);
            result.setArticlesWithEmbeddings(embeddings.size());
            
            if (embeddings.isEmpty()) {
                log.warn("No embeddings found for recent news articles");
                result.setEndTime(OffsetDateTime.now());
                return result;
            }
            
            // Perform clustering
            Map<String, List<Long>> clusters = performSimpleClustering(embeddings);
            result.setClustersGenerated(clusters.size());
            
            // Detect duplicates within clusters
            Map<String, List<Long>> duplicateGroups = detectDuplicates(recentNews, embeddings);
            result.setDuplicateGroupsFound(duplicateGroups.size());
            
            // Extract topic keywords for each cluster
            Map<String, List<String>> topicKeywords = extractTopicKeywords(clusters, recentNews);
            
            // Save clustering results
            int savedCount = saveClusteringResults(clusters, duplicateGroups, topicKeywords, recentNews);
            result.setTopicsAssigned(savedCount);
            
            result.setEndTime(OffsetDateTime.now());
            
            log.info("Clustering completed: {} articles, {} clusters, {} duplicate groups, {} topics assigned",
                result.getTotalArticles(), result.getClustersGenerated(), 
                result.getDuplicateGroupsFound(), result.getTopicsAssigned());
            
            return result;
            
        } catch (Exception e) {
            log.error("Failed to perform topic clustering", e);
            result.setEndTime(OffsetDateTime.now());
            result.setError(e.getMessage());
            return result;
        }
    }
    
    /**
     * Load embeddings for given news articles
     */
    private Map<Long, List<Float>> loadEmbeddings(List<News> newsList) {
        Map<Long, List<Float>> embeddings = new HashMap<>();
        
        List<Long> newsIds = newsList.stream()
            .map(News::getId)
            .toList();
        
        List<NewsEmbedding> newsEmbeddings = newsEmbeddingRepository.findByNewsIdIn(newsIds);
        
        for (NewsEmbedding embedding : newsEmbeddings) {
            try {
                List<Float> vector = objectMapper.readValue(
                    embedding.getVectorText(), 
                    new TypeReference<List<Float>>() {}
                );
                embeddings.put(embedding.getNews().getId(), vector);
            } catch (Exception e) {
                log.warn("Failed to parse embedding for news ID: {}", embedding.getNews().getId(), e);
            }
        }
        
        return embeddings;
    }
    
    /**
     * Perform simple clustering based on cosine similarity
     */
    private Map<String, List<Long>> performSimpleClustering(Map<Long, List<Float>> embeddings) {
        Map<String, List<Long>> clusters = new HashMap<>();
        Set<Long> clustered = new HashSet<>();
        int clusterIndex = 0;
        
        List<Long> newsIds = new ArrayList<>(embeddings.keySet());
        
        for (int i = 0; i < newsIds.size(); i++) {
            Long newsId1 = newsIds.get(i);
            
            if (clustered.contains(newsId1)) {
                continue;
            }
            
            List<Long> cluster = new ArrayList<>();
            cluster.add(newsId1);
            clustered.add(newsId1);
            
            List<Float> vector1 = embeddings.get(newsId1);
            
            // Find similar articles
            for (int j = i + 1; j < newsIds.size(); j++) {
                Long newsId2 = newsIds.get(j);
                
                if (clustered.contains(newsId2)) {
                    continue;
                }
                
                List<Float> vector2 = embeddings.get(newsId2);
                double similarity = cosineSimilarity(vector1, vector2);
                
                if (similarity >= SIMILARITY_THRESHOLD) {
                    cluster.add(newsId2);
                    clustered.add(newsId2);
                }
            }
            
            // Only create cluster if it has minimum size
            if (cluster.size() >= MIN_CLUSTER_SIZE) {
                String clusterId = "topic_" + String.format("%03d", clusterIndex++);
                clusters.put(clusterId, cluster);
            } else {
                // Single article gets its own cluster
                String clusterId = "single_" + newsId1;
                clusters.put(clusterId, cluster);
            }
        }
        
        return clusters;
    }
    
    /**
     * Detect duplicate articles based on high similarity
     */
    private Map<String, List<Long>> detectDuplicates(List<News> newsList, Map<Long, List<Float>> embeddings) {
        Map<String, List<Long>> duplicateGroups = new HashMap<>();
        Set<Long> processed = new HashSet<>();
        int groupIndex = 0;
        
        Map<Long, News> newsMap = newsList.stream()
            .collect(Collectors.toMap(News::getId, news -> news));
        
        List<Long> newsIds = new ArrayList<>(embeddings.keySet());
        
        for (int i = 0; i < newsIds.size(); i++) {
            Long newsId1 = newsIds.get(i);
            
            if (processed.contains(newsId1)) {
                continue;
            }
            
            News news1 = newsMap.get(newsId1);
            List<Float> vector1 = embeddings.get(newsId1);
            List<Long> duplicates = new ArrayList<>();
            duplicates.add(newsId1);
            
            for (int j = i + 1; j < newsIds.size(); j++) {
                Long newsId2 = newsIds.get(j);
                
                if (processed.contains(newsId2)) {
                    continue;
                }
                
                News news2 = newsMap.get(newsId2);
                List<Float> vector2 = embeddings.get(newsId2);
                
                // Check both embedding similarity and title similarity
                double embeddingSimilarity = cosineSimilarity(vector1, vector2);
                double titleSimilarity = jaccardSimilarity(news1.getTitle(), news2.getTitle());
                
                if (embeddingSimilarity >= DUPLICATE_THRESHOLD || titleSimilarity >= DUPLICATE_THRESHOLD) {
                    duplicates.add(newsId2);
                    processed.add(newsId2);
                }
            }
            
            processed.add(newsId1);
            
            // Only create group if there are actual duplicates
            if (duplicates.size() > 1) {
                String groupId = "dup_" + String.format("%03d", groupIndex++);
                duplicateGroups.put(groupId, duplicates);
            }
        }
        
        return duplicateGroups;
    }
    
    /**
     * Extract topic keywords for each cluster
     */
    private Map<String, List<String>> extractTopicKeywords(Map<String, List<Long>> clusters, List<News> newsList) {
        Map<String, List<String>> topicKeywords = new HashMap<>();
        Map<Long, News> newsMap = newsList.stream()
            .collect(Collectors.toMap(News::getId, news -> news));
        
        for (Map.Entry<String, List<Long>> entry : clusters.entrySet()) {
            String clusterId = entry.getKey();
            List<Long> newsIds = entry.getValue();
            
            // Collect all text from cluster articles
            StringBuilder allText = new StringBuilder();
            for (Long newsId : newsIds) {
                News news = newsMap.get(newsId);
                if (news != null) {
                    allText.append(news.getTitle()).append(" ");
                    if (news.getBody() != null) {
                        allText.append(news.getBody()).append(" ");
                    }
                }
            }
            
            // Extract keywords using simple frequency analysis
            List<String> keywords = extractKeywords(allText.toString());
            topicKeywords.put(clusterId, keywords);
        }
        
        return topicKeywords;
    }
    
    /**
     * Save clustering results to database
     */
    @Transactional
    public int saveClusteringResults(Map<String, List<Long>> clusters, 
                                   Map<String, List<Long>> duplicateGroups,
                                   Map<String, List<String>> topicKeywords,
                                   List<News> newsList) {
        
        Map<Long, News> newsMap = newsList.stream()
            .collect(Collectors.toMap(News::getId, news -> news));
        
        // Map news to duplicate groups
        Map<Long, String> newsToGroupId = new HashMap<>();
        for (Map.Entry<String, List<Long>> entry : duplicateGroups.entrySet()) {
            String groupId = entry.getKey();
            for (Long newsId : entry.getValue()) {
                newsToGroupId.put(newsId, groupId);
            }
        }
        
        int savedCount = 0;
        
        for (Map.Entry<String, List<Long>> entry : clusters.entrySet()) {
            String topicId = entry.getKey();
            List<Long> newsIds = entry.getValue();
            List<String> keywords = topicKeywords.getOrDefault(topicId, new ArrayList<>());
            
            for (Long newsId : newsIds) {
                News news = newsMap.get(newsId);
                if (news == null) {
                    continue;
                }
                
                try {
                    // Check if topic already exists
                    Optional<NewsTopic> existingTopic = newsTopicRepository.findByNewsId(newsId);
                    
                    NewsTopic newsTopic;
                    if (existingTopic.isPresent()) {
                        newsTopic = existingTopic.get();
                        newsTopic.setUpdatedAt(OffsetDateTime.now());
                    } else {
                        newsTopic = NewsTopic.builder()
                            .news(news)
                            .createdAt(OffsetDateTime.now())
                            .build();
                    }
                    
                    newsTopic.setTopicId(topicId);
                    newsTopic.setGroupId(newsToGroupId.get(newsId));
                    newsTopic.setClusteringMethod("simple_cosine");
                    
                    // Convert keywords to JSON
                    try {
                        String keywordsJson = objectMapper.writeValueAsString(keywords);
                        newsTopic.setTopicKeywords(keywordsJson);
                    } catch (JsonProcessingException e) {
                        log.warn("Failed to serialize keywords for topic: {}", topicId, e);
                        newsTopic.setTopicKeywords("[]");
                    }
                    
                    newsTopicRepository.save(newsTopic);
                    savedCount++;
                    
                } catch (Exception e) {
                    log.error("Failed to save topic for news ID: {}", newsId, e);
                }
            }
        }
        
        return savedCount;
    }
    
    /**
     * Extract keywords from text using simple frequency analysis
     */
    private List<String> extractKeywords(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        // Korean stop words (simplified)
        Set<String> stopWords = Set.of(
            "이", "그", "저", "것", "들", "의", "가", "을", "를", "에", "는", "은", "과", "와", "한", "하", "하는", "했", "할",
            "있", "있는", "있다", "없", "없는", "없다", "위해", "때문", "통해", "대해", "관련", "등", "및", "또는", "그리고"
        );
        
        // Split text and count frequencies
        Map<String, Integer> wordCount = new HashMap<>();
        String[] words = text.toLowerCase()
            .replaceAll("[^가-힣a-z0-9\\s]", " ")
            .split("\\s+");
        
        for (String word : words) {
            word = word.trim();
            if (word.length() >= 2 && !stopWords.contains(word)) {
                wordCount.put(word, wordCount.getOrDefault(word, 0) + 1);
            }
        }
        
        // Return top keywords
        return wordCount.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(5)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }
    
    /**
     * Calculate cosine similarity between two vectors
     */
    private double cosineSimilarity(List<Float> vector1, List<Float> vector2) {
        if (vector1.size() != vector2.size()) {
            throw new IllegalArgumentException("Vector dimensions must match");
        }
        
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        
        for (int i = 0; i < vector1.size(); i++) {
            dotProduct += vector1.get(i) * vector2.get(i);
            norm1 += vector1.get(i) * vector1.get(i);
            norm2 += vector2.get(i) * vector2.get(i);
        }
        
        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
    
    /**
     * Calculate Jaccard similarity between two strings
     */
    private double jaccardSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null) {
            return 0.0;
        }
        
        Set<String> words1 = Set.of(text1.toLowerCase().split("\\s+"));
        Set<String> words2 = Set.of(text2.toLowerCase().split("\\s+"));
        
        Set<String> intersection = new HashSet<>(words1);
        intersection.retainAll(words2);
        
        Set<String> union = new HashSet<>(words1);
        union.addAll(words2);
        
        if (union.isEmpty()) {
            return 0.0;
        }
        
        return (double) intersection.size() / union.size();
    }
    
    @lombok.Builder(toBuilder = true)
    @lombok.Data
    public static class ClusteringResult {
        private OffsetDateTime startTime;
        private OffsetDateTime endTime;
        @lombok.Builder.Default
        private int totalArticles = 0;
        @lombok.Builder.Default
        private int articlesWithEmbeddings = 0;
        @lombok.Builder.Default
        private int clustersGenerated = 0;
        @lombok.Builder.Default
        private int duplicateGroupsFound = 0;
        @lombok.Builder.Default
        private int topicsAssigned = 0;
        @lombok.Builder.Default
        private boolean success = true;
        private String error;
        private String errorMessage;
        private Map<String, Object> qualityMetrics;
        
        public long getDurationMillis() {
            if (startTime != null && endTime != null) {
                return java.time.Duration.between(startTime, endTime).toMillis();
            }
            return 0;
        }
    }
    
    /**
     * Convert AdvancedClusteringResult to legacy ClusteringResult format
     */
    private ClusteringResult convertAdvancedResult(AdvancedClusteringService.AdvancedClusteringResult advancedResult) {
        return ClusteringResult.builder()
            .success(advancedResult.isSuccess())
            .startTime(advancedResult.getStartTime())
            .endTime(advancedResult.getEndTime())
            .totalArticles(advancedResult.getArticlesProcessed())
            .articlesWithEmbeddings(advancedResult.getArticlesProcessed())
            .clustersGenerated(advancedResult.getClustersCreated())
            .topicsAssigned(advancedResult.getArticlesProcessed() - advancedResult.getNoisePoints())
            .duplicateGroupsFound(0) // Advanced clustering doesn't track duplicates separately
            .qualityMetrics(Map.of(
                "algorithm", advancedResult.getAlgorithm(),
                "silhouette_score", advancedResult.getSilhouetteScore(),
                "davies_bouldin_index", advancedResult.getDaviesBouldinIndex(),
                "calinski_harabasz_index", advancedResult.getCalinskiHarabaszIndex(),
                "noise_points", advancedResult.getNoisePoints()
            ))
            .errorMessage(advancedResult.isSuccess() ? null : advancedResult.getMessage())
            .build();
    }
}