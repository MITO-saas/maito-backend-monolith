package com.maito.search.internal.controller;

import com.maito.search.api.service.ProductSearchService;
import com.maito.search.internal.resolver.TenantIndexResolver;
import com.maito.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/search")
@RequiredArgsConstructor
@Tag(name = "Admin Search Management", description = "Administrative index management and catalog re-indexing")
public class AdminSearchController {

    private final ProductSearchService searchService;
    private final TenantIndexResolver tenantIndexResolver;

    @PostMapping("/reindex")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'ADMIN')")
    @Operation(summary = "Trigger full catalog re-indexing for the active tenant")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reindex() {
        String tenantSlug = tenantIndexResolver.resolveActiveTenantSlug();
        int count = searchService.reindexTenantCatalog(tenantSlug);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "tenant", tenantSlug,
                "status", "COMPLETED",
                "indexedCount", count,
                "message", "Successfully reindexed " + count + " products for tenant: " + tenantSlug
        )));
    }
}
