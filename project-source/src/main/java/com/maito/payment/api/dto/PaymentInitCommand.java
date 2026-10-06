package com.maito.payment.api.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record PaymentInitCommand(
    @NotNull UUID orderId,
    BigDecimal amount,
    String currency,
    String gatewayProvider
) {}
