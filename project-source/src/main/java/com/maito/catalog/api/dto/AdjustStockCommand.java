package com.maito.catalog.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AdjustStockCommand(
    @NotNull UUID variantId,
    String warehouseCode,
    @NotNull Integer quantityDelta,
    String reason
) {}
