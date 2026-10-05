package com.maito.analytics.api.dto;

import java.util.UUID;

public record StockRiskItemDto(
        UUID variantId,
        String warehouseCode,
        int availableStock,
        int reorderThreshold,
        boolean isStockDepleted
) {}
