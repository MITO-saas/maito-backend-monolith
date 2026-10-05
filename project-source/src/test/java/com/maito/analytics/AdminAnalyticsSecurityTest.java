package com.maito.analytics;

import com.maito.analytics.api.dto.DashboardKpiResponse;
import com.maito.analytics.api.service.AnalyticsService;
import com.maito.audit.api.service.AuditLogService;
import com.maito.auth.jwt.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AdminAnalyticsSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @MockBean
    private AnalyticsService analyticsService;

    @MockBean
    private AuditLogService auditLogService;

    private static final String TENANT_HEADER = "X-Tenant-ID";
    private static final String TENANT_SLUG = "mito_crunch";

    @Test
    @DisplayName("Assert anonymous access to /api/v1/admin/analytics/kpis returns HTTP 401 Unauthorized")
    void shouldRejectAnonymousAnalyticsAccess() throws Exception {
        mockMvc.perform(get("/api/v1/admin/analytics/kpis")
                        .header(TENANT_HEADER, TENANT_SLUG))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Assert customer access to /api/v1/admin/analytics/kpis returns HTTP 403 Forbidden")
    void shouldRejectCustomerAnalyticsAccess() throws Exception {
        String customerToken = tokenProvider.generateAccessToken(
                UUID.randomUUID(), "mito_crunch", "ROLE_TENANT_CUSTOMER", List.of(), UUID.randomUUID()
        );

        mockMvc.perform(get("/api/v1/admin/analytics/kpis")
                        .header(TENANT_HEADER, TENANT_SLUG)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Assert anonymous access to /api/v1/admin/audit/logs returns HTTP 401 Unauthorized")
    void shouldRejectAnonymousAuditAccess() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit/logs")
                        .header(TENANT_HEADER, TENANT_SLUG))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Assert customer access to /api/v1/admin/audit/logs returns HTTP 403 Forbidden")
    void shouldRejectCustomerAuditAccess() throws Exception {
        String customerToken = tokenProvider.generateAccessToken(
                UUID.randomUUID(), "mito_crunch", "ROLE_TENANT_CUSTOMER", List.of(), UUID.randomUUID()
        );

        mockMvc.perform(get("/api/v1/admin/audit/logs")
                        .header(TENANT_HEADER, TENANT_SLUG)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Assert tenant admin access to /api/v1/admin/analytics/kpis returns HTTP 200 OK")
    void shouldAllowAdminAnalyticsAccess() throws Exception {
        String adminToken = tokenProvider.generateAccessToken(
                UUID.randomUUID(), "mito_crunch", "ROLE_TENANT_ADMIN", List.of(), UUID.randomUUID()
        );

        when(analyticsService.getExecutiveKpis(any(), any()))
                .thenReturn(new DashboardKpiResponse(BigDecimal.valueOf(1000), 10, BigDecimal.valueOf(100), List.of(), List.of()));

        mockMvc.perform(get("/api/v1/admin/analytics/kpis")
                        .header(TENANT_HEADER, TENANT_SLUG)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.grossMerchandiseValue").value(1000));
    }

    @Test
    @DisplayName("Assert tenant admin access to /api/v1/admin/audit/logs returns HTTP 200 OK")
    void shouldAllowAdminAuditAccess() throws Exception {
        String adminToken = tokenProvider.generateAccessToken(
                UUID.randomUUID(), "mito_crunch", "ROLE_TENANT_ADMIN", List.of(), UUID.randomUUID()
        );

        when(auditLogService.getAuditLogs(any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/admin/audit/logs")
                        .header(TENANT_HEADER, TENANT_SLUG)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
