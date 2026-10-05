package com.maito.order.api.dto;

import jakarta.validation.constraints.NotBlank;

public record PaymentCallbackCommand(
    @NotBlank String paymentReference,
    @NotBlank String status,
    String signature
) {}
