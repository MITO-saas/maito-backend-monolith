package com.maito.tenant.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Result record confirming isolated physical database provisioning and connection pool creation.
 */
@Schema(description = "Automated Tenant Provisioning Result")
public record TenantProvisioningResult(
    @Schema(description = "Tenant identifier", example = "mito_crunch")
    String tenantId,

    @Schema(description = "Tenant database slug", example = "mitocrunch")
    String tenantSlug,

    @Schema(description = "Registered primary custom domain", example = "store.mitocrunch.com")
    String primaryDomain,

    @Schema(description = "Physical isolated database name", example = "db_mitocrunch")
    String databaseName,

    @Schema(description = "Account state", example = "ACTIVE")
    String status,

    @Schema(description = "Provisioning timestamp")
    Instant provisionedAt,

    @Schema(description = "Human-readable status summary")
    String message
) {}