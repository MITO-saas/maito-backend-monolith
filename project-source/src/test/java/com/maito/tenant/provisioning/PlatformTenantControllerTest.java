package com.maito.tenant.provisioning;

import com.maito.tenant.api.dto.TenantPoolMetrics;
import com.maito.tenant.datasource.HikariPoolManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PlatformTenantController.class)
@AutoConfigureMockMvc(addFilters = false)
class PlatformTenantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TenantProvisioningService provisioningService;

    @MockBean
    private HikariPoolManager poolManager;

    @Test
    @DisplayName("GET /telemetry returns safe connection pool telemetry")
    void shouldReturnPoolTelemetry() throws Exception {
        TenantPoolMetrics metrics = new TenantPoolMetrics("mito_crunch", 2, 3, 5, 0, "ACTIVE");
        when(poolManager.getPoolTelemetry()).thenReturn(List.of(metrics));

        mockMvc.perform(get("/api/v1/internal/platform/tenants/telemetry")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].tenantId").value("mito_crunch"))
                .andExpect(jsonPath("$.data[0].activeConnections").value(2))
                .andExpect(jsonPath("$.data[0].idleConnections").value(3))
                .andExpect(jsonPath("$.data[0].totalConnections").value(5))
                .andExpect(jsonPath("$.data[0].threadsAwaitingConnection").value(0))
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"));
    }

    @Test
    @DisplayName("DELETE /{tenantId} decommissions tenant successfully")
    void shouldDecommissionTenant() throws Exception {
        doNothing().when(provisioningService).decommissionTenant("mito_crunch");

        mockMvc.perform(delete("/api/v1/internal/platform/tenants/mito_crunch")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tenantId").value("mito_crunch"))
                .andExpect(jsonPath("$.data.status").value("DECOMMISSIONED"));

        verify(provisioningService, times(1)).decommissionTenant("mito_crunch");
    }
}