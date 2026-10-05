package com.maito.catalog.api.dto;

import java.util.List;
import java.util.UUID;

public record ProductSummaryDto(
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
) {}
