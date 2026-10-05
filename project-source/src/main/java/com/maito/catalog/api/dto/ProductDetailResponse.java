package com.maito.catalog.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ProductDetailResponse(
    UUID id,
    String slug,
    String name,
    String brand,
    String shortDescription,
    String description,
    UUID categoryId,
    String categorySlug,
    String hsnCode,
    BigDecimal taxRatePercent,
    Map<String, Object> attributes,
    Boolean isPublished,
    String currency,
    List<VariantDto> variants
) {}
