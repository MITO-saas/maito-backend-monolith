package com.maito.wallet;

import com.maito.shared.exception.BusinessException;
import com.maito.shared.exception.ErrorCode;
import com.maito.tenant.routing.TenantContext;
import com.maito.tenant.routing.TenantContextHolder;
import com.maito.user.api.dto.TenantProfileDto;
import com.maito.user.api.service.UserService;
import com.maito.wallet.api.dto.WalletDto;
import com.maito.wallet.api.dto.WalletTransactionDto;
import com.maito.wallet.api.service.WalletService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("local")
class WalletDoubleEntryIntegrationTest {

    @Autowired
    private WalletService walletService;

    @Autowired
    private UserService userService;

    private final TenantContext tenantContextMito = new TenantContext(
            "mito_crunch",
            "mitocrunch",
            "IN",
            "INR",
            "en_IN",
            "db_mitocrunch"
    );

    private UUID customerProfileId;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(tenantContextMito);
        TenantProfileDto profile = userService.createProfile(
                UUID.randomUUID(),
                "WalletUser_" + System.currentTimeMillis(),
                "Tester",
                "ROLE_TENANT_CUSTOMER",
                List.of()
        );
        customerProfileId = profile.id();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Assert wallet initialization with zero balance and atomic credit calculation")
    void testWalletInitializationAndCredit() {
        WalletDto initial = walletService.getOrCreateWallet(customerProfileId);
        assertThat(initial).isNotNull();
        assertThat(initial.balance()).isEqualByComparingTo(BigDecimal.ZERO);

        WalletDto credited = walletService.credit(
                customerProfileId,
                new BigDecimal("250.50"),
                "ORDER_CASHBACK",
                "ORD-TEST-001",
                "Cashback for completed purchase"
        );
        assertThat(credited.balance()).isEqualByComparingTo(new BigDecimal("250.50"));

        Page<WalletTransactionDto> txs = walletService.getTransactions(customerProfileId, PageRequest.of(0, 10));
        assertThat(txs.getTotalElements()).isEqualTo(1);
        WalletTransactionDto tx = txs.getContent().get(0);
        assertThat(tx.transactionType()).isEqualTo("CREDIT");
        assertThat(tx.amount()).isEqualByComparingTo(new BigDecimal("250.50"));
        assertThat(tx.balanceAfter()).isEqualByComparingTo(new BigDecimal("250.50"));
        assertThat(tx.category()).isEqualTo("ORDER_CASHBACK");
    }

    @Test
    @DisplayName("Assert atomic debit and rejection of debit when balance is insufficient")
    void testWalletDebitAndInsufficientBalanceRejection() {
        walletService.credit(customerProfileId, new BigDecimal("100.00"), "MANUAL_ADJUSTMENT", "REF-1", "Initial deposit");

        WalletDto debited = walletService.debit(customerProfileId, new BigDecimal("35.00"), "CHECKOUT_REDEMPTION", "ORD-1", "Coins applied");
        assertThat(debited.balance()).isEqualByComparingTo(new BigDecimal("65.00"));

        // Attempt to debit more than current balance
        BusinessException ex = assertThrows(BusinessException.class, () ->
                walletService.debit(customerProfileId, new BigDecimal("70.00"), "CHECKOUT_REDEMPTION", "ORD-2", "Excess debit"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_WALLET_BALANCE);

        // Verify balance was unaffected
        WalletDto finalWallet = walletService.getOrCreateWallet(customerProfileId);
        assertThat(finalWallet.balance()).isEqualByComparingTo(new BigDecimal("65.00"));
    }

    @Test
    @DisplayName("Assert double-entry audit ledger consistency under concurrent transactions")
    void testConcurrentWalletCredits() throws Exception {
        walletService.getOrCreateWallet(customerProfileId);
        int threadCount = 10;
        BigDecimal creditPerThread = new BigDecimal("10.00");

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Future<Void>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            futures.add(executor.submit(() -> {
                try {
                    TenantContextHolder.set(tenantContextMito);
                    walletService.credit(
                            customerProfileId,
                            creditPerThread,
                            "PROMO_CREDIT",
                            "REF-" + idx,
                            "Concurrent credit"
                    );
                    return null;
                } finally {
                    latch.countDown();
                    TenantContextHolder.clear();
                }
            }));
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        for (Future<Void> f : futures) {
            f.get();
        }

        TenantContextHolder.set(tenantContextMito);
        WalletDto finalWallet = walletService.getOrCreateWallet(customerProfileId);
        BigDecimal expectedBalance = creditPerThread.multiply(BigDecimal.valueOf(threadCount));
        assertThat(finalWallet.balance()).isEqualByComparingTo(expectedBalance);

        Page<WalletTransactionDto> txs = walletService.getTransactions(customerProfileId, PageRequest.of(0, 50));
        assertThat(txs.getTotalElements()).isEqualTo(threadCount);
    }
}
