package com.maito.catalog.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

public record CreateVariantCommand(
    @NotBlank String sku,
    String barcode,
    Integer weightGrams,
    Map<String, Object> variantAttributes,
    Map<String, PriceTierDto> pricingTiers,
    List<String> mediaGallery,
    Boolean isActive,
    Integer initialStock,
    String warehouseCode
) {}
