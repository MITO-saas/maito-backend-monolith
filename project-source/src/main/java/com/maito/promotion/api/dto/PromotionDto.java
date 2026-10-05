package com.maito.promotion.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PromotionDto(
    UUID id,
    String code,
    String description,
    String discountType,
    BigDecimal discountValue,
    BigDecimal minimumOrderAmount,
    BigDecimal maxDiscountCap,
    Integer usageLimitTotal,
    Integer usageLimitPerCustomer,
    Instant validFrom,
    Instant validTo,
    Boolean isActive
) {}
