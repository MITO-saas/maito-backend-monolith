package com.maito.fulfillment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maito.auth.jwt.JwtTokenProvider;
import com.maito.cart.api.dto.AddCartItemCommand;
import com.maito.cart.api.dto.CartResponse;
import com.maito.cart.api.service.CartService;
import com.maito.fulfillment.api.dto.CarrierType;
import com.maito.fulfillment.api.dto.CreateShipmentCommand;
import com.maito.fulfillment.api.dto.ShipmentResponse;
import com.maito.fulfillment.api.service.FulfillmentService;
import com.maito.order.api.dto.CreateOrderCommand;
import com.maito.order.api.dto.OrderResponse;
import com.maito.order.api.dto.PaymentCallbackCommand;
import com.maito.order.api.service.OrderService;
import com.maito.tenant.routing.TenantContext;
import com.maito.tenant.routing.TenantContextHolder;
import com.maito.user.api.dto.TenantProfileDto;
import com.maito.user.api.service.UserService;
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
    private JwtTokenProvider tokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FulfillmentService fulfillmentService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private CartService cartService;

    @Autowired
    private UserService userService;

    private final UUID periPeriVariantId = UUID.fromString("f1000000-0000-0000-0000-000000000001");

    private OrderResponse setupPaidOrder() {
        TenantContextHolder.set(new TenantContext("mito_crunch", "mitocrunch", "IN", "INR", "en_IN", "db_mitocrunch"));
        try {
            TenantProfileDto customer = userService.createProfile(
                    UUID.randomUUID(), "Security", "Cust", "ROLE_TENANT_CUSTOMER", List.of()
            );
            CartResponse cart = cartService.getOrCreateCart(null, customer.id(), "INR");
            cartService.addItem(cart.id(), new AddCartItemCommand(periPeriVariantId, 1));
            OrderResponse order = orderService.createOrderFromCart(cart.id(), customer.id(), new CreateOrderCommand(
                    Map.of("line1", "Station Road", "city", "Patna", "pincode", "800001"), null
            ));
            return orderService.confirmPayment(order.id(), new PaymentCallbackCommand("TXN-SEC", "PAID", "mock-sig"));
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    @DisplayName("Assert public storefront tracking is accessible to anonymous shoppers without authentication")
    void shouldAllowPublicStorefrontTracking() throws Exception {
        OrderResponse order = setupPaidOrder();
        TenantContextHolder.set(new TenantContext("mito_crunch", "mitocrunch", "IN", "INR", "en_IN", "db_mitocrunch"));
        try {
            fulfillmentService.createShipment(new CreateShipmentCommand(
                    order.id(), CarrierType.SELF_FLEET, "Rider", "9876543210", 500, 500
            ));
        } finally {
            TenantContextHolder.clear();
        }

        mockMvc.perform(get("/api/v1/fulfillment/track/" + order.orderNumber())
                        .header("X-Tenant-ID", "mito_crunch")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderNumber").value(order.orderNumber()))
                .andExpect(jsonPath("$.data.currentStatus").value("MANIFESTED"));
    }

    @Test
    @DisplayName("Assert HTTP 401 UNAUTHORIZED when anonymous attempts admin shipment creation")
    void shouldRejectAnonymousShipmentCreation() throws Exception {
        CreateShipmentCommand cmd = new CreateShipmentCommand(
                UUID.randomUUID(), CarrierType.SELF_FLEET, "Rider", "9876543210", 500, 500
        );

        mockMvc.perform(post("/api/v1/admin/fulfillment/shipments")
                        .header("X-Tenant-ID", "mito_crunch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cmd)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Assert HTTP 403 FORBIDDEN when customer attempts admin shipment creation")
    void shouldRejectCustomerShipmentCreation() throws Exception {
        String customerToken = tokenProvider.generateAccessToken(
                UUID.randomUUID(), "mito_crunch", "ROLE_TENANT_CUSTOMER", List.of(), UUID.randomUUID()
        );

        CreateShipmentCommand cmd = new CreateShipmentCommand(
                UUID.randomUUID(), CarrierType.SELF_FLEET, "Rider", "9876543210", 500, 500
        );

        mockMvc.perform(post("/api/v1/admin/fulfillment/shipments")
                        .header("X-Tenant-ID", "mito_crunch")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cmd)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("Assert HTTP 201 CREATED when ROLE_TENANT_ADMIN books shipment")
    void shouldAllowAdminToCreateShipment() throws Exception {
        OrderResponse order = setupPaidOrder();

        String adminToken = tokenProvider.generateAccessToken(
                UUID.randomUUID(), "mito_crunch", "ROLE_TENANT_ADMIN", List.of("fulfillment:manage"), UUID.randomUUID()
        );

        CreateShipmentCommand cmd = new CreateShipmentCommand(
                order.id(), CarrierType.DELHIVERY, null, null, 500, 500
        );

        mockMvc.perform(post("/api/v1/admin/fulfillment/shipments")
                        .header("X-Tenant-ID", "mito_crunch")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cmd)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderNumber").value(order.orderNumber()))
                .andExpect(jsonPath("$.data.carrierType").value("DELHIVERY"));
    }
}
