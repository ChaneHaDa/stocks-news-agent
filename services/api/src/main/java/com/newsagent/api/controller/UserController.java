package com.newsagent.api.controller;

import com.newsagent.api.entity.UserPreference;
import com.newsagent.api.service.PersonalizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User", description = "User preferences and personalization API")
public class UserController {
    
    private final PersonalizationService personalizationService;
    
    @GetMapping("/{userId}/preferences")
    @Operation(summary = "Get user preferences", description = "사용자의 개인화 설정을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved user preferences")
    public ResponseEntity<UserPreference> getUserPreferences(
            @Parameter(description = "User ID")
            @PathVariable String userId) {
        
        log.debug("Getting preferences for user: {}", userId);
        
        UserPreference preferences = personalizationService.getUserPreferences(userId);
        return ResponseEntity.ok(preferences);
    }
    
    @PutMapping("/{userId}/preferences")
    @Operation(summary = "Update user preferences", description = "사용자의 개인화 설정을 업데이트합니다.")
    @ApiResponse(responseCode = "200", description = "Successfully updated user preferences")
    public ResponseEntity<UserPreference> updateUserPreferences(
            @Parameter(description = "User ID")
            @PathVariable String userId,
            
            @RequestBody UserPreferenceRequest request) {
        
        log.debug("Updating preferences for user: {}", userId);
        
        UserPreference updatedPreferences = personalizationService.updateUserPreferences(
            userId,
            request.getInterestedTickers(),
            request.getInterestedKeywords(),
            request.getDiversityWeight(),
            request.getPersonalizationEnabled()
        );
        
        return ResponseEntity.ok(updatedPreferences);
    }
    
    @PutMapping("/{userId}/preferences/tickers")
    @Operation(summary = "Update interested tickers", description = "관심 종목 목록을 업데이트합니다.")
    @ApiResponse(responseCode = "200", description = "Successfully updated interested tickers")
    public ResponseEntity<UserPreference> updateInterestedTickers(
            @Parameter(description = "User ID")
            @PathVariable String userId,
            
            @Parameter(description = "List of interested ticker codes")
            @RequestBody List<String> tickers) {
        
        log.debug("Updating interested tickers for user: {} -> {}", userId, tickers);
        
        UserPreference updatedPreferences = personalizationService.updateUserPreferences(
            userId, tickers, null, null, null);
        
        return ResponseEntity.ok(updatedPreferences);
    }
    
    @PutMapping("/{userId}/preferences/keywords")
    @Operation(summary = "Update interested keywords", description = "관심 키워드 목록을 업데이트합니다.")
    @ApiResponse(responseCode = "200", description = "Successfully updated interested keywords")
    public ResponseEntity<UserPreference> updateInterestedKeywords(
            @Parameter(description = "User ID")
            @PathVariable String userId,
            
            @Parameter(description = "List of interested keywords")
            @RequestBody List<String> keywords) {
        
        log.debug("Updating interested keywords for user: {} -> {}", userId, keywords);
        
        UserPreference updatedPreferences = personalizationService.updateUserPreferences(
            userId, null, keywords, null, null);
        
        return ResponseEntity.ok(updatedPreferences);
    }
    
    @PutMapping("/{userId}/preferences/personalization")
    @Operation(summary = "Enable/disable personalization", description = "개인화 기능을 활성화/비활성화합니다.")
    @ApiResponse(responseCode = "200", description = "Successfully updated personalization setting")
    public ResponseEntity<UserPreference> updatePersonalizationEnabled(
            @Parameter(description = "User ID")
            @PathVariable String userId,
            
            @Parameter(description = "Enable personalization")
            @RequestParam boolean enabled) {
        
        log.debug("Setting personalization enabled={} for user: {}", enabled, userId);
        
        UserPreference updatedPreferences = personalizationService.updateUserPreferences(
            userId, null, null, null, enabled);
        
        return ResponseEntity.ok(updatedPreferences);
    }
    
    @PutMapping("/{userId}/preferences/diversity")
    @Operation(summary = "Update diversity weight", description = "다양성 가중치를 업데이트합니다.")
    @ApiResponse(responseCode = "200", description = "Successfully updated diversity weight")
    public ResponseEntity<UserPreference> updateDiversityWeight(
            @Parameter(description = "User ID")
            @PathVariable String userId,
            
            @Parameter(description = "Diversity weight (0.0 to 1.0)")
            @RequestParam double weight) {
        
        log.debug("Updating diversity weight={} for user: {}", weight, userId);
        
        // Validate range
        if (weight < 0.0 || weight > 1.0) {
            return ResponseEntity.badRequest().build();
        }
        
        UserPreference updatedPreferences = personalizationService.updateUserPreferences(
            userId, null, null, weight, null);
        
        return ResponseEntity.ok(updatedPreferences);
    }
    
    // DTO for request body
    public static class UserPreferenceRequest {
        private List<String> interestedTickers;
        private List<String> interestedKeywords;
        private Double diversityWeight;
        private Boolean personalizationEnabled;
        
        // Getters and setters
        public List<String> getInterestedTickers() { return interestedTickers; }
        public void setInterestedTickers(List<String> interestedTickers) { this.interestedTickers = interestedTickers; }
        
        public List<String> getInterestedKeywords() { return interestedKeywords; }
        public void setInterestedKeywords(List<String> interestedKeywords) { this.interestedKeywords = interestedKeywords; }
        
        public Double getDiversityWeight() { return diversityWeight; }
        public void setDiversityWeight(Double diversityWeight) { this.diversityWeight = diversityWeight; }
        
        public Boolean getPersonalizationEnabled() { return personalizationEnabled; }
        public void setPersonalizationEnabled(Boolean personalizationEnabled) { this.personalizationEnabled = personalizationEnabled; }
    }
}