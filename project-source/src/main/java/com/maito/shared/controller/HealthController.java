package com.maito.shared.controller;

import com.maito.shared.api.ApiResponse;
import com.maito.shared.api.dto.HealthResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Foundational Health and Help diagnostic controller.
 * Serves as the primary operational check across dev, test, uat, and production environments.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Health & Help", description = "System diagnostic, liveness verification, and help endpoints")
public class HealthController {

    @Value("${spring.application.name:maito-backend-monolith}")
    private String applicationName;

    @Value("${spring.profiles.active:local}")
    private String activeProfile;

    @GetMapping({"/health", "/help"})
    @Operation(
        summary = "System Health and Help Diagnostic",
        description = "Returns system operational status, active environment profile, service version, and documentation links."
    )
    public ResponseEntity<ApiResponse<HealthResponse>> getHealth() {
        HealthResponse response = new HealthResponse(
            "UP",
            applicationName,
            "v1.0.0",
            Instant.now(),
            activeProfile,
            "Maito Backend Monolith API is running. Access interactive Swagger UI at /swagger-ui.html or OpenAPI spec at /v3/api-docs."
        );
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}