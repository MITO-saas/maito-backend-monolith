package com.maito.wallet.api.service;

import com.maito.wallet.api.dto.WalletDto;
import com.maito.wallet.api.dto.WalletTransactionDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.UUID;

public interface WalletService {
    WalletDto getOrCreateWallet(UUID customerProfileId);
    WalletDto credit(UUID customerProfileId, BigDecimal amount, String category, String refId, String desc);
    WalletDto debit(UUID customerProfileId, BigDecimal amount, String category, String refId, String desc);
    Page<WalletTransactionDto> getTransactions(UUID customerProfileId, Pageable pageable);
}
