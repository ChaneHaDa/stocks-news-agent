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
     * Calculate similarity between two news items based on title and content
     */
    public double calculateSimilarity(News news1, News news2) {
        // Simple similarity based on title overlap and content overlap
        double titleSimilarity = calculateTextSimilarity(news1.getTitle(), news2.getTitle());
        double contentSimilarity = calculateTextSimilarity(news1.getBody(), news2.getBody());
        
        // Weight title more heavily
        return 0.7 * titleSimilarity + 0.3 * contentSimilarity;
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
}