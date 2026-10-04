package com.maito.tenant.provisioning;

import com.maito.shared.api.ApiResponse;
import com.maito.tenant.api.dto.ProvisionTenantRequest;
import com.maito.tenant.api.dto.TenantDetailsResponse;
import com.maito.tenant.api.dto.TenantProvisioningResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform Control Plane REST Controller.
 * Internal administrative endpoints for automated tenant provisioning and health inspection.
 */
@RestController
@RequestMapping("/api/v1/internal/platform/tenants")
@Tag(name = "Platform Control Plane", description = "Multi-tenant SaaS lifecycle, database provisioning, and telemetry")
@Slf4j
public class PlatformTenantController {

    private final TenantProvisioningService provisioningService;

    public PlatformTenantController(TenantProvisioningService provisioningService) {
        this.provisioningService = provisioningService;
    }

    @PostMapping
    @Operation(
        summary = "Provision New Isolated Tenant",
        description = "Automates physical PostgreSQL database creation, Liquibase migrations, dedicated HikariCP pool registration, and domain binding."
    )
    public ResponseEntity<ApiResponse<TenantProvisioningResult>> provisionTenant(
            @Valid @RequestBody ProvisionTenantRequest request) {

        log.info("Received provisioning request for tenant: [{}]", request.tenantId());
        TenantProvisioningResult result = provisioningService.provisionTenant(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(result));
    }

    @GetMapping("/{tenantId}")
    @Operation(
        summary = "Get Tenant Control Plane Metadata",
        description = "Retrieves tenant operational health, routing profile, domain configuration, and regional settings."
    )
    public ResponseEntity<ApiResponse<TenantDetailsResponse>> getTenantDetails(
            @PathVariable String tenantId) {

        TenantDetailsResponse details = provisioningService.getTenantDetails(tenantId);
        return ResponseEntity.ok(ApiResponse.ok(details));
    }
}