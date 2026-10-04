package com.maito.tenant.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

/**
 * Operational telemetry and configuration details for an active tenant.
 */
@Schema(description = "Tenant Control Plane Details and Status")
public record TenantDetailsResponse(
    String tenantId,
    String tenantSlug,
    String legalEntityName,
    String accountState,
    String primaryDomain,
    Map<String, Object> routingConfig,
    Map<String, Object> regionalProfile,
    Map<String, Object> tierEntitlements,
    Instant createdAt,
    Instant updatedAt
) {}