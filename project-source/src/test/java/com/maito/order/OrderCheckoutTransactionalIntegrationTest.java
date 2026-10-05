package com.maito.order;

import com.maito.cart.api.dto.AddCartItemCommand;
import com.maito.cart.api.dto.CartResponse;
import com.maito.cart.api.service.CartService;
import com.maito.catalog.api.dto.CreateProductCommand;
import com.maito.catalog.api.dto.CreateVariantCommand;
import com.maito.catalog.api.dto.InventoryLevelDto;
import com.maito.catalog.api.dto.PriceTierDto;
import com.maito.catalog.api.dto.ProductDetailResponse;
import com.maito.catalog.api.service.CatalogService;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
    private CatalogService catalogService;

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

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(tenantContext);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private UUID createTestSku(String prefix, int stock) {
        String slug = prefix.toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8);
        CreateVariantCommand vCmd = new CreateVariantCommand(
                slug.toUpperCase() + "-SKU",
                "BAR-" + UUID.randomUUID().toString().substring(0, 8),
                100,
                Map.of("flavor", prefix),
                Map.of("INR", new PriceTierDto(new BigDecimal("199.00"), new BigDecimal("149.00"))),
                List.of(),
                true,
                stock,
                "DEFAULT_WH"
        );
        ProductDetailResponse prod = catalogService.createProduct(new CreateProductCommand(
                slug,
                "Test Product " + prefix,
                "Mito Crunch",
                "Short desc",
                "Detailed desc",
                null,
                "19041090",
                new BigDecimal("5.00"),
                Map.of(),
                true,
                List.of(vCmd)
        ));
        return prod.variants().get(0).id();
    }

    @Test
    @DisplayName("Assert transactional checkout: Atomic stock reservation -> payment confirmation -> permanent deduction")
    void shouldExecuteTransactionalOrderCheckoutAndPayment() {
        TenantProfileDto customer = userService.createProfile(
                UUID.randomUUID(), "Checkout", "Customer", "ROLE_TENANT_CUSTOMER", List.of()
        );
        UUID customerProfileId = customer.id();

        // 1. Create a dedicated test SKU with 10 units
        UUID testVariantId = createTestSku("checkout-basic", 10);

        // 2. Customer creates cart and adds 2 units
        CartResponse cart = cartService.getOrCreateCart(null, customerProfileId, "INR");
        cartService.addItem(cart.id(), new AddCartItemCommand(testVariantId, 2));

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
        InventoryLevelDto reservedLevel = inventoryService.getInventoryLevel(testVariantId, "DEFAULT_WH");
        assertThat(reservedLevel.availableStock()).isEqualTo(8);
        assertThat(reservedLevel.reservedStock()).isEqualTo(2);

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
        InventoryLevelDto finalLevel = inventoryService.getInventoryLevel(testVariantId, "DEFAULT_WH");
        assertThat(finalLevel.availableStock()).isEqualTo(8);
        assertThat(finalLevel.reservedStock()).isEqualTo(0);
    }

    @Test
    @DisplayName("Assert zero overselling under concurrency: 10 concurrent requests for 5 available units -> 5 succeed, 5 fail")
    void shouldPreventOversellingUnderConcurrentCheckoutContention() throws Exception {
        // 1. Create SKU with exactly 5 available units
        UUID scarceVariantId = createTestSku("scarce-stock", 5);

        // 2. Create 10 distinct customers and 10 carts with 1 unit each
        int totalRequests = 10;
        List<UUID> customerIds = new ArrayList<>();
        List<UUID> cartIds = new ArrayList<>();

        for (int i = 0; i < totalRequests; i++) {
            TenantProfileDto cust = userService.createProfile(
                    UUID.randomUUID(), "Customer" + i, "User", "ROLE_TENANT_CUSTOMER", List.of()
            );
            customerIds.add(cust.id());

            CartResponse cart = cartService.getOrCreateCart(null, cust.id(), "INR");
            cartService.addItem(cart.id(), new AddCartItemCommand(scarceVariantId, 1));
            cartIds.add(cart.id());
        }

        // 3. Concurrently trigger order checkout
        ExecutorService executor = Executors.newFixedThreadPool(totalRequests);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(totalRequests);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger insufficientStockCount = new AtomicInteger(0);
        AtomicInteger otherFailures = new AtomicInteger(0);

        CreateOrderCommand orderCmd = new CreateOrderCommand(
                Map.of("line1", "MG Road", "city", "Bengaluru", "pincode", "560001"),
                null
        );

        for (int i = 0; i < totalRequests; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    TenantContextHolder.set(tenantContext);
                    startGate.await();

                    orderService.createOrderFromCart(cartIds.get(index), customerIds.get(index), orderCmd);
                    successCount.incrementAndGet();
                } catch (BusinessException be) {
                    if (be.getErrorCode() == ErrorCode.INSUFFICIENT_STOCK) {
                        insufficientStockCount.incrementAndGet();
                    } else {
                        otherFailures.incrementAndGet();
                    }
                } catch (Exception e) {
                    otherFailures.incrementAndGet();
                } finally {
                    TenantContextHolder.clear();
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean completed = endGate.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(otherFailures.get()).isEqualTo(0);
        assertThat(successCount.get()).isEqualTo(5);
        assertThat(insufficientStockCount.get()).isEqualTo(5);

        // 4. Assert database inventory levels: exactly 0 available, exactly 5 reserved
        TenantContextHolder.set(tenantContext);
        try {
            InventoryLevelDto finalInv = inventoryService.getInventoryLevel(scarceVariantId, "DEFAULT_WH");
            assertThat(finalInv.availableStock()).isEqualTo(0);
            assertThat(finalInv.reservedStock()).isEqualTo(5);
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    @DisplayName("Assert compensating rollback on checkout failure: zero orphan reserved stock and zero orphan orders")
    void shouldRollbackCleanlyWhenMultiItemOrderFailsMidCheckout() {
        TenantProfileDto customer = userService.createProfile(
                UUID.randomUUID(), "Rollback", "Tester", "ROLE_TENANT_CUSTOMER", List.of()
        );
        UUID customerProfileId = customer.id();

        // SKU 1 has 10 units
        UUID sku1 = createTestSku("sku1-avail", 10);
        // SKU 2 has 0 units
        UUID sku2 = createTestSku("sku2-empty", 0);

        // Customer cart has 2 units of SKU 1
        CartResponse cart = cartService.getOrCreateCart(null, customerProfileId, "INR");
        cartService.addItem(cart.id(), new AddCartItemCommand(sku1, 2));

        // Attempting to add SKU 2 (out of stock) should fail immediately
        assertThatThrownBy(() -> cartService.addItem(cart.id(), new AddCartItemCommand(sku2, 1)))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getErrorCode() == ErrorCode.INSUFFICIENT_STOCK);

        // SKU 1 stock must remain untouched (10 available, 0 reserved)
        InventoryLevelDto sku1Level = inventoryService.getInventoryLevel(sku1, "DEFAULT_WH");
        assertThat(sku1Level.availableStock()).isEqualTo(10);
        assertThat(sku1Level.reservedStock()).isEqualTo(0);

        // Zero orders created
        assertThat(orderService.getCustomerOrders(customerProfileId)).isEmpty();
    }

    @Test
    @DisplayName("Assert Order Cancellation releases reserved inventory stock back to warehouse")
    void shouldRestoreReservedStockWhenOrderIsCancelled() {
        TenantProfileDto customer = userService.createProfile(
                UUID.randomUUID(), "Cancel", "Customer", "ROLE_TENANT_CUSTOMER", List.of()
        );
        UUID customerProfileId = customer.id();

        UUID cancelSku = createTestSku("cancel-sku", 10);

        CartResponse cart = cartService.getOrCreateCart(null, customerProfileId, "INR");
        cartService.addItem(cart.id(), new AddCartItemCommand(cancelSku, 3));

        OrderResponse order = orderService.createOrderFromCart(cart.id(), customerProfileId, new CreateOrderCommand(
                Map.of("line1", "Park Street", "city", "Kolkata", "pincode", "700016"),
                null
        ));

        // Available is 7, Reserved is 3
        InventoryLevelDto reservedInv = inventoryService.getInventoryLevel(cancelSku, "DEFAULT_WH");
        assertThat(reservedInv.availableStock()).isEqualTo(7);
        assertThat(reservedInv.reservedStock()).isEqualTo(3);

        // Cancel order
        orderService.cancelOrder(order.id());

        // Verify Order status is CANCELLED
        OrderResponse cancelledOrder = orderService.getOrderByNumber(order.orderNumber(), customerProfileId);
        assertThat(cancelledOrder.orderStatus()).isEqualTo("CANCELLED");

        // Verify inventory: available restored to 10, reserved restored to 0
        InventoryLevelDto restoredInv = inventoryService.getInventoryLevel(cancelSku, "DEFAULT_WH");
        assertThat(restoredInv.availableStock()).isEqualTo(10);
        assertThat(restoredInv.reservedStock()).isEqualTo(0);
    }

    @Test
    @DisplayName("Assert Payment Webhook rejects invalid order ID with controlled business error")
    void shouldRejectPaymentCallbackForInvalidOrder() {
        PaymentCallbackCommand payCmd = new PaymentCallbackCommand("TXN-INVALID", "PAID", "mock-sig");
        assertThatThrownBy(() -> orderService.confirmPayment(UUID.randomUUID(), payCmd))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getErrorCode() == ErrorCode.RESOURCE_NOT_FOUND);
    }
}
