package com.maito.payment.api.dto;

import java.time.Instant;
import java.util.UUID;

public record TenantPaymentConfigDto(
        UUID id,
        PaymentProvider provider,
        boolean isEnabled,
        boolean isTestMode,
        String keyId,
        String maskedSecretKey,
        String maskedWebhookSecret,
        String merchantAccountId,
        Instant updatedAt
) {}
