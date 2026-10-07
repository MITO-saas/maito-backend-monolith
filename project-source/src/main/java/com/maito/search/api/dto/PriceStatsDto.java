package com.maito.search.api.dto;

import java.math.BigDecimal;

public record PriceStatsDto(
        BigDecimal min,
        BigDecimal max,
        BigDecimal avg
) {
}
