package com.newsagent.api.service;

import com.newsagent.api.entity.News;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiversityService {
    
    private final ContentNormalizer contentNormalizer;
    private final EmbeddingService embeddingService;
    
    /**
     * Apply Maximal Marginal Relevance (MMR) to reduce duplicate topics
     * MMR = λ * Relevance(news, query) - (1-λ) * max(Similarity(news, selected))
     */
    public List<News> applyMMR(List<News> newsList, int targetSize, double lambda) {
        if (newsList.size() <= targetSize) {
            return newsList;
        }
        
        List<News> selected = new ArrayList<>();
        List<News> remaining = new ArrayList<>(newsList);
        
        // Select first item (highest ranked)
        if (!remaining.isEmpty()) {
            selected.add(remaining.remove(0));
        }
        
        // Continue until we have enough items or no more candidates
        while (selected.size() < targetSize && !remaining.isEmpty()) {
            News bestCandidate = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            int bestIndex = -1;
            
            for (int i = 0; i < remaining.size(); i++) {
                News candidate = remaining.get(i);
                double mmrScore = calculateMMRScore(candidate, selected, lambda);
                
                if (mmrScore > bestScore) {
                    bestScore = mmrScore;
                    bestCandidate = candidate;
                    bestIndex = i;
                }
            }
            
            if (bestCandidate != null) {
                selected.add(bestCandidate);
                remaining.remove(bestIndex);
            } else {
                break;
            }
        }
        
        return selected;
    }
    
    private double calculateMMRScore(News candidate, List<News> selected, double lambda) {
        // Relevance score (normalized rank score)
        double relevance = candidate.getNewsScore() != null ? 
            candidate.getNewsScore().getRankScore() : 0.0;
        
        // Maximum similarity to already selected items
        double maxSimilarity = 0.0;
        for (News selectedNews : selected) {
            double similarity = calculateSimilarity(candidate, selectedNews);
            maxSimilarity = Math.max(maxSimilarity, similarity);
        }
        
        // MMR formula
        return lambda * relevance - (1 - lambda) * maxSimilarity;
    }
    
    /**
     * Calculate similarity between two news items using embeddings and topic information
     */
    public double calculateSimilarity(News news1, News news2) {
        // Try embedding-based similarity first (most accurate)
        Optional<Double> embeddingSimilarity = calculateEmbeddingSimilarity(news1, news2);
        if (embeddingSimilarity.isPresent()) {
            return embeddingSimilarity.get();
        }
        
        // Fallback to topic-based similarity
        double topicSimilarity = calculateTopicSimilarity(news1, news2);
        if (topicSimilarity > 0) {
            return topicSimilarity;
        }
        
        // Final fallback to text-based similarity
        double titleSimilarity = calculateTextSimilarity(news1.getTitle(), news2.getTitle());
        double contentSimilarity = calculateTextSimilarity(news1.getBody(), news2.getBody());
        
        // Weight title more heavily
        return 0.7 * titleSimilarity + 0.3 * contentSimilarity;
    }
    
    /**
     * Calculate embedding-based similarity
     */
    private Optional<Double> calculateEmbeddingSimilarity(News news1, News news2) {
        try {
            return embeddingService.calculateSimilarity(news1.getId(), news2.getId());
        } catch (Exception e) {
            log.debug("Failed to calculate embedding similarity for news {} and {}: {}", 
                news1.getId(), news2.getId(), e.getMessage());
            return Optional.empty();
        }
    }
    
    /**
     * Calculate topic-based similarity
     */
    private double calculateTopicSimilarity(News news1, News news2) {
        // TODO: Implement topic-based similarity after NewsTopic entity is properly set up
        return 0.0;
    }
    
    private double calculateTextSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null) {
            return 0.0;
        }
        
        // Clean and normalize text
        String cleanText1 = contentNormalizer.cleanHtml(text1);
        String cleanText2 = contentNormalizer.cleanHtml(text2);
        
        // Tokenize
        Set<String> tokens1 = tokenize(cleanText1);
        Set<String> tokens2 = tokenize(cleanText2);
        
        if (tokens1.isEmpty() && tokens2.isEmpty()) {
            return 1.0;
        }
        
        if (tokens1.isEmpty() || tokens2.isEmpty()) {
            return 0.0;
        }
        
        // Jaccard similarity
        Set<String> intersection = new HashSet<>(tokens1);
        intersection.retainAll(tokens2);
        
        Set<String> union = new HashSet<>(tokens1);
        union.addAll(tokens2);
        
        return (double) intersection.size() / union.size();
    }
    
    private Set<String> tokenize(String text) {
        if (text == null) {
            return Collections.emptySet();
        }
        
        return Arrays.stream(text.toLowerCase().split("\\s+"))
            .filter(token -> token.length() > 2) // Filter out very short words
            .filter(token -> !isStopWord(token))
            .collect(Collectors.toSet());
    }
    
    private boolean isStopWord(String word) {
        // Korean and English stop words
        Set<String> stopWords = Set.of(
            "의", "가", "이", "은", "는", "을", "를", "에", "와", "과", "로", "으로",
            "에서", "부터", "까지", "한", "그", "저", "이런", "그런", "저런",
            "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for",
            "of", "with", "by", "is", "are", "was", "were", "be", "been", "have", "has"
        );
        return stopWords.contains(word.toLowerCase());
    }
    
    /**
     * Simple topic clustering to identify similar news groups
     */
    public Map<Integer, List<News>> clusterByTopic(List<News> newsList, double similarityThreshold) {
        Map<Integer, List<News>> clusters = new HashMap<>();
        int clusterId = 0;
        
        for (News news : newsList) {
            boolean addedToCluster = false;
            
            // Try to find an existing cluster
            for (Map.Entry<Integer, List<News>> entry : clusters.entrySet()) {
                List<News> cluster = entry.getValue();
                
                // Check similarity with representative (first item) of cluster
                if (!cluster.isEmpty()) {
                    double similarity = calculateSimilarity(news, cluster.get(0));
                    if (similarity >= similarityThreshold) {
                        cluster.add(news);
                        addedToCluster = true;
                        break;
                    }
                }
            }
            
            // Create new cluster if not added
            if (!addedToCluster) {
                List<News> newCluster = new ArrayList<>();
                newCluster.add(news);
                clusters.put(clusterId++, newCluster);
            }
        }
        
        return clusters;
    }
    
    /**
     * Apply topic-aware diversity filtering
     * Limits the number of articles per topic to ensure diversity
     */
    public List<News> applyTopicDiversityFilter(List<News> newsList, int maxPerTopic) {
        Map<String, List<News>> topicGroups = new HashMap<>();
        List<News> newsWithoutTopic = new ArrayList<>();
        
        // Group news by topic (temporarily disabled until NewsTopic entity is set up)
        for (News news : newsList) {
            // TODO: Implement topic grouping after NewsTopic entity is properly set up
            newsWithoutTopic.add(news);
        }
        
        List<News> result = new ArrayList<>();
        
        // Add limited number from each topic
        for (List<News> topicNews : topicGroups.values()) {
            // Sort by rank score within topic
            topicNews.sort((a, b) -> {
                double scoreA = a.getNewsScore() != null ? a.getNewsScore().getRankScore() : 0.0;
                double scoreB = b.getNewsScore() != null ? b.getNewsScore().getRankScore() : 0.0;
                return Double.compare(scoreB, scoreA); // Descending
            });
            
            // Take top articles from this topic
            int count = Math.min(maxPerTopic, topicNews.size());
            result.addAll(topicNews.subList(0, count));
        }
        
        // Add news without topic information
        result.addAll(newsWithoutTopic);
        
        // Sort final result by rank score
        result.sort((a, b) -> {
            double scoreA = a.getNewsScore() != null ? a.getNewsScore().getRankScore() : 0.0;
            double scoreB = b.getNewsScore() != null ? b.getNewsScore().getRankScore() : 0.0;
            return Double.compare(scoreB, scoreA); // Descending
        });
        
        return result;
    }
    
    /**
     * Apply combined MMR and topic diversity filtering
     */
    public List<News> applyAdvancedDiversityFilter(List<News> newsList, int targetSize, 
                                                   double mmrLambda, int maxPerTopic) {
        
        // First apply topic diversity filter
        List<News> topicFiltered = applyTopicDiversityFilter(newsList, maxPerTopic);
        
        // Then apply MMR for final selection
        return applyMMR(topicFiltered, targetSize, mmrLambda);
    }
    
    /**
     * Calculate diversity score for a list of news articles
     */
    public double calculateDiversityScore(List<News> newsList) {
        if (newsList.size() <= 1) {
            return 1.0;
        }
        
        double totalSimilarity = 0.0;
        int comparisons = 0;
        
        for (int i = 0; i < newsList.size(); i++) {
            for (int j = i + 1; j < newsList.size(); j++) {
                totalSimilarity += calculateSimilarity(newsList.get(i), newsList.get(j));
                comparisons++;
            }
        }
        
        double averageSimilarity = comparisons > 0 ? totalSimilarity / comparisons : 0.0;
        
        // Diversity is inverse of similarity (1.0 = completely diverse, 0.0 = all identical)
        return 1.0 - averageSimilarity;
    }
}