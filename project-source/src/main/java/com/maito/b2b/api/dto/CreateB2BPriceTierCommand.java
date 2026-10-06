package com.maito.b2b.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateB2BPriceTierCommand(
        @NotNull(message = "Variant ID is required")
        UUID variantId,

        @NotNull(message = "Minimum quantity is required")
        @Min(value = 1, message = "Minimum quantity must be greater than 0")
        Integer minQuantity,

        @NotNull(message = "Wholesale unit price is required")
        @DecimalMin(value = "0.01", message = "Wholesale unit price must be positive")
        BigDecimal wholesaleUnitPrice,

        String currencyCode,

        Boolean isActive
) {}
