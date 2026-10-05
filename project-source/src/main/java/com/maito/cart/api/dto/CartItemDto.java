package com.maito.cart.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CartItemDto(
    UUID id,
    UUID variantId,
    String sku,
    String productName,
    Integer weightGrams,
    BigDecimal unitPrice,
    Integer quantity,
    BigDecimal totalLineAmount,
    String primaryImageUrl
) {}
