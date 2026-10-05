package com.maito.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maito.auth.jwt.JwtTokenProvider;
import com.maito.order.api.dto.UpdateOrderStatusCommand;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AdminOrderSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Assert HTTP 401 UNAUTHORIZED when anonymous accesses /api/v1/admin/orders")
    void shouldRejectAnonymousOrderAccess() throws Exception {
        mockMvc.perform(get("/api/v1/admin/orders")
                        .header("X-Tenant-ID", "mito_crunch")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Assert HTTP 403 FORBIDDEN when customer hits /api/v1/admin/orders")
    void shouldRejectCustomerAdminOrderAccess() throws Exception {
        String customerToken = tokenProvider.generateAccessToken(
                UUID.randomUUID(),
                "mito_crunch",
                "ROLE_TENANT_CUSTOMER",
                List.of(),
                UUID.randomUUID()
        );

        mockMvc.perform(get("/api/v1/admin/orders")
                        .header("X-Tenant-ID", "mito_crunch")
                        .header("Authorization", "Bearer " + customerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("Assert HTTP 200 OK when ROLE_TENANT_ADMIN accesses /api/v1/admin/orders")
    void shouldAllowAdminToAccessOrders() throws Exception {
        String adminToken = tokenProvider.generateAccessToken(
                UUID.randomUUID(),
                "mito_crunch",
                "ROLE_TENANT_ADMIN",
                List.of("orders:manage"),
                UUID.randomUUID()
        );

        mockMvc.perform(get("/api/v1/admin/orders")
                        .header("X-Tenant-ID", "mito_crunch")
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("Assert HTTP 403 FORBIDDEN when customer attempts to update order status")
    void shouldRejectCustomerUpdatingOrderStatus() throws Exception {
        String customerToken = tokenProvider.generateAccessToken(
                UUID.randomUUID(),
                "mito_crunch",
                "ROLE_TENANT_CUSTOMER",
                List.of(),
                UUID.randomUUID()
        );

        UpdateOrderStatusCommand cmd = new UpdateOrderStatusCommand("FULFILLED");

        mockMvc.perform(put("/api/v1/admin/orders/" + UUID.randomUUID() + "/status")
                        .header("X-Tenant-ID", "mito_crunch")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cmd)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }
}
