package com.maito.promotion.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ApplyCouponCommand(
    @NotBlank String code,
    @NotNull BigDecimal cartSubtotal
) {}
