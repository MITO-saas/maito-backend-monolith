package com.maito.promotion.api.dto;

import java.math.BigDecimal;

public record DiscountCalculationResult(
    String code,
    String discountType,
    BigDecimal discountAmount,
    BigDecimal freeShippingSavings,
    boolean applied,
    String message
) {}
