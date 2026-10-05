package com.maito.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maito.auth.jwt.JwtTokenProvider;
import com.maito.catalog.api.dto.AdjustStockCommand;
import com.maito.catalog.api.dto.CreateProductCommand;
import com.maito.catalog.api.dto.CreateVariantCommand;
import com.maito.catalog.api.dto.PriceTierDto;
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
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AdminCatalogControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Assert HTTP 401 UNAUTHORIZED when anonymous request hits POST /api/v1/admin/catalog/products")
    void shouldRejectAnonymousProductCreation() throws Exception {
        CreateProductCommand cmd = buildSampleCreateCommand("anonymous-test-product");

        mockMvc.perform(post("/api/v1/admin/catalog/products")
                        .header("X-Tenant-ID", "mito_crunch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cmd)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Assert HTTP 401 UNAUTHORIZED when anonymous request hits POST /api/v1/admin/inventory/adjust")
    void shouldRejectAnonymousInventoryAdjustment() throws Exception {
        AdjustStockCommand cmd = new AdjustStockCommand(UUID.randomUUID(), "DEFAULT_WH", 10, "Test");

        mockMvc.perform(post("/api/v1/admin/inventory/adjust")
                        .header("X-Tenant-ID", "mito_crunch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cmd)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Assert HTTP 403 FORBIDDEN when customer hits POST /api/v1/admin/catalog/products")
    void shouldRejectCustomerProductCreation() throws Exception {
        String customerToken = tokenProvider.generateAccessToken(
                UUID.randomUUID(),
                "mito_crunch",
                "ROLE_TENANT_CUSTOMER",
                List.of(),
                UUID.randomUUID()
        );

        CreateProductCommand cmd = buildSampleCreateCommand("customer-forbidden-product");

        mockMvc.perform(post("/api/v1/admin/catalog/products")
                        .header("X-Tenant-ID", "mito_crunch")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cmd)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("Assert HTTP 403 FORBIDDEN when customer hits POST /api/v1/admin/inventory/adjust")
    void shouldRejectCustomerInventoryAdjustment() throws Exception {
        String customerToken = tokenProvider.generateAccessToken(
                UUID.randomUUID(),
                "mito_crunch",
                "ROLE_TENANT_CUSTOMER",
                List.of(),
                UUID.randomUUID()
        );

        AdjustStockCommand cmd = new AdjustStockCommand(UUID.randomUUID(), "DEFAULT_WH", 50, "Test Customer");

        mockMvc.perform(post("/api/v1/admin/inventory/adjust")
                        .header("X-Tenant-ID", "mito_crunch")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cmd)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("Assert HTTP 201 CREATED when ROLE_TENANT_ADMIN creates product via POST /api/v1/admin/catalog/products")
    void shouldAllowAdminToCreateProduct() throws Exception {
        String adminToken = tokenProvider.generateAccessToken(
                UUID.randomUUID(),
                "mito_crunch",
                "ROLE_TENANT_ADMIN",
                List.of("catalog:manage"),
                UUID.randomUUID()
        );

        String uniqueSlug = "admin-created-" + UUID.randomUUID().toString().substring(0, 8);
        CreateProductCommand cmd = buildSampleCreateCommand(uniqueSlug);

        mockMvc.perform(post("/api/v1/admin/catalog/products")
                        .header("X-Tenant-ID", "mito_crunch")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cmd)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.slug").value(uniqueSlug))
                .andExpect(jsonPath("$.data.variants[0].sku").value(uniqueSlug.toUpperCase() + "-100G"));
    }

    @Test
    @DisplayName("Assert HTTP 200 OK when ROLE_TENANT_ADMIN adjusts inventory via POST /api/v1/admin/inventory/adjust")
    void shouldAllowAdminToAdjustInventory() throws Exception {
        String adminToken = tokenProvider.generateAccessToken(
                UUID.randomUUID(),
                "mito_crunch",
                "ROLE_TENANT_ADMIN",
                List.of("inventory:manage"),
                UUID.randomUUID()
        );

        // Adjust stock for known seeded variant f1000000-0000-0000-0000-000000000001
        UUID variantId = UUID.fromString("f1000000-0000-0000-0000-000000000001");
        AdjustStockCommand cmd = new AdjustStockCommand(variantId, "DEFAULT_WH", 15, "Replenishment from batch A");

        mockMvc.perform(post("/api/v1/admin/inventory/adjust")
                        .header("X-Tenant-ID", "mito_crunch")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cmd)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.variantId").value(variantId.toString()));
    }

    private CreateProductCommand buildSampleCreateCommand(String slug) {
        CreateVariantCommand variant = new CreateVariantCommand(
                slug.toUpperCase() + "-100G",
                "890999999999",
                100,
                Map.of("flavor", "Tangy Tomato"),
                Map.of("INR", new PriceTierDto(new BigDecimal("150.00"), new BigDecimal("120.00"))),
                List.of("/assets/brands/mito_crunch/products/sample.webp"),
                true,
                50,
                "DEFAULT_WH"
        );

        return new CreateProductCommand(
                slug,
                "Sample Product " + slug,
                "Mito Crunch",
                "Short description",
                "Full description",
                null,
                "19041090",
                new BigDecimal("5.00"),
                Map.of("dietary", List.of("Gluten-Free")),
                true,
                List.of(variant)
        );
    }
}
