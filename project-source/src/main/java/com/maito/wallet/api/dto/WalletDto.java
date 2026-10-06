package com.maito.wallet.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WalletDto(
    UUID id,
    UUID customerProfileId,
    BigDecimal balance,
    String currencyCode,
    boolean isActive,
    Instant updatedAt
) {}
