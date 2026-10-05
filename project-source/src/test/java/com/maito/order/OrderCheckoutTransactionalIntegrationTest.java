package com.maito.order;

import com.maito.cart.api.dto.AddCartItemCommand;
import com.maito.cart.api.dto.CartResponse;
import com.maito.cart.api.service.CartService;
import com.maito.catalog.api.dto.InventoryLevelDto;
import com.maito.catalog.api.service.InventoryService;
import com.maito.order.api.dto.CreateOrderCommand;
import com.maito.order.api.dto.OrderResponse;
import com.maito.order.api.dto.PaymentCallbackCommand;
import com.maito.order.api.service.OrderService;
import com.maito.shared.exception.BusinessException;
import com.maito.shared.exception.ErrorCode;
import com.maito.tenant.routing.TenantContext;
import com.maito.tenant.routing.TenantContextHolder;
import com.maito.user.api.dto.TenantProfileDto;
import com.maito.user.api.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("local")
class OrderCheckoutTransactionalIntegrationTest {

    @Autowired
    private CartService cartService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private UserService userService;

    private final TenantContext tenantContext = new TenantContext(
            "mito_crunch",
            "mitocrunch",
            "IN",
            "INR",
            "en_IN",
            "db_mitocrunch"
    );

    private final UUID mintVariantId = UUID.fromString("f1000000-0000-0000-0000-000000000004");

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(tenantContext);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Assert transactional checkout: Atomic stock reservation -> payment confirmation -> permanent deduction")
    void shouldExecuteTransactionalOrderCheckoutAndPayment() {
        TenantProfileDto customer = userService.createProfile(
                UUID.randomUUID(), "Checkout", "Customer", "ROLE_TENANT_CUSTOMER", List.of()
        );
        UUID customerProfileId = customer.id();

        // 1. Check initial inventory level
        InventoryLevelDto initialLevel = inventoryService.getInventoryLevel(mintVariantId, "DEFAULT_WH");
        int initialAvailable = initialLevel.availableStock();
        int initialReserved = initialLevel.reservedStock();

        // 2. Customer creates cart and adds 2 units
        CartResponse cart = cartService.getOrCreateCart(null, customerProfileId, "INR");
        cartService.addItem(cart.id(), new AddCartItemCommand(mintVariantId, 2));

        // 3. Customer places order
        CreateOrderCommand createCmd = new CreateOrderCommand(
                Map.of("line1", "Flat 402, Lotus Towers", "city", "Patna", "state", "Bihar", "pincode", "800001"),
                "CRUNCHFREE"
        );

        OrderResponse order = orderService.createOrderFromCart(cart.id(), customerProfileId, createCmd);

        assertThat(order).isNotNull();
        assertThat(order.orderStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(order.orderNumber()).startsWith("MC-2026-");
        assertThat(order.items()).hasSize(1);
        assertThat(order.items().get(0).quantity()).isEqualTo(2);

        // 4. Verify inventory: availableStock decremented by 2, reservedStock incremented by 2
        InventoryLevelDto reservedLevel = inventoryService.getInventoryLevel(mintVariantId, "DEFAULT_WH");
        assertThat(reservedLevel.availableStock()).isEqualTo(initialAvailable - 2);
        assertThat(reservedLevel.reservedStock()).isEqualTo(initialReserved + 2);

        // 5. Payment gateway webhook confirmation
        PaymentCallbackCommand payCmd = new PaymentCallbackCommand(
                "GATEWAY-TXN-" + UUID.randomUUID().toString().substring(0, 8),
                "PAID",
                "sample-mock-signature"
        );

        OrderResponse paidOrder = orderService.confirmPayment(order.id(), payCmd);
        assertThat(paidOrder.orderStatus()).isEqualTo("PAID");
        assertThat(paidOrder.paymentStatus()).isEqualTo("PAID");

        // 6. Verify inventory: reservedStock decremented by 2 (permanent fulfillment)
        InventoryLevelDto finalLevel = inventoryService.getInventoryLevel(mintVariantId, "DEFAULT_WH");
        assertThat(finalLevel.availableStock()).isEqualTo(initialAvailable - 2);
        assertThat(finalLevel.reservedStock()).isEqualTo(initialReserved);
    }

    @Test
    @DisplayName("Assert checkout fails fast with INSUFFICIENT_STOCK if items are unavailable")
    void shouldFailFastWhenInventoryIsUnavailable() {
        TenantProfileDto customer = userService.createProfile(
                UUID.randomUUID(), "Insufficient", "Customer", "ROLE_TENANT_CUSTOMER", List.of()
        );
        UUID customerProfileId = customer.id();
        CartResponse cart = cartService.getOrCreateCart(null, customerProfileId, "INR");

        // Attempting to add more than available units should fail
        assertThatThrownBy(() -> cartService.addItem(cart.id(), new AddCartItemCommand(mintVariantId, 999999)))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getErrorCode() == ErrorCode.INSUFFICIENT_STOCK);
    }
}
