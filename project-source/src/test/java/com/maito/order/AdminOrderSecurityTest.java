package com.maito.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maito.auth.jwt.JwtTokenProvider;
import com.maito.cart.api.dto.AddCartItemCommand;
import com.maito.cart.api.dto.CartResponse;
import com.maito.cart.api.service.CartService;
import com.maito.order.api.dto.CreateOrderCommand;
import com.maito.order.api.dto.OrderResponse;
import com.maito.order.api.dto.UpdateOrderStatusCommand;
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

    @Autowired
    private OrderService orderService;

    @Autowired
    private CartService cartService;

    @Autowired
    private UserService userService;

    private final UUID mintVariantId = UUID.fromString("f1000000-0000-0000-0000-000000000004");

    @Test
    @DisplayName("Assert HTTP 401 UNAUTHORIZED when anonymous attempts checkout create-order")
    void shouldRejectAnonymousCheckoutOrderCreation() throws Exception {
        CreateOrderCommand cmd = new CreateOrderCommand(
                Map.of("line1", "Test Address", "city", "Mumbai", "pincode", "400001"),
                null
        );

        mockMvc.perform(post("/api/v1/checkout/create-order")
                        .header("X-Tenant-ID", "mito_crunch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cmd)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

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
    @DisplayName("Assert HTTP 200 OK when ROLE_TENANT_ADMIN accesses /api/v1/admin/orders and mutates status")
    void shouldAllowAdminToAccessOrdersAndMutateStatus() throws Exception {
        String adminToken = tokenProvider.generateAccessToken(
                UUID.randomUUID(),
                "mito_crunch",
                "ROLE_TENANT_ADMIN",
                List.of("orders:manage"),
                UUID.randomUUID()
        );

        // 1. Admin reads orders
        mockMvc.perform(get("/api/v1/admin/orders")
                        .header("X-Tenant-ID", "mito_crunch")
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 2. Admin updates an existing order status to SHIPPED
        TenantContextHolder.set(new TenantContext("mito_crunch", "mitocrunch", "IN", "INR", "en_IN", "db_mitocrunch"));
        OrderResponse order;
        try {
            TenantProfileDto cust = userService.createProfile(UUID.randomUUID(), "AdminStatus", "Test", "ROLE_TENANT_CUSTOMER", List.of());
            CartResponse cart = cartService.getOrCreateCart(null, cust.id(), "INR");
            cartService.addItem(cart.id(), new AddCartItemCommand(mintVariantId, 1));
            order = orderService.createOrderFromCart(cart.id(), cust.id(), new CreateOrderCommand(
                    Map.of("line1", "Admin Road", "city", "Delhi", "pincode", "110001"), null
            ));
        } finally {
            TenantContextHolder.clear();
        }

        UpdateOrderStatusCommand updateCmd = new UpdateOrderStatusCommand("SHIPPED");

        mockMvc.perform(put("/api/v1/admin/orders/" + order.id() + "/status")
                        .header("X-Tenant-ID", "mito_crunch")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateCmd)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderStatus").value("SHIPPED"));
    }

    @Test
    @DisplayName("Assert HTTP 403 FORBIDDEN when customer attempts to access another customer's order number")
    void shouldRejectCustomerQueryingAnotherCustomersOrder() throws Exception {
        TenantContextHolder.set(new TenantContext("mito_crunch", "mitocrunch", "IN", "INR", "en_IN", "db_mitocrunch"));
        OrderResponse victimOrder;
        UUID attackerProfileId = UUID.randomUUID();
        try {
            TenantProfileDto victim = userService.createProfile(UUID.randomUUID(), "Victim", "Customer", "ROLE_TENANT_CUSTOMER", List.of());
            userService.createProfile(UUID.randomUUID(), "Attacker", "Customer", "ROLE_TENANT_CUSTOMER", List.of());

            CartResponse cart = cartService.getOrCreateCart(null, victim.id(), "INR");
            cartService.addItem(cart.id(), new AddCartItemCommand(mintVariantId, 1));
            victimOrder = orderService.createOrderFromCart(cart.id(), victim.id(), new CreateOrderCommand(
                    Map.of("line1", "Victim Address", "city", "Chennai", "pincode", "600001"), null
            ));
        } finally {
            TenantContextHolder.clear();
        }

        String attackerToken = tokenProvider.generateAccessToken(
                UUID.randomUUID(),
                "mito_crunch",
                "ROLE_TENANT_CUSTOMER",
                List.of(),
                attackerProfileId
        );

        mockMvc.perform(get("/api/v1/account/orders/" + victimOrder.orderNumber())
                        .header("X-Tenant-ID", "mito_crunch")
                        .header("Authorization", "Bearer " + attackerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_4030"));
    }
}
