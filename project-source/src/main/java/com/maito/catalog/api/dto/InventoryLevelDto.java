package com.maito.catalog.api.dto;

import java.util.UUID;

public record InventoryLevelDto(
    UUID id,
    UUID variantId,
    String warehouseCode,
    Integer availableStock,
    Integer reservedStock,
    Integer reorderThreshold,
    String stockStatus
) {}
