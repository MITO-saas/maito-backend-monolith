package com.maito.wallet.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WalletTransactionDto(
    UUID id,
    UUID walletId,
    String transactionType,
    String category,
    BigDecimal amount,
    BigDecimal balanceAfter,
    String referenceId,
    String description,
    Instant createdAt
) {}
