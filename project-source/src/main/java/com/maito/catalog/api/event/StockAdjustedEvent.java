package com.maito.catalog.api.event;

import java.util.UUID;

public record StockAdjustedEvent(
        UUID variantId,
        Integer availableStock,
        Boolean inStock,
        String tenantId
) {
}
