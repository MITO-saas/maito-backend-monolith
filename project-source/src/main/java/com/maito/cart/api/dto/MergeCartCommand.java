package com.maito.cart.api.dto;

import jakarta.validation.constraints.NotBlank;

public record MergeCartCommand(
    @NotBlank String guestCartId
) {}
