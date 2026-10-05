package com.maito.cart.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AddCartItemCommand(
    @NotNull UUID variantId,
    @NotNull @Min(1) Integer quantity
) {}
