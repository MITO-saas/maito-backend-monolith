package com.maito.promotion.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

public record CreatePromotionCommand(
    @NotBlank String code,
    @NotBlank String description,
    @NotBlank String discountType,
    @NotNull BigDecimal discountValue,
    @NotNull BigDecimal minimumOrderAmount,
    BigDecimal maxDiscountCap,
    Integer usageLimitTotal,
    Integer usageLimitPerCustomer,
    @NotNull Instant validFrom,
    @NotNull Instant validTo,
    Boolean isActive
) {}
