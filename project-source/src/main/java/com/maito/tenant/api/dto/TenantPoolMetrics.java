package com.maito.tenant.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Safe runtime telemetry snapshot for a tenant connection pool.
 * Database credentials, JDBC URLs, and passwords are strictly omitted.
 */
@Schema(description = "Safe runtime telemetry snapshot for an isolated tenant connection pool")
public record TenantPoolMetrics(
    @Schema(description = "Tenant identifier", example = "mito_crunch")
    String tenantId,

    @Schema(description = "Number of currently active leased connections", example = "3")
    int activeConnections,

    @Schema(description = "Number of idle ready connections in the pool", example = "2")
    int idleConnections,

    @Schema(description = "Total connections managed by the pool", example = "5")
    int totalConnections,

    @Schema(description = "Threads currently blocked waiting for a connection lease", example = "0")
    int threadsAwaitingConnection,

    @Schema(description = "Operational status of the pool", example = "ACTIVE")
    String status
) {}