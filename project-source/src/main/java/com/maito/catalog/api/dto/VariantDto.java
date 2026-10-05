package com.maito.catalog.api.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record VariantDto(
    UUID id,
    UUID productId,
    String sku,
    String barcode,
    Integer weightGrams,
    Map<String, Object> variantAttributes,
    Map<String, PriceTierDto> pricingTiers,
    PriceTierDto activePrice,
    List<String> mediaGallery,
    Boolean isActive,
    Integer availableStock,
    String stockStatus
) {}
