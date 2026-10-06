package com.maito.b2b;

import com.maito.b2b.api.dto.*;
import com.maito.b2b.api.service.B2BInvoiceService;
import com.maito.b2b.api.service.B2BOrderService;
import com.maito.b2b.api.service.B2BPartnerService;
import com.maito.b2b.api.service.B2BPriceTierService;
import com.maito.catalog.api.dto.CreateProductCommand;
import com.maito.catalog.api.dto.CreateVariantCommand;
import com.maito.catalog.api.dto.PriceTierDto;
import com.maito.catalog.api.dto.ProductDetailResponse;
import com.maito.catalog.api.service.CatalogService;
import com.maito.catalog.api.service.InventoryService;
import com.maito.order.api.dto.OrderResponse;
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
class B2BWholesalePricingAndCreditIntegrationTest {

    @Autowired
    private B2BPartnerService partnerService;

    @Autowired
    private B2BPriceTierService priceTierService;

    @Autowired
    private B2BOrderService orderService;

    @Autowired
    private B2BInvoiceService invoiceService;

    @Autowired
    private UserService userService;

    @Autowired
    private CatalogService catalogService;

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
    private UUID testVariantId;
    private B2BPartnerResponse verifiedPartner;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(tenantContextMito);
        TenantProfileDto profile = userService.createProfile(
                UUID.randomUUID(),
                "WholesaleTester_" + System.currentTimeMillis(),
                "Manager",
                "ROLE_TENANT_CUSTOMER",
                List.of()
        );
        customerProfileId = profile.id();

        String slug = "horeca-chips-" + UUID.randomUUID().toString().substring(0, 8);
        CreateVariantCommand vCmd = new CreateVariantCommand(
                slug.toUpperCase() + "-SKU",
                "BAR-" + UUID.randomUUID().toString().substring(0, 8),
                200,
                Map.of("flavor", "Cream & Onion"),
                Map.of("INR", new PriceTierDto(new BigDecimal("199.00"), new BigDecimal("150.00"))),
                List.of(),
                true,
                10000,
                "DEFAULT_WH"
        );
        ProductDetailResponse product = catalogService.createProduct(new CreateProductCommand(
                slug,
                "HoReCa Wholesale Makhana " + slug,
                "Mito Crunch",
                "HoReCa short",
                "HoReCa bulk item",
                null,
                "19041090",
                new BigDecimal("5.00"),
                Map.of(),
                true,
                List.of(vCmd)
        ));
        testVariantId = product.variants().getFirst().id();

        // Configure bulk wholesale price tier: >= 50 units @ ₹95.00
        priceTierService.createOrUpdatePriceTier(new CreateB2BPriceTierCommand(
                testVariantId,
                50,
                new BigDecimal("95.00"),
                "INR",
                true
        ));

        // Register and verify B2B Partner with ₹500,000 credit limit
        String randomGstin = "10ABCDE" + (1000 + (int)(Math.random() * 8999)) + "F1Z5";
        B2BPartnerResponse partner = partnerService.registerPartner(customerProfileId, new RegisterPartnerCommand(
                "Taj Hotels Enterprise B2B",
                "Taj Hospitality",
                randomGstin,
                null,
                "FSSAI-999888777666",
                Map.of("city", "Mumbai", "pincode", "400001")
        ));

        verifiedPartner = partnerService.verifyPartner(partner.id(), new VerifyPartnerCommand(
                "VERIFIED",
                new BigDecimal("500000.00"),
                30,
                "Pre-approved HoReCa corporate credit line"
        ));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Ordering >= 50 units applies wholesale tiered price of ₹95.00 instead of standard ₹140.00/₹150.00")
    void testWholesaleTierPricingAppliedForBulkQuantities() {
        CreateB2BBulkOrderCommand orderCmd = new CreateB2BBulkOrderCommand(
                Map.of("city", "Mumbai"),
                Map.of("city", "Mumbai"),
                List.of(new B2BBulkOrderItemCommand(testVariantId, 60)),
                "NET_30",
                "Tier pricing verification"
        );

        OrderResponse order = orderService.createBulkOrder(customerProfileId, orderCmd);

        assertThat(order).isNotNull();
        assertThat(order.items()).hasSize(1);
        // Unit price should be ₹95.00 from price tier
        assertThat(order.items().getFirst().unitPrice()).isEqualByComparingTo(new BigDecimal("95.00"));
        // 60 * 95 = 5700.00
        assertThat(order.subtotalAmount()).isEqualByComparingTo(new BigDecimal("5700.00"));
        // 5% tax = 285.00
        assertThat(order.taxAmount()).isEqualByComparingTo(new BigDecimal("285.00"));
        // Total = 5985.00
        assertThat(order.totalAmount()).isEqualByComparingTo(new BigDecimal("5985.00"));
    }

