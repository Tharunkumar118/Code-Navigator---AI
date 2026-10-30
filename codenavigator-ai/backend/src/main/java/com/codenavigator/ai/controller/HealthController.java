package com.codenavigator.ai.controller;

import com.codenavigator.ai.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Lightweight controller to confirm the application context started
 * and the web layer is responding. Distinct from /actuator/health,
 * which additionally verifies infrastructure (DB) status.
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, String>>> checkHealth() {
        Map<String, String> status = Map.of(
                "status", "UP",
                "service", "CodeNavigator AI Backend",
                "version", "0.0.1-SNAPSHOT"
        );
        return ResponseEntity.ok(ApiResponse.success("Service is healthy", status));
    }
}
