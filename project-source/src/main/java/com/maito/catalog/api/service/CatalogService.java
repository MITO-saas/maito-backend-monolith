package com.maito.catalog.api.service;

import com.maito.catalog.api.dto.CategoryDto;
import com.maito.catalog.api.dto.CreateProductCommand;
import com.maito.catalog.api.dto.ProductDetailResponse;
import com.maito.catalog.api.dto.ProductSummaryDto;
import com.maito.catalog.api.dto.UpdateProductCommand;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface CatalogService {
    List<CategoryDto> getCategoryTree();
    List<ProductSummaryDto> searchProducts(UUID categoryId, String categorySlug, String currency, BigDecimal minPrice, BigDecimal maxPrice, String dietary, int page, int size);
    ProductDetailResponse getProductBySlug(String slug, String currencyCode);
    ProductDetailResponse createProduct(CreateProductCommand command);
    ProductDetailResponse updateProduct(UUID productId, UpdateProductCommand command);
    void evictProductCache(String tenantId, String slug);
}
