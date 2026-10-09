package com.maito.catalog.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record StorefrontVariantDto(
    UUID id,
    String sku,
    String title,
    BigDecimal price,
    BigDecimal compareAtPrice,
    Integer availableStock,
    BigDecimal taxRate,
    String hsnCode
) {}
