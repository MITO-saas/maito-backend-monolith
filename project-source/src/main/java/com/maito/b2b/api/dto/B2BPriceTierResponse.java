package com.maito.b2b.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record B2BPriceTierResponse(
        UUID id,
        UUID variantId,
        Integer minQuantity,
        BigDecimal wholesaleUnitPrice,
        String currencyCode,
        Boolean isActive,
        Instant createdAt
) {}
