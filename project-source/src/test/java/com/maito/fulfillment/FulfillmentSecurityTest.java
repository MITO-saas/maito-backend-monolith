package com.maito.fulfillment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maito.auth.jwt.JwtTokenProvider;
import com.maito.fulfillment.api.dto.CarrierType;
import com.maito.fulfillment.api.dto.CreateShipmentCommand;
import com.maito.fulfillment.api.dto.ShipmentResponse;
import com.maito.fulfillment.api.service.FulfillmentService;
import com.maito.shared.exception.BusinessException;
import com.maito.shared.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class FulfillmentSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FulfillmentService fulfillmentService;

    private static final String TENANT_HEADER = "X-Tenant-ID";
    private static final String TENANT_SLUG = "mito_crunch";

    @Test
    @DisplayName("Assert public storefront tracking endpoint is accessible without authentication")
    void shouldAllowPublicStorefrontTracking() throws Exception {
        when(fulfillmentService.getTrackingByOrderNumber("ORD-12345"))
                .thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Order not found"));

        mockMvc.perform(get("/api/v1/fulfillment/track/ORD-12345")
                        .header(TENANT_HEADER, TENANT_SLUG))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Assert anonymous access to admin fulfillment is rejected with HTTP 401 Unauthorized")
    void shouldRejectAnonymousAccessToAdminFulfillment() throws Exception {
        CreateShipmentCommand cmd = new CreateShipmentCommand(
                UUID.randomUUID(), CarrierType.SELF_FLEET, "Rider", "9876543210", 500, 500
        );

        mockMvc.perform(post("/api/v1/admin/fulfillment/shipments")
                        .header(TENANT_HEADER, TENANT_SLUG)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cmd)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Assert customer role access to admin fulfillment is rejected with HTTP 403 Forbidden")
    void shouldRejectCustomerAccessToAdminFulfillment() throws Exception {
        String customerToken = jwtTokenProvider.generateAccessToken(
                UUID.randomUUID(), "mito_crunch", "ROLE_TENANT_CUSTOMER", List.of(), UUID.randomUUID()
        );

        CreateShipmentCommand cmd = new CreateShipmentCommand(
                UUID.randomUUID(), CarrierType.SELF_FLEET, "Rider", "9876543210", 500, 500
        );

        mockMvc.perform(post("/api/v1/admin/fulfillment/shipments")
                        .header(TENANT_HEADER, TENANT_SLUG)
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cmd)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Assert tenant admin role can book shipments and trigger dispatch operations")
    void shouldAllowTenantAdminToBookAndDispatchShipment() throws Exception {
        String adminToken = jwtTokenProvider.generateAccessToken(
                UUID.randomUUID(), "mito_crunch", "ROLE_TENANT_ADMIN", List.of(), UUID.randomUUID()
        );

        UUID orderId = UUID.randomUUID();
        UUID shipmentId = UUID.randomUUID();
        CreateShipmentCommand cmd = new CreateShipmentCommand(
                orderId, CarrierType.SELF_FLEET, "Rider", "9876543210", 500, 500
        );

        ShipmentResponse mockResponse = new ShipmentResponse(
                shipmentId,
                orderId,
                "ORD-99999",
                "SHP-001",
                CarrierType.SELF_FLEET,
                "SELF-001",
                com.maito.fulfillment.api.dto.ShipmentStatus.MANIFESTED,
                "Rider",
                "9876543210",
                "1234",
                500,
                500,
                "label.pdf",
                null,
                null,
                Instant.now()
        );

        when(fulfillmentService.createShipment(any())).thenReturn(mockResponse);
        when(fulfillmentService.dispatchShipment(shipmentId)).thenReturn(mockResponse);

        // 1. Create shipment
        mockMvc.perform(post("/api/v1/admin/fulfillment/shipments")
                        .header(TENANT_HEADER, TENANT_SLUG)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cmd)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.shipmentNumber").value("SHP-001"));

        // 2. Dispatch shipment
        mockMvc.perform(post("/api/v1/admin/fulfillment/shipments/" + shipmentId + "/dispatch")
                        .header(TENANT_HEADER, TENANT_SLUG)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
