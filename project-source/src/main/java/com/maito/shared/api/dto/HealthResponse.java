package com.maito.shared.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Health and Help diagnostic response DTO.
 */
@Schema(description = "System Health and Environment Diagnostic Details")
public record HealthResponse(
    @Schema(description = "Service health status", example = "UP")
    String status,

    @Schema(description = "Service name identifier", example = "maito-backend-monolith")
    String service,

    @Schema(description = "Platform version", example = "v1.0.0")
    String version,

    @Schema(description = "Diagnostic check timestamp")
    Instant timestamp,

    @Schema(description = "Active environment profile", example = "local")
    String environment,

    @Schema(description = "Help message and API guidance", example = "Maito Backend Monolith API is running. Access Swagger at /swagger-ui.html.")
    String message
) {}