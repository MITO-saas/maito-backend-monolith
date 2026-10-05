package com.maito.order.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemDto(
    UUID id,
    UUID variantId,
    String productNameSnapshot,
    String skuSnapshot,
    BigDecimal unitPrice,
    Integer quantity,
    BigDecimal totalLineAmount
) {}
