package com.newsagent.api.controller;

import com.newsagent.api.service.AdvancedClusteringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * F3: Advanced Clustering Controller
 * Provides endpoints for HDBSCAN, K-means, and clustering analysis
 */
@RestController
@RequestMapping("/admin/clustering")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Advanced Clustering", description = "F3 고급 클러스터링 관리 API")
public class ClusteringController {
    
    private final AdvancedClusteringService advancedClusteringService;
    
    @PostMapping("/hdbscan")
    @Operation(
        summary = "HDBSCAN 클러스터링 수행", 
        description = "밀도 기반 계층적 클러스터링으로 뉴스 토픽을 자동 그룹핑합니다."
    )
    @ApiResponse(responseCode = "200", description = "HDBSCAN 클러스터링 성공")
    public ResponseEntity<AdvancedClusteringService.AdvancedClusteringResult> performHDBSCAN() {
        try {
            log.info("Starting manual HDBSCAN clustering");
            
            AdvancedClusteringService.AdvancedClusteringResult result = 
                advancedClusteringService.performHDBSCANClustering();
            
            if (result.isSuccess()) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.badRequest().body(result);
            }
            
        } catch (Exception e) {
            log.error("HDBSCAN clustering failed", e);
            return ResponseEntity.internalServerError().body(
                AdvancedClusteringService.AdvancedClusteringResult.builder()
                    .algorithm("HDBSCAN")
                    .success(false)
                    .message("Clustering failed: " + e.getMessage())
                    .build()
            );
        }
    }
    
    @PostMapping("/kmeans")
    @Operation(
        summary = "K-means 클러스터링 수행", 
        description = "K-means mini-batch 알고리즘으로 지정된 수의 클러스터를 생성합니다."
    )
    @ApiResponse(responseCode = "200", description = "K-means 클러스터링 성공")
    public ResponseEntity<AdvancedClusteringService.AdvancedClusteringResult> performKMeans(
            @Parameter(description = "생성할 클러스터 수", example = "5")
            @RequestParam(defaultValue = "5") int numClusters
    ) {
        try {
            log.info("Starting manual K-means clustering with {} clusters", numClusters);
            
            AdvancedClusteringService.AdvancedClusteringResult result = 
                advancedClusteringService.performKMeansMiniBatchClustering(numClusters);
            
            if (result.isSuccess()) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.badRequest().body(result);
            }
            
        } catch (Exception e) {
            log.error("K-means clustering failed", e);
            return ResponseEntity.internalServerError().body(
                AdvancedClusteringService.AdvancedClusteringResult.builder()
                    .algorithm("K-means-MiniBatch")
                    .success(false)
                    .message("Clustering failed: " + e.getMessage())
                    .build()
            );
        }
    }
    
    @PostMapping("/optimize")
    @Operation(
        summary = "최적 클러스터 수 찾기", 
        description = "Silhouette 분석과 Elbow method를 사용해 최적의 클러스터 수를 자동으로 결정합니다."
    )
    @ApiResponse(responseCode = "200", description = "최적화 분석 완료")
    public ResponseEntity<AdvancedClusteringService.OptimalClusterResult> findOptimalClusters() {
        try {
            log.info("Starting optimal cluster analysis");
            
            AdvancedClusteringService.OptimalClusterResult result = 
                advancedClusteringService.findOptimalClusters();
            
            if (result.isSuccess()) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.badRequest().body(result);
            }
            
        } catch (Exception e) {
            log.error("Optimal cluster analysis failed", e);
            return ResponseEntity.internalServerError().body(
                AdvancedClusteringService.OptimalClusterResult.builder()
                    .success(false)
                    .message("Analysis failed: " + e.getMessage())
                    .build()
            );
        }
    }
    
    @GetMapping("/status")
    @Operation(
        summary = "클러스터링 서비스 상태", 
        description = "고급 클러스터링 서비스의 현재 상태와 가용성을 확인합니다."
    )
    @ApiResponse(responseCode = "200", description = "서비스 상태 조회 성공")
    public ResponseEntity<ClusteringStatus> getClusteringStatus() {
        try {
            // Get recent clustering results from database
            // This could be implemented to show recent clustering statistics
            
            ClusteringStatus status = ClusteringStatus.builder()
                .available(true)
                .algorithms(new String[]{"HDBSCAN", "K-means-MiniBatch"})
                .qualityMetrics(new String[]{"Silhouette Score", "Davies-Bouldin Index", "Calinski-Harabasz Index"})
                .message("Advanced clustering service is operational")
                .build();
            
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            log.error("Failed to get clustering status", e);
            return ResponseEntity.internalServerError().body(
                ClusteringStatus.builder()
                    .available(false)
                    .message("Service status check failed: " + e.getMessage())
                    .build()
            );
        }
    }
    
    @lombok.Builder
    @lombok.Data
    public static class ClusteringStatus {
        private boolean available;
        private String[] algorithms;
        private String[] qualityMetrics;
        private String message;
    }
}