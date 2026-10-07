package com.maito.payment.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpsertPaymentConfigCommand(
        @NotNull PaymentProvider provider,
        Boolean isEnabled,
        Boolean isTestMode,
        @NotBlank String keyId,
        @NotBlank String secretKey,
        @NotBlank String webhookSecret,
        String merchantAccountId
) {}
