package com.maito.payment.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentInitResponse(
    UUID transactionId,
    UUID orderId,
    String gatewayProvider,
    String gatewayOrderId,
    BigDecimal amount,
    String currency,
    String keyId
) {}
