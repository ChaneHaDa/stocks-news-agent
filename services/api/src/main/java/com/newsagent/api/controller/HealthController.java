package com.newsagent.api.controller;

import com.newsagent.api.dto.HealthResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Health", description = "Health Check API")
public class HealthController {

    @GetMapping("/healthz")
    @Operation(summary = "Health check endpoint", description = "Service health status")
    @ApiResponse(responseCode = "200", description = "Service is healthy")
    public HealthResponse health() {
        return new HealthResponse(true);
    }
}