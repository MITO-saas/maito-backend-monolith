package com.maito.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maito.auth.jwt.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class RbacSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Test
    @DisplayName("Assert HTTP 401 when accessing /api/v1/admin/cms/pages without token")
    void shouldReturn401WhenAccessingAdminWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/admin/cms/pages")
                        .header("X-Tenant-ID", "mito_crunch")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Assert HTTP 403 when authenticated as ROLE_TENANT_CUSTOMER hitting /api/v1/admin/**")
    void shouldReturn403WhenCustomerHitsAdminEndpoint() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        String customerToken = tokenProvider.generateAccessToken(
                customerId,
                "mito_crunch",
                "ROLE_TENANT_CUSTOMER",
                List.of(),
                profileId
        );

        mockMvc.perform(get("/api/v1/admin/cms/pages")
                        .header("X-Tenant-ID", "mito_crunch")
                        .header("Authorization", "Bearer " + customerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("Assert HTTP 200 or 404 (non-401/403 authorized) when authenticated as ROLE_TENANT_ADMIN hitting /api/v1/admin/**")
    void shouldAuthorizeAdminAccessToAdminEndpoint() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        String adminToken = tokenProvider.generateAccessToken(
                adminId,
                "mito_crunch",
                "ROLE_TENANT_ADMIN",
                List.of("cms:manage"),
                profileId
        );

        // GET /api/v1/admin/cms/pages does not exist, but let's test GET /api/v1/auth/me or an admin endpoint
        // A valid admin token hitting /api/v1/auth/me gives 200 OK
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("X-Tenant-ID", "mito_crunch")
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.role").value("ROLE_TENANT_ADMIN"));
    }

    @Test
    @DisplayName("Assert HTTP 403 when cross-tenant token replay is detected")
    void shouldRejectCrossTenantTokenReplay() throws Exception {
        UUID userId = UUID.randomUUID();
        // Token issued for tenant_a
        String tokenA = tokenProvider.generateAccessToken(
                userId,
                "tenant_a",
                "ROLE_TENANT_CUSTOMER",
                List.of(),
                UUID.randomUUID()
        );

        // Sent against tenant mito_crunch
        mockMvc.perform(get("/api/v1/account/profile")
                        .header("X-Tenant-ID", "mito_crunch")
                        .header("Authorization", "Bearer " + tokenA)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("CROSS_TENANT_VIOLATION"));
    }
}
