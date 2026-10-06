package com.maito.wallet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maito.auth.jwt.JwtTokenProvider;
import com.maito.tenant.routing.TenantContext;
import com.maito.tenant.routing.TenantContextHolder;
import com.maito.user.api.dto.TenantProfileDto;
import com.maito.user.api.service.UserService;
import com.maito.wallet.api.dto.AdjustWalletCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AdminWalletSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @BeforeEach
    void setUp() {
        TenantContextHolder.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Assert HTTP 401 UNAUTHORIZED when anonymous attempts wallet adjustment")
    void testAnonymousWalletAdjustmentRejected() throws Exception {
        AdjustWalletCommand cmd = new AdjustWalletCommand(
                UUID.randomUUID(),
                "CREDIT",
                new BigDecimal("50.00"),
                "BONUS",
                "REF-1",
                "Test bonus"
        );

        mockMvc.perform(post("/api/v1/admin/wallet/adjust")
                        .header("X-Tenant-ID", "mito_crunch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cmd)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Assert HTTP 403 FORBIDDEN when customer token attempts admin wallet adjustment")
    void testCustomerTokenForbiddenOnAdminWallet() throws Exception {
        String customerToken = tokenProvider.generateAccessToken(
                UUID.randomUUID(),
                "mito_crunch",
                "ROLE_TENANT_CUSTOMER",
                List.of(),
                UUID.randomUUID()
        );

        AdjustWalletCommand cmd = new AdjustWalletCommand(
                UUID.randomUUID(),
                "CREDIT",
                new BigDecimal("100.00"),
                "MANUAL_ADJUSTMENT",
                "REF-2",
                "Customer trying to self-credit"
        );

        mockMvc.perform(post("/api/v1/admin/wallet/adjust")
                        .header("X-Tenant-ID", "mito_crunch")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cmd)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Assert HTTP 200 OK when tenant admin executes wallet adjustment")
    void testTenantAdminCanAdjustWallet() throws Exception {
        TenantContextHolder.set(new TenantContext("mito_crunch", "mitocrunch", "IN", "INR", "en_IN", "db_mitocrunch"));
        UUID customerProfileId;
        try {
            TenantProfileDto customer = userService.createProfile(
                    UUID.randomUUID(),
                    "AdminAdjCust_" + System.currentTimeMillis(),
                    "Tester",
                    "ROLE_TENANT_CUSTOMER",
                    List.of()
            );
            customerProfileId = customer.id();
        } finally {
            TenantContextHolder.clear();
        }

        String adminToken = tokenProvider.generateAccessToken(
                UUID.randomUUID(),
                "mito_crunch",
                "ROLE_TENANT_ADMIN",
                List.of("wallet:manage"),
                UUID.randomUUID()
        );

        AdjustWalletCommand cmd = new AdjustWalletCommand(
                customerProfileId,
                "CREDIT",
                new BigDecimal("75.00"),
                "CUSTOMER_SUPPORT_GOODWILL",
                "TICKET-999",
                "Goodwill compensation credit"
        );

        mockMvc.perform(post("/api/v1/admin/wallet/adjust")
                        .header("X-Tenant-ID", "mito_crunch")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cmd)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.balance").value(75.0));
    }
}
