package com.maito.tenant.provisioning;

import com.maito.shared.api.ApiResponse;
import com.maito.tenant.api.dto.ProvisionTenantRequest;
import com.maito.tenant.api.dto.TenantDetailsResponse;
import com.maito.tenant.api.dto.TenantPoolMetrics;
import com.maito.tenant.api.dto.TenantProvisioningResult;
import com.maito.tenant.datasource.HikariPoolManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Platform Control Plane REST Controller.
 * Internal administrative endpoints for automated tenant provisioning, pool telemetry, and decommissioning.
 */
@RestController
@RequestMapping("/api/v1/internal/platform/tenants")
@Tag(name = "Platform Control Plane", description = "Multi-tenant SaaS lifecycle, database provisioning, and telemetry")
@Slf4j
public class PlatformTenantController {

    private final TenantProvisioningService provisioningService;
    private final HikariPoolManager poolManager;

    public PlatformTenantController(
            TenantProvisioningService provisioningService,
            HikariPoolManager poolManager) {
        this.provisioningService = provisioningService;
        this.poolManager = poolManager;
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

    @GetMapping("/telemetry")
    @Operation(
        summary = "Tenant Connection Pool Telemetry",
        description = "Provides real-time pool metrics (active, idle, total connections, blocked threads) across all active tenant pools without credential exposure."
    )
    public ResponseEntity<ApiResponse<List<TenantPoolMetrics>>> getTelemetry() {
        log.debug("Fetching live tenant pool telemetry metrics.");
        List<TenantPoolMetrics> metrics = poolManager.getPoolTelemetry();
        return ResponseEntity.ok(ApiResponse.ok(metrics));
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

    @DeleteMapping("/{tenantId}")
    @Operation(
        summary = "Decommission Tenant & Evict Pool",
        description = "Transitions tenant state to DECOMMISSIONED, closes dedicated HikariCP pool, purges Redis routing cache, and audits action."
    )
    public ResponseEntity<ApiResponse<Map<String, String>>> decommissionTenant(
            @PathVariable String tenantId) {

        log.warn("Received administrative request to decommission tenant: [{}]", tenantId);
        provisioningService.decommissionTenant(tenantId);
        Map<String, String> payload = Map.of(
                "tenantId", tenantId,
                "status", "DECOMMISSIONED",
                "message", "Tenant successfully decommissioned. Pool closed and routing cache evicted."
        );
        return ResponseEntity.ok(ApiResponse.ok(payload));
    }
}