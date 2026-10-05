package com.maito.analytics.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailySalesPointDto(
        LocalDate date,
        long orderCount,
        BigDecimal salesAmount
) {}
