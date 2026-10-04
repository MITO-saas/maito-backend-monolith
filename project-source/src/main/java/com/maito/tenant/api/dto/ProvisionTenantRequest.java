package com.maito.tenant.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Command payload for programmatic zero-deploy tenant database and control-plane provisioning.
 */
@Schema(description = "Automated Tenant Provisioning Request")
public record ProvisionTenantRequest(
    @NotBlank(message = "tenantId must not be blank")
    @Size(min = 3, max = 64, message = "tenantId length must be between 3 and 64")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "tenantId may only contain alphanumeric characters, underscores, and dashes")
    @Schema(description = "Unique immutable tenant identifier", example = "mito_crunch")
    String tenantId,

    @NotBlank(message = "tenantSlug must not be blank")
    @Size(min = 3, max = 64, message = "tenantSlug length must be between 3 and 64")
    @Pattern(regexp = "^[a-z0-9_]+$", message = "tenantSlug must be lowercase alphanumeric with underscores only")
    @Schema(description = "URL and database slug", example = "mitocrunch")
    String tenantSlug,

    @NotBlank(message = "legalName must not be blank")
    @Size(max = 255)
    @Schema(description = "Registered legal business entity name", example = "Mito Crunch Superfoods Pvt Ltd")
    String legalName,

    @NotBlank(message = "primaryDomain must not be blank")
    @Schema(description = "Primary vanity or custom domain", example = "store.mitocrunch.com")
    String primaryDomain,

    @Schema(description = "ISO country code", example = "IN")
    String countryCode,

    @Schema(description = "ISO currency code", example = "INR")
    String currencyCode,

    @Schema(description = "Optional extensible configuration overrides")
    Map<String, Object> initialConfig
) {}