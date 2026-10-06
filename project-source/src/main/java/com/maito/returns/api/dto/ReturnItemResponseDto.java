package com.maito.returns.api.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder
public record ReturnItemResponseDto(
    UUID id,
    UUID returnId,
    UUID orderItemId,
    UUID variantId,
    Integer quantity,
    BigDecimal unitPrice,
    Instant createdAt
) {}