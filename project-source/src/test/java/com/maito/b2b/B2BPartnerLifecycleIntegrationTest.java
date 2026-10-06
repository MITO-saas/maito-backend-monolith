package com.maito.b2b;

import com.maito.b2b.api.dto.B2BBulkOrderItemCommand;
import com.maito.b2b.api.dto.B2BPartnerResponse;
import com.maito.b2b.api.dto.CreateB2BBulkOrderCommand;
import com.maito.b2b.api.dto.RegisterPartnerCommand;
import com.maito.b2b.api.dto.VerifyPartnerCommand;
import com.maito.b2b.api.service.B2BOrderService;
import com.maito.b2b.api.service.B2BPartnerService;
import com.maito.catalog.api.dto.CreateProductCommand;
import com.maito.catalog.api.dto.CreateVariantCommand;
import com.maito.catalog.api.dto.PriceTierDto;
import com.maito.catalog.api.dto.ProductDetailResponse;
import com.maito.catalog.api.service.CatalogService;
import com.maito.catalog.api.service.InventoryService;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("local")
class B2BPartnerLifecycleIntegrationTest {

    @Autowired
    private B2BPartnerService partnerService;

    @Autowired
    private B2BOrderService orderService;

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

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(tenantContextMito);
        TenantProfileDto profile = userService.createProfile(
                UUID.randomUUID(),
                "B2BTester_" + System.currentTimeMillis(),
                "Enterprise",
                "ROLE_TENANT_CUSTOMER",
                List.of()
        );
        customerProfileId = profile.id();

        String slug = "b2b-snack-" + UUID.randomUUID().toString().substring(0, 8);
        CreateVariantCommand vCmd = new CreateVariantCommand(
                slug.toUpperCase() + "-SKU",
                "BAR-" + UUID.randomUUID().toString().substring(0, 8),
                100,
                Map.of("flavor", "Spicy"),
                Map.of("INR", new PriceTierDto(new BigDecimal("199.00"), new BigDecimal("149.00"))),
                List.of(),
                true,
                1000,
                "DEFAULT_WH"
        );
        ProductDetailResponse product = catalogService.createProduct(new CreateProductCommand(
                slug,
                "B2B Wholesale Snack " + slug,
                "Mito Crunch",
                "B2B short",
                "B2B item description",
                null,
                "19041090",
                new BigDecimal("5.00"),
                Map.of(),
                true,
                List.of(vCmd)
        ));
        testVariantId = product.variants().getFirst().id();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Successfully register B2B partner with valid GSTIN and verify PENDING status")
    void testRegisterPartnerWithValidGstin() {
        String randomGstin = "10ABCDE" + (1000 + (int)(Math.random() * 8999)) + "F1Z5";
        RegisterPartnerCommand cmd = new RegisterPartnerCommand(
                "Patna Grand Hotel Pvt Ltd",
                "Grand Hotel",
                randomGstin,
                null,
                "FSSAI-12345678901234",
                Map.of("city", "Patna", "state", "Bihar")
        );

        B2BPartnerResponse response = partnerService.registerPartner(customerProfileId, cmd);

        assertThat(response).isNotNull();
        assertThat(response.companyLegalName()).isEqualTo("Patna Grand Hotel Pvt Ltd");
        assertThat(response.gstin()).isEqualTo(randomGstin);
        assertThat(response.pan()).isEqualTo(randomGstin.substring(2, 12));
        assertThat(response.verificationStatus()).isEqualTo("PENDING_VERIFICATION");
        assertThat(response.creditLimit()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.availableCredit()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Assert BusinessException INVALID_GSTIN when registering with malformed GSTIN")
    void testRegisterPartnerWithInvalidGstinThrows() {
        RegisterPartnerCommand cmd = new RegisterPartnerCommand(
                "Invalid Hotel Ltd",
                null,
                "INVALID_GSTIN_123",
                null,
                null,
                Map.of()
        );

        BusinessException ex = assertThrows(BusinessException.class, () ->
                partnerService.registerPartner(customerProfileId, cmd));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_GSTIN);
    }

    @Test
    @DisplayName("Admin verifies B2B partner, assigns credit limit of 200,000 and 30-day payment terms")
    void testAdminVerifyPartnerApprovesCreditAndTerms() {
        String randomGstin = "10ABCDE" + (1000 + (int)(Math.random() * 8999)) + "F1Z5";
        B2BPartnerResponse partner = partnerService.registerPartner(customerProfileId, new RegisterPartnerCommand(
                "Royal Palace Caterers",
                "Royal Palace",
                randomGstin,
                null,
                null,
                Map.of("city", "Patna")
        ));

        VerifyPartnerCommand verifyCmd = new VerifyPartnerCommand(
                "VERIFIED",
                new BigDecimal("200000.00"),
                30,
                "Verified by Risk Ops"
        );

        B2BPartnerResponse verified = partnerService.verifyPartner(partner.id(), verifyCmd);

        assertThat(verified.verificationStatus()).isEqualTo("VERIFIED");
        assertThat(verified.creditLimit()).isEqualByComparingTo(new BigDecimal("200000.00"));
        assertThat(verified.paymentTermsDays()).isEqualTo(30);
        assertThat(verified.availableCredit()).isEqualByComparingTo(new BigDecimal("200000.00"));
    }

    @Test
    @DisplayName("Unverified partner cannot place Net-30 bulk wholesale orders")
    void testUnverifiedPartnerCannotPlaceNetCreditBulkOrder() {
        String randomGstin = "10ABCDE" + (1000 + (int)(Math.random() * 8999)) + "F1Z5";
        partnerService.registerPartner(customerProfileId, new RegisterPartnerCommand(
                "Unverified Cafe",
                null,
                randomGstin,
                null,
                null,
                Map.of()
        ));

        CreateB2BBulkOrderCommand orderCmd = new CreateB2BBulkOrderCommand(
                Map.of("city", "Patna"),
                Map.of("city", "Patna"),
                List.of(new B2BBulkOrderItemCommand(testVariantId, 20)),
                "NET_30",
                "Urgent wholesale restock"
        );

        BusinessException ex = assertThrows(BusinessException.class, () ->
                orderService.createBulkOrder(customerProfileId, orderCmd));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.B2B_PARTNER_NOT_VERIFIED);
    }
}