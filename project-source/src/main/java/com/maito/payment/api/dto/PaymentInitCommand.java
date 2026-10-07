package com.maito.payment.api.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record PaymentInitCommand(
        @NotNull UUID orderId,
        @NotNull BigDecimal amount,
        String currency,
        String gatewayProvider
) {
    public PaymentInitCommand(UUID orderId, BigDecimal amount, String currency) {
        this(orderId, amount, currency, "RAZORPAY");
    }
}
