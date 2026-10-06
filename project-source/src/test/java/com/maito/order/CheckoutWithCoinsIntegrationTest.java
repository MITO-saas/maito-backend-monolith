package com.maito.order;

import com.maito.cart.api.dto.AddCartItemCommand;
import com.maito.cart.api.dto.CartResponse;
import com.maito.cart.api.service.CartService;
import com.maito.catalog.api.dto.InventoryLevelDto;
import com.maito.catalog.api.service.InventoryService;
import com.maito.order.api.dto.CreateOrderCommand;
import com.maito.order.api.dto.OrderResponse;
import com.maito.order.api.service.OrderService;
import com.maito.shared.exception.BusinessException;
import com.maito.shared.exception.ErrorCode;
import com.maito.tenant.routing.TenantContext;
import com.maito.tenant.routing.TenantContextHolder;
import com.maito.user.api.dto.TenantProfileDto;
import com.maito.user.api.service.UserService;
import com.maito.wallet.api.dto.WalletDto;
import com.maito.wallet.api.service.WalletService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("local")
class CheckoutWithCoinsIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private CartService cartService;

    @Autowired
    private UserService userService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private InventoryService inventoryService;

    private final TenantContext tenantContextMito = new TenantContext(
            "mito_crunch",
            "mitocrunch",
            "IN",
            "INR",
            "en_IN",
            "db_mitocrunch"
    );

    private UUID customerProfileId;
    private final UUID periPeriVariantId = UUID.fromString("f1000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(tenantContextMito);
        TenantProfileDto profile = userService.createProfile(
                UUID.randomUUID(),
                "CoinCustomer_" + System.currentTimeMillis(),
                "LoyaltyUser",
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
    @DisplayName("Assert checkout with loyalty coins debits customer wallet and deducts order total")
    void testCheckoutWithCoinsDeduction() {
        // 1. Credit wallet with 200 coins
        walletService.credit(customerProfileId, new BigDecimal("200.00"), "INITIAL_BONUS", "BONUS-01", "Signup loyalty bonus");
        WalletDto preWallet = walletService.getOrCreateWallet(customerProfileId);
        assertThat(preWallet.balance()).isEqualByComparingTo(new BigDecimal("200.00"));

        // 2. Add 4x Peri Peri (149 * 4 = 596.00)
        CartResponse cart = cartService.getOrCreateCart(null, customerProfileId, "INR");
        cartService.addItem(cart.id(), new AddCartItemCommand(periPeriVariantId, 4));

        // 3. Checkout redeeming 100 coins
        Map<String, Object> address = Map.of(
                "line1", "MG Road, Flat 402",
                "city", "Patna",
                "state", "Bihar",
                "pincode", "800001"
        );
        CreateOrderCommand cmd = new CreateOrderCommand(address, null, new BigDecimal("100.00"));
        OrderResponse order = orderService.createOrderFromCart(cart.id(), customerProfileId, cmd);

        assertThat(order).isNotNull();
        assertThat(order.coinsRedeemed()).isEqualByComparingTo(new BigDecimal("100.00"));

        // Total calculation: subtotal 596.00 + 5% GST (29.80) + 0 shipping (subtotal >= 499) = 625.80
        // Coins deduction: 100.00 => totalAmount = 525.80
        assertThat(order.totalAmount()).isEqualByComparingTo(new BigDecimal("525.80"));

        // 4. Verify wallet balance was debited by exactly 100 coins
        WalletDto postWallet = walletService.getOrCreateWallet(customerProfileId);
        assertThat(postWallet.balance()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("Assert checkout fails when customer attempts to redeem more coins than wallet balance")
    void testCheckoutRejectsExcessiveCoinRedemption() {
        // Credit 50 coins
        walletService.credit(customerProfileId, new BigDecimal("50.00"), "MANUAL_ADJUSTMENT", "REF-01", "Promo balance");

        CartResponse cart = cartService.getOrCreateCart(null, customerProfileId, "INR");
        cartService.addItem(cart.id(), new AddCartItemCommand(periPeriVariantId, 2));

        Map<String, Object> address = Map.of(
                "line1", "Station Road",
                "city", "Darbhanga",
                "state", "Bihar",
                "pincode", "846004"
        );

        // Attempt to redeem 100 coins with only 50 available
        CreateOrderCommand cmd = new CreateOrderCommand(address, null, new BigDecimal("100.00"));
        BusinessException ex = assertThrows(BusinessException.class, () ->
                orderService.createOrderFromCart(cart.id(), customerProfileId, cmd));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_WALLET_BALANCE);

        // Wallet balance remains untouched at 50.00
        WalletDto wallet = walletService.getOrCreateWallet(customerProfileId);
        assertThat(wallet.balance()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    @DisplayName("Assert compensating rollback restores redeemed coins when checkout fails mid-flight")
    void testCompensatingRollbackWhenStockReservationFails() {
        // 1. Credit 100 coins
        walletService.credit(customerProfileId, new BigDecimal("100.00"), "MANUAL_ADJUSTMENT", "BONUS-99", "Pre-checkout balance");
        WalletDto initialWallet = walletService.getOrCreateWallet(customerProfileId);
        assertThat(initialWallet.balance()).isEqualByComparingTo(new BigDecimal("100.00"));

        // 2. Add 2 units to cart (valid stock exists)
        CartResponse cart = cartService.getOrCreateCart(null, customerProfileId, "INR");
        cartService.addItem(cart.id(), new AddCartItemCommand(periPeriVariantId, 2));

        // 3. Exhaust available stock right before checkout to provoke mid-flight reservation failure
        InventoryLevelDto inv = inventoryService.getInventoryLevel(periPeriVariantId, "DEFAULT_WH");
        int drainQty = inv.availableStock();
        inventoryService.reserveStock(periPeriVariantId, "DEFAULT_WH", drainQty);

        Map<String, Object> address = Map.of(
                "line1", "Boring Road",
                "city", "Patna",
                "state", "Bihar",
                "pincode", "800001"
        );

        // 4. Attempt checkout redeeming 60 coins (stock reservation will fail mid-flight)
        CreateOrderCommand cmd = new CreateOrderCommand(address, null, new BigDecimal("60.00"));
        try {
            assertThrows(RuntimeException.class, () ->
                    orderService.createOrderFromCart(cart.id(), customerProfileId, cmd));

            // 5. Verify compensating rollback restored the 60 coins back to wallet (balance remains 100.00)
            WalletDto restoredWallet = walletService.getOrCreateWallet(customerProfileId);
            assertThat(restoredWallet.balance()).isEqualByComparingTo(new BigDecimal("100.00"));
        } finally {
            // Clean up drained stock
            inventoryService.releaseStock(periPeriVariantId, "DEFAULT_WH", drainQty);
        }
    }
}