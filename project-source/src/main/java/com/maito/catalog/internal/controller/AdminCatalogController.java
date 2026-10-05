package com.maito.catalog.internal.controller;

import com.maito.catalog.api.dto.CreateProductCommand;
import com.maito.catalog.api.dto.ProductDetailResponse;
import com.maito.catalog.api.dto.UpdateProductCommand;
import com.maito.catalog.api.service.CatalogService;
import com.maito.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/catalog")
@RequiredArgsConstructor
@Tag(name = "Admin Catalog", description = "Tenant Admin product and catalog management")
public class AdminCatalogController {

    private final CatalogService catalogService;

    @PostMapping("/products")
    @Operation(summary = "Create product with variants and pricing tiers (Admin only)")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> createProduct(@Valid @RequestBody CreateProductCommand command) {
        ProductDetailResponse response = catalogService.createProduct(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @PutMapping("/products/{id}")
    @Operation(summary = "Update product details and evict Redis cache (Admin only)")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> updateProduct(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProductCommand command) {
        ProductDetailResponse response = catalogService.updateProduct(id, command);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
