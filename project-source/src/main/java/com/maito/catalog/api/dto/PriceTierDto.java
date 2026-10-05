package com.maito.catalog.api.dto;

import java.math.BigDecimal;

public record PriceTierDto(
    BigDecimal mrp,
    BigDecimal salePrice
) {}
