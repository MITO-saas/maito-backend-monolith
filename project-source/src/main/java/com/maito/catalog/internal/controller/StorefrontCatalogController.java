package com.maito.catalog.internal.controller;

import com.maito.catalog.api.dto.CategoryDto;
import com.maito.catalog.api.dto.ProductDetailResponse;
import com.maito.catalog.api.dto.ProductSummaryDto;
import com.maito.catalog.api.service.CatalogService;
import com.maito.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/catalog")
@RequiredArgsConstructor
@Tag(name = "Storefront Catalog", description = "Public catalog browsing, categories, products and live inventory")
public class StorefrontCatalogController {

    private final CatalogService catalogService;

    @GetMapping("/categories")
    @Operation(summary = "Get hierarchical category tree")
    public ResponseEntity<ApiResponse<List<CategoryDto>>> getCategories() {
        List<CategoryDto> tree = catalogService.getCategoryTree();
        return ResponseEntity.ok(ApiResponse.ok(tree));
    }

    @GetMapping("/products")
    @Operation(summary = "Paginated product search with category, price range, and dietary filters")
    public ResponseEntity<ApiResponse<List<ProductSummaryDto>>> searchProducts(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String categorySlug,
            @RequestParam(required = false, defaultValue = "INR") String currency,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String dietary,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size
    ) {
        List<ProductSummaryDto> list = catalogService.searchProducts(
                categoryId, categorySlug, currency, minPrice, maxPrice, dietary, page, size
        );
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @GetMapping("/products/{slug}")
    @Operation(summary = "Get product details with variant matrix and live stock indicators")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> getProductBySlug(
            @PathVariable String slug,
            @RequestParam(required = false, defaultValue = "INR") String currency
    ) {
        ProductDetailResponse response = catalogService.getProductBySlug(slug, currency);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
