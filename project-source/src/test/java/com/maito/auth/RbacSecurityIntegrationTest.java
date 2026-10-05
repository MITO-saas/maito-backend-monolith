package com.maito.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maito.auth.jwt.JwtTokenProvider;
import com.maito.cms.api.dto.CreatePageCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItems;
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

    @Autowired
    private ObjectMapper objectMapper;

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
    @DisplayName("Assert HTTP 201 when authenticated as ROLE_TENANT_ADMIN creating CMS page via /api/v1/admin/cms/pages")
    void shouldAuthorizeAdminToCreateCmsPage() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        String adminToken = tokenProvider.generateAccessToken(
                adminId,
                "mito_crunch",
                "ROLE_TENANT_ADMIN",
                List.of("cms:manage"),
                profileId
        );

        CreatePageCommand cmd = new CreatePageCommand(
                "audit-test-page-" + UUID.randomUUID().toString().substring(0, 8),
                "Audit Test Page",
                Map.of("metaTitle", "Audit Test"),
                true
        );

        mockMvc.perform(post("/api/v1/admin/cms/pages")
                        .header("X-Tenant-ID", "mito_crunch")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cmd)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Audit Test Page"));
    }

    @Test
    @DisplayName("Assert HTTP 401 when JWT token signature or payload is tampered")
    void shouldRejectTamperedJwtToken() throws Exception {
        UUID customerId = UUID.randomUUID();
        String validToken = tokenProvider.generateAccessToken(
                customerId,
                "mito_crunch",
                "ROLE_TENANT_CUSTOMER",
                List.of(),
                UUID.randomUUID()
        );

        String tamperedToken = validToken.substring(0, validToken.length() - 6) + "xxxxxx";

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("X-Tenant-ID", "mito_crunch")
                        .header("Authorization", "Bearer " + tamperedToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Assert HTTP 401 when JWT access token is expired past TTL")
    void shouldRejectExpiredJwtToken() throws Exception {
        UUID customerId = UUID.randomUUID();
        String expiredToken = tokenProvider.generateTokenWithCustomExpiry(
                customerId,
                "mito_crunch",
                "ROLE_TENANT_CUSTOMER",
                List.of(),
                UUID.randomUUID(),
                -5000L // 5 seconds in the past
        );

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("X-Tenant-ID", "mito_crunch")
                        .header("Authorization", "Bearer " + expiredToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Assert HTTP 403 when cross-tenant token replay is detected (mito_crunch token sent to vijiya_solar)")
    void shouldRejectCrossTenantTokenReplay() throws Exception {
        UUID userId = UUID.randomUUID();
        String tokenMito = tokenProvider.generateAccessToken(
                userId,
                "mito_crunch",
                "ROLE_TENANT_CUSTOMER",
                List.of(),
                UUID.randomUUID()
        );

        // Replay against vijiya_solar tenant
        mockMvc.perform(get("/api/v1/account/profile")
                        .header("X-Tenant-ID", "vijiya_solar")
                        .header("Authorization", "Bearer " + tokenMito)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("CROSS_TENANT_VIOLATION"));
    }

    @Test
    @DisplayName("Assert arbitrary dynamic JSONB permissions parse cleanly into UserPrincipal context")
    void shouldParseDynamicArbitraryJsonbPermissions() throws Exception {
        UUID userId = UUID.randomUUID();
        List<String> arbitraryPerms = List.of("inventory:read", "warehouse:override", "reports:export");

        String dynamicToken = tokenProvider.generateAccessToken(
                userId,
                "mito_crunch",
                "ROLE_TENANT_STAFF",
                arbitraryPerms,
                UUID.randomUUID()
        );

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("X-Tenant-ID", "mito_crunch")
                        .header("Authorization", "Bearer " + dynamicToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.permissions", hasItems("inventory:read", "warehouse:override", "reports:export")));
    }
}
