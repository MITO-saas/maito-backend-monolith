package com.maito.analytics.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record DashboardKpiResponse(
        BigDecimal grossMerchandiseValue,
        long totalPaidOrders,
        BigDecimal averageOrderValue,
        List<TopSellingVariantDto> topSellingVariants,
        List<StockRiskItemDto> stockRiskItems
) {}
