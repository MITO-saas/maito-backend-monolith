package com.maito.wallet.internal.service;

import com.maito.shared.exception.BusinessException;
import com.maito.shared.exception.ErrorCode;
import com.maito.wallet.api.dto.WalletDto;
import com.maito.wallet.api.dto.WalletTransactionDto;
import com.maito.wallet.api.service.WalletService;
import com.maito.wallet.internal.domain.Wallet;
import com.maito.wallet.internal.domain.WalletTransaction;
import com.maito.wallet.internal.repository.WalletRepository;
import com.maito.wallet.internal.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;

    @Override
    @Transactional
    public WalletDto getOrCreateWallet(UUID customerProfileId) {
        if (customerProfileId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Customer profile ID cannot be null");
        }
        Wallet wallet = getOrCreateEntity(customerProfileId);
        return toDto(wallet);
    }

    @Override
    @Transactional
    public WalletDto credit(UUID customerProfileId, BigDecimal amount, String category, String refId, String desc) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Credit amount must be greater than zero");
        }
        BigDecimal creditAmount = amount.setScale(2, RoundingMode.HALF_UP);
        Wallet wallet = walletRepository.findByCustomerProfileIdWithLock(customerProfileId)
                .orElseGet(() -> getOrCreateEntity(customerProfileId));

        BigDecimal balanceAfter = wallet.getBalance().add(creditAmount);
        wallet.setBalance(balanceAfter);
        walletRepository.save(wallet);

        WalletTransaction tx = WalletTransaction.builder()
                .walletId(wallet.getId())
                .transactionType("CREDIT")
                .category(category != null ? category : "MANUAL_ADJUSTMENT")
                .amount(creditAmount)
                .balanceAfter(balanceAfter)
                .referenceId(refId)
                .description(desc != null ? desc : "Wallet credit")
                .build();
        transactionRepository.save(tx);

        log.info("Credited {} to wallet of customer {} (New balance: {})", creditAmount, customerProfileId, balanceAfter);
        return toDto(wallet);
    }

    @Override
    @Transactional
    public WalletDto debit(UUID customerProfileId, BigDecimal amount, String category, String refId, String desc) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Debit amount must be greater than zero");
        }
        BigDecimal debitAmount = amount.setScale(2, RoundingMode.HALF_UP);
        Wallet wallet = walletRepository.findByCustomerProfileIdWithLock(customerProfileId)
                .orElseGet(() -> getOrCreateEntity(customerProfileId));

        if (wallet.getBalance().compareTo(debitAmount) < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_WALLET_BALANCE,
                    "Insufficient wallet balance: available " + wallet.getBalance() + ", requested " + debitAmount);
        }

        BigDecimal balanceAfter = wallet.getBalance().subtract(debitAmount);
        wallet.setBalance(balanceAfter);
        walletRepository.save(wallet);

        WalletTransaction tx = WalletTransaction.builder()
                .walletId(wallet.getId())
                .transactionType("DEBIT")
                .category(category != null ? category : "CHECKOUT_REDEMPTION")
                .amount(debitAmount)
                .balanceAfter(balanceAfter)
                .referenceId(refId)
                .description(desc != null ? desc : "Wallet debit")
                .build();
        transactionRepository.save(tx);

        log.info("Debited {} from wallet of customer {} (New balance: {})", debitAmount, customerProfileId, balanceAfter);
        return toDto(wallet);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<WalletTransactionDto> getTransactions(UUID customerProfileId, Pageable pageable) {
        Wallet wallet = getOrCreateEntity(customerProfileId);
        return transactionRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getId(), pageable)
                .map(this::toTxDto);
    }

    private Wallet getOrCreateEntity(UUID customerProfileId) {
        return walletRepository.findByCustomerProfileId(customerProfileId)
                .orElseGet(() -> {
                    Wallet newWallet = Wallet.builder()
                            .customerProfileId(customerProfileId)
                            .balance(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                            .currencyCode("INR")
                            .isActive(true)
                            .build();
                    return walletRepository.save(newWallet);
                });
    }

    private WalletDto toDto(Wallet wallet) {
        return new WalletDto(
                wallet.getId(),
                wallet.getCustomerProfileId(),
                wallet.getBalance(),
                wallet.getCurrencyCode(),
                wallet.isActive(),
                wallet.getUpdatedAt()
        );
    }

    private WalletTransactionDto toTxDto(WalletTransaction tx) {
        return new WalletTransactionDto(
                tx.getId(),
                tx.getWalletId(),
                tx.getTransactionType(),
                tx.getCategory(),
                tx.getAmount(),
                tx.getBalanceAfter(),
                tx.getReferenceId(),
                tx.getDescription(),
                tx.getCreatedAt()
        );
    }
}
