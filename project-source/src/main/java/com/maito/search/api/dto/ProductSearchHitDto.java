package com.maito.search.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProductSearchHitDto(
        UUID id,
        String slug,
        String name,
        String brand,
        String shortDescription,
        String categorySlug,
        String categoryName,
        BigDecimal price,
        BigDecimal mrp,
        Integer discountPercent,
        Integer availableStock,
        Boolean inStock,
        String primaryImage,
        List<String> tags,
        Double ratingAverage,
        Integer reviewCount
) {
}
