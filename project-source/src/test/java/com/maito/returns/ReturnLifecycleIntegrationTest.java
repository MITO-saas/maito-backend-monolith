package com.maito.returns;

import com.maito.catalog.api.dto.CreateProductCommand;
import com.maito.catalog.api.dto.CreateVariantCommand;
import com.maito.catalog.api.dto.InventoryLevelDto;
import com.maito.catalog.api.dto.PriceTierDto;
import com.maito.catalog.api.dto.ProductDetailResponse;
import com.maito.catalog.api.service.CatalogService;
import com.maito.catalog.api.service.InventoryService;
import com.maito.order.internal.domain.Order;
import com.maito.order.internal.domain.OrderItem;
import com.maito.order.internal.repository.OrderItemRepository;
import com.maito.order.internal.repository.OrderRepository;
import com.maito.returns.api.dto.*;
import com.maito.returns.api.service.ReturnService;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("local")
class ReturnLifecycleIntegrationTest {

    @Autowired
    private ReturnService returnService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private CatalogService catalogService;

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
    private final Map<String, Object> testShippingAddress = Map.of(
            "fullName", "Mito Crunch Customer",
            "addressLine1", "123 Farm fresh road",
            "city", "Bengaluru",
            "state", "Karnataka",
            "postalCode", "560001"
    );

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(tenantContextMito);
        TenantProfileDto profile = userService.createProfile(
                UUID.randomUUID(),
                "ReturnTest_" + System.currentTimeMillis(),
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
    @DisplayName("Assert non-delivered order cannot be returned (throws ORDER_NOT_DELIVERED)")
    void testNonDeliveredOrderCannotBeReturned() {
        UUID variantId = createTestSku("pending-ret", 5);

        Order pendingOrder = orderRepository.save(Order.builder()
                .orderNumber("ORD-PEND-" + System.currentTimeMillis())
                .customerProfileId(customerProfileId)
                .orderStatus("PENDING_PAYMENT")
                .currencyCode("INR")
                .subtotalAmount(new BigDecimal("299.00"))
                .totalAmount(new BigDecimal("299.00"))
                .shippingAddressSnapshot(testShippingAddress)
                .build());

        OrderItem item = orderItemRepository.save(OrderItem.builder()
                .orderId(pendingOrder.getId())
                .variantId(variantId)
                .productNameSnapshot("Test Crunch")
                .skuSnapshot("TEST-SKU-1")
                .unitPrice(new BigDecimal("299.00"))
                .quantity(1)
                .totalLineAmount(new BigDecimal("299.00"))
                .build());

        CreateReturnCommand cmd = CreateReturnCommand.builder()
                .orderId(pendingOrder.getId())
                .reasonCategory("DEFECTIVE_PRODUCT")
                .customerNotes("Defective item")
                .items(List.of(ReturnItemRequestDto.builder()
                        .orderItemId(item.getId())
                        .variantId(item.getVariantId())
                        .quantity(1)
                        .build()))
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () ->
                returnService.createReturnRequest(customerProfileId, cmd));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ORDER_NOT_DELIVERED);
    }

    @Test
    @DisplayName("Assert return lifecycle: request -> approve -> QC pass restocks inventory and credits wallet")
    void testSuccessfulReturnApprovalAndQcPassRestocksInventoryAndCreditsWallet() {
        int initialStock = 5;
        UUID variantId = createTestSku("qc-pass-sku", initialStock);
        BigDecimal unitPrice = new BigDecimal("150.00");
        int returnQty = 2;
        BigDecimal expectedRefund = unitPrice.multiply(BigDecimal.valueOf(returnQty));

        Order deliveredOrder = orderRepository.save(Order.builder()
                .orderNumber("ORD-DELV-" + System.currentTimeMillis())
                .customerProfileId(customerProfileId)
                .orderStatus("DELIVERED")
                .currencyCode("INR")
                .subtotalAmount(new BigDecimal("300.00"))
                .totalAmount(new BigDecimal("300.00"))
                .shippingAddressSnapshot(testShippingAddress)
                .build());

        OrderItem orderItem = orderItemRepository.save(OrderItem.builder()
                .orderId(deliveredOrder.getId())
                .variantId(variantId)
                .productNameSnapshot("Organic Makhana Peri Peri")
                .skuSnapshot("MAK-PERI-100G")
                .unitPrice(unitPrice)
                .quantity(2)
                .totalLineAmount(new BigDecimal("300.00"))
                .build());

        // 1. Create Return Request
        CreateReturnCommand cmd = CreateReturnCommand.builder()
                .orderId(deliveredOrder.getId())
                .reasonCategory("TRANSIT_DAMAGE")
                .customerNotes("Package was torn upon delivery")
                .proofMediaUrls(List.of("https://cdn.example.com/proof1.jpg"))
                .items(List.of(ReturnItemRequestDto.builder()
                        .orderItemId(orderItem.getId())
                        .variantId(variantId)
                        .quantity(returnQty)
                        .build()))
                .build();

        ReturnResponse returnResponse = returnService.createReturnRequest(customerProfileId, cmd);
        assertThat(returnResponse).isNotNull();
        assertThat(returnResponse.returnNumber()).startsWith("RET-");
        assertThat(returnResponse.status()).isEqualTo("REQUESTED");
        assertThat(returnResponse.refundAmount()).isEqualByComparingTo(expectedRefund);
        assertThat(returnResponse.items()).hasSize(1);

        // 2. Approve Return
        ReturnResponse approved = returnService.approveReturn(returnResponse.id());
        assertThat(approved.status()).isEqualTo("APPROVED");

        // Record stock and wallet balance prior to QC
        InventoryLevelDto currentInv = inventoryService.getInventoryLevel(variantId, "DEFAULT_WH");
        int stockBeforeQc = currentInv.availableStock();
        WalletDto initialWallet = walletService.getOrCreateWallet(customerProfileId);
        BigDecimal initialBalance = initialWallet.balance();

        // 3. Submit Passing QC
        SubmitQcCommand qcCmd = SubmitQcCommand.builder()
                .passed(true)
                .qcNotes("Verified item returned in sealed pouch. Restock approved.")
                .refundMode("WALLET")
                .build();

        ReturnResponse settled = returnService.submitQcEvaluation(returnResponse.id(), qcCmd);
        assertThat(settled.status()).isEqualTo("SETTLED");
        assertThat(settled.settledAt()).isNotNull();

        // 4. Assert Atomic Restock
        InventoryLevelDto updatedLevel = inventoryService.getInventoryLevel(variantId, "DEFAULT_WH");
        assertThat(updatedLevel.availableStock()).isEqualTo(stockBeforeQc + returnQty);

        // 5. Assert Automated Refund Credit to Customer Wallet
        WalletDto updatedWallet = walletService.getOrCreateWallet(customerProfileId);
        assertThat(updatedWallet.balance()).isEqualByComparingTo(initialBalance.add(expectedRefund));

        // 6. Assert Audit Transaction Record
        Page<WalletTransactionDto> txs = walletService.getTransactions(customerProfileId, PageRequest.of(0, 10));
        assertThat(txs.getContent()).anyMatch(tx ->
                "REFUND".equalsIgnoreCase(tx.category()) &&
                tx.amount().compareTo(expectedRefund) == 0 &&
                settled.returnNumber().equals(tx.referenceId()));
    }

    @Test
    @DisplayName("Assert failing QC rejects the claim with no inventory restock or wallet credit")
    void testFailedQcRejectsClaimWithNoRestockOrWalletCredit() {
        int initialStock = 10;
        UUID variantId = createTestSku("qc-fail-sku", initialStock);
        BigDecimal unitPrice = new BigDecimal("200.00");
        int returnQty = 1;

        Order deliveredOrder = orderRepository.save(Order.builder()
                .orderNumber("ORD-QCFAIL-" + System.currentTimeMillis())
                .customerProfileId(customerProfileId)
                .orderStatus("DELIVERED")
                .currencyCode("INR")
                .subtotalAmount(new BigDecimal("200.00"))
                .totalAmount(new BigDecimal("200.00"))
                .shippingAddressSnapshot(testShippingAddress)
                .build());

        OrderItem orderItem = orderItemRepository.save(OrderItem.builder()
                .orderId(deliveredOrder.getId())
                .variantId(variantId)
                .productNameSnapshot("Makhana Mint Magic")
                .skuSnapshot("MAK-MINT-100G")
                .unitPrice(unitPrice)
                .quantity(1)
                .totalLineAmount(new BigDecimal("200.00"))
                .build());

        CreateReturnCommand cmd = CreateReturnCommand.builder()
                .orderId(deliveredOrder.getId())
                .reasonCategory("DISSATISFIED")
                .customerNotes("Expired product claim")
                .items(List.of(ReturnItemRequestDto.builder()
                        .orderItemId(orderItem.getId())
                        .variantId(variantId)
                        .quantity(returnQty)
                        .build()))
                .build();

        ReturnResponse returnResponse = returnService.createReturnRequest(customerProfileId, cmd);
        returnService.approveReturn(returnResponse.id());

        InventoryLevelDto initialLevel = inventoryService.getInventoryLevel(variantId, "DEFAULT_WH");
        int stockBeforeQc = initialLevel.availableStock();
        WalletDto initialWallet = walletService.getOrCreateWallet(customerProfileId);
        BigDecimal initialBalance = initialWallet.balance();

        // Submit Failing QC
        SubmitQcCommand qcCmd = SubmitQcCommand.builder()
                .passed(false)
                .qcNotes("Product was consumed and tampered. Return claim rejected.")
                .build();

        ReturnResponse rejected = returnService.submitQcEvaluation(returnResponse.id(), qcCmd);
        assertThat(rejected.status()).isEqualTo("REJECTED");
        assertThat(rejected.settledAt()).isNull();

        // Inventory should NOT change
        InventoryLevelDto finalLevel = inventoryService.getInventoryLevel(variantId, "DEFAULT_WH");
        assertThat(finalLevel.availableStock()).isEqualTo(stockBeforeQc);

        // Wallet balance should NOT change
        WalletDto finalWallet = walletService.getOrCreateWallet(customerProfileId);
        assertThat(finalWallet.balance()).isEqualByComparingTo(initialBalance);
    }

    @Test
    @DisplayName("Assert approving an already settled or rejected return is rejected with INVALID_RETURN_STATUS")
    void testApprovingNonRequestedReturnRejected() {
        UUID variantId = createTestSku("invalid-state-sku", 10);
        Order deliveredOrder = orderRepository.save(Order.builder()
                .orderNumber("ORD-INVSTATE-" + System.currentTimeMillis())
                .customerProfileId(customerProfileId)
                .orderStatus("DELIVERED")
                .currencyCode("INR")
                .subtotalAmount(new BigDecimal("150.00"))
                .totalAmount(new BigDecimal("150.00"))
                .shippingAddressSnapshot(testShippingAddress)
                .build());

        OrderItem orderItem = orderItemRepository.save(OrderItem.builder()
                .orderId(deliveredOrder.getId())
                .variantId(variantId)
                .productNameSnapshot("Organic Makhana")
                .skuSnapshot("MAK-ORG-100G")
                .unitPrice(new BigDecimal("150.00"))
                .quantity(1)
                .totalLineAmount(new BigDecimal("150.00"))
                .build());

        CreateReturnCommand cmd = CreateReturnCommand.builder()
                .orderId(deliveredOrder.getId())
                .reasonCategory("DEFECTIVE_PRODUCT")
                .customerNotes("Test invalid state")
                .items(List.of(ReturnItemRequestDto.builder()
                        .orderItemId(orderItem.getId())
                        .variantId(variantId)
                        .quantity(1)
                        .build()))
                .build();

        ReturnResponse ret = returnService.createReturnRequest(customerProfileId, cmd);
        returnService.approveReturn(ret.id());

        // Attempting to approve again when status is already APPROVED
        BusinessException ex = assertThrows(BusinessException.class, () ->
                returnService.approveReturn(ret.id()));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_RETURN_STATUS);
    }
}