package com.maito.catalog.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProductSummaryDto(
    UUID id,
    String slug,
    String name,
    String title,
    String brand,
    String shortDescription,
    String description,
    UUID categoryId,
    String categorySlug,
    String categoryName,
    PriceTierDto priceRange,
    BigDecimal minPrice,
    BigDecimal maxPrice,
    String primaryImageUrl,
    List<String> dietaryTags,
    Boolean isPublished,
    List<StorefrontVariantDto> variants
) {
    // 11-argument constructor for backwards compatibility with tests and legacy callers
    public ProductSummaryDto(
        UUID id,
        String slug,
        String name,
        String brand,
        String shortDescription,
        UUID categoryId,
        String categorySlug,
        PriceTierDto priceRange,
        String primaryImageUrl,
        List<String> dietaryTags,
        Boolean isPublished
    ) {
        this(
            id,
            slug,
            name,
            name,
            brand,
            shortDescription,
            shortDescription,
            categoryId,
            categorySlug,
            categorySlug,
            priceRange,
            priceRange != null ? priceRange.salePrice() : BigDecimal.ZERO,
            priceRange != null ? priceRange.mrp() : BigDecimal.ZERO,
            primaryImageUrl,
            dietaryTags,
            isPublished,
            List.of()
        );
    }
}
