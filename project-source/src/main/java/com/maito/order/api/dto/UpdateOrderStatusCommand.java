package com.maito.order.api.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateOrderStatusCommand(
    @NotBlank String status
) {}
