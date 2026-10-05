package com.maito.analytics.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record TopSellingVariantDto(
        UUID variantId,
        String sku,
        String productName,
        long unitsSold,
        BigDecimal totalRevenue
) {}
