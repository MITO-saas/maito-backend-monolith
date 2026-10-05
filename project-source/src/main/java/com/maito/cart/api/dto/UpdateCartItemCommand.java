package com.maito.cart.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateCartItemCommand(
    @NotNull @Min(1) Integer quantity
) {}
