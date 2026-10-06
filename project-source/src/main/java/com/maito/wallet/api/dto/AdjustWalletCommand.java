package com.maito.wallet.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record AdjustWalletCommand(
    @NotNull UUID customerProfileId,
    @NotBlank String transactionType,
    @NotNull @DecimalMin("0.01") BigDecimal amount,
    @NotBlank String category,
    String referenceId,
    @NotBlank String reason
) {}