    @Test
    @DisplayName("Placing Net-30 order deducts from available credit and generates GST tax invoice with due date")
    void testNet30OrderDeductsAvailableCreditAndGeneratesInvoice() {
        CreateB2BBulkOrderCommand orderCmd = new CreateB2BBulkOrderCommand(
                Map.of("city", "Mumbai"),
                Map.of("city", "Mumbai"),
                List.of(new B2BBulkOrderItemCommand(testVariantId, 100)),
                "NET_30",
                "Bulk 100 units order"
        );

        OrderResponse order = orderService.createBulkOrder(customerProfileId, orderCmd);

        // 100 * 95 = 9500 + 475 tax = 9975.00
        BigDecimal orderTotal = order.totalAmount();
        assertThat(orderTotal).isEqualByComparingTo(new BigDecimal("9975.00"));

        // Verify credit deduction
        B2BPartnerResponse partner = partnerService.getPartnerByCustomer(customerProfileId);
        assertThat(partner.usedCredit()).isEqualByComparingTo(orderTotal);
        assertThat(partner.availableCredit()).isEqualByComparingTo(new BigDecimal("500000.00").subtract(orderTotal));

        // Verify GST Invoice generation
        Page<B2BInvoiceResponse> invoices = invoiceService.getCustomerInvoices(customerProfileId, PageRequest.of(0, 10));
        assertThat(invoices.getContent()).isNotEmpty();
        B2BInvoiceResponse invoice = invoices.getContent().getFirst();
        assertThat(invoice.orderId()).isEqualTo(order.id());
        assertThat(invoice.totalAmount()).isEqualByComparingTo(orderTotal);
        assertThat(invoice.paymentStatus()).isEqualTo("UNPAID");
        assertThat(invoice.paymentTerms()).isEqualTo("NET_30");
        assertThat(invoice.dueAt()).isNotNull();
        assertThat(invoice.taxBreakdown()).containsKey("cgst");
        assertThat(invoice.taxBreakdown()).containsKey("sgst");
    }

    @Test
    @DisplayName("Exceeding approved credit limit throws INSUFFICIENT_B2B_CREDIT")
    void testExceedingCreditLimitThrowsInsufficientCredit() {
        // Request 6,000 units: 6000 * 95 = 570,000 > credit limit of 500,000
        CreateB2BBulkOrderCommand orderCmd = new CreateB2BBulkOrderCommand(
                Map.of("city", "Mumbai"),
                Map.of("city", "Mumbai"),
                List.of(new B2BBulkOrderItemCommand(testVariantId, 6000)),
                "NET_30",
                "Massive order exceeding limit"
        );

        BusinessException ex = assertThrows(BusinessException.class, () ->
                orderService.createBulkOrder(customerProfileId, orderCmd));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_B2B_CREDIT);
    }

    @Test
    @DisplayName("Paying invoice settles payment and restores partner available credit line")
    void testPayInvoiceRestoresCreditLine() {
        CreateB2BBulkOrderCommand orderCmd = new CreateB2BBulkOrderCommand(
                Map.of("city", "Mumbai"),
                Map.of("city", "Mumbai"),
                List.of(new B2BBulkOrderItemCommand(testVariantId, 80)),
                "NET_30",
                "Invoice settlement test"
        );

        OrderResponse order = orderService.createBulkOrder(customerProfileId, orderCmd);
        BigDecimal orderTotal = order.totalAmount();

        B2BInvoiceResponse invoice = invoiceService.getInvoiceByOrderId(order.id());
        assertThat(invoice.paymentStatus()).isEqualTo("UNPAID");

        // Pay the invoice
        B2BInvoiceResponse settled = invoiceService.payInvoice(invoice.id());
        assertThat(settled.paymentStatus()).isEqualTo("PAID");
        assertThat(settled.paidAt()).isNotNull();

        // Verify partner used credit is restored
        B2BPartnerResponse partner = partnerService.getPartnerByCustomer(customerProfileId);
        assertThat(partner.usedCredit()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(partner.availableCredit()).isEqualByComparingTo(new BigDecimal("500000.00"));

        // Verify credit ledger contains CREDIT_RELEASE
        List<B2BCreditLedgerResponse> ledger = invoiceService.getPartnerCreditLedger(customerProfileId);
        assertThat(ledger).anyMatch(entry -> "CREDIT_RELEASE".equals(entry.entryType()) && entry.amount().compareTo(orderTotal) == 0);
    }
}