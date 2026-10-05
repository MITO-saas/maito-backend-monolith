package com.maito.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maito.catalog.api.dto.CategoryDto;
import com.maito.catalog.api.dto.PriceTierDto;
import com.maito.catalog.api.dto.ProductDetailResponse;
import com.maito.catalog.api.dto.ProductSummaryDto;
import com.maito.catalog.api.dto.VariantDto;
import com.maito.catalog.api.service.CatalogService;
import com.maito.catalog.internal.controller.StorefrontCatalogController;
import com.maito.shared.exception.BusinessException;
import com.maito.shared.exception.ErrorCode;
import com.maito.store.api.dto.StoreSettingsDto;
import com.maito.store.api.service.StoreService;
import com.maito.store.internal.controller.StorefrontStoreController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {StorefrontCatalogController.class, StorefrontStoreController.class})
@AutoConfigureMockMvc(addFilters = false)
class StorefrontCatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CatalogService catalogService;

    @MockBean
    private StoreService storeService;

    @Test
    @DisplayName("GET /api/v1/catalog/categories returns 200 OK with category hierarchy")
    void shouldReturnCategoryTree() throws Exception {
        CategoryDto child = new CategoryDto(
                UUID.randomUUID(),
                "roasted-makhana",
                "Roasted Makhana",
                "Roasted foxnuts",
                null,
                "/snacks/roasted-makhana",
                1,
                true,
                List.of()
        );
        CategoryDto parent = new CategoryDto(
                UUID.randomUUID(),
                "snacks",
                "Snacks",
                "Healthy snacks",
                null,
                "/snacks",
                1,
                true,
                List.of(child)
        );

        when(catalogService.getCategoryTree()).thenReturn(List.of(parent));

        mockMvc.perform(get("/api/v1/catalog/categories")
                        .header("X-Tenant-ID", "mito_crunch")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].slug").value("snacks"))
                .andExpect(jsonPath("$.data[0].children[0].slug").value("roasted-makhana"));
    }

    @Test
    @DisplayName("GET /api/v1/catalog/products returns 200 OK with filtered summary list")
    void shouldSearchProducts() throws Exception {
        ProductSummaryDto summary = new ProductSummaryDto(
                UUID.randomUUID(),
                "artisanal-roasted-peri-peri-jumbo-makhana",
                "Artisanal Roasted Peri Peri Jumbo Makhana",
                "Mito Crunch",
                "Crispy slow-roasted foxnuts",
                UUID.randomUUID(),
                "roasted-makhana",
                new PriceTierDto(new BigDecimal("199.00"), new BigDecimal("149.00")),
                "/assets/brands/mito_crunch/products/makhana_peri_peri.webp",
                List.of("Gluten-Free", "Vegan"),
                true
        );

        when(catalogService.searchProducts(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(summary));

        mockMvc.perform(get("/api/v1/catalog/products")
                        .param("categorySlug", "roasted-makhana")
                        .param("currency", "INR")
                        .header("X-Tenant-ID", "mito_crunch")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].slug").value("artisanal-roasted-peri-peri-jumbo-makhana"))
                .andExpect(jsonPath("$.data[0].priceRange.salePrice").value(149.00));
    }

    @Test
    @DisplayName("GET /api/v1/catalog/products/{slug} returns 200 OK with product details and USD pricing")
    void shouldReturnProductDetailWithRequestedCurrency() throws Exception {
        VariantDto variant = new VariantDto(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "MITO-MAK-PERI-100G",
                "8901234567890",
                100,
                Map.of("flavor", "Peri Peri"),
                Map.of("USD", new PriceTierDto(new BigDecimal("4.99"), new BigDecimal("3.99"))),
                new PriceTierDto(new BigDecimal("4.99"), new BigDecimal("3.99")),
                List.of("/assets/brands/mito_crunch/products/makhana_peri_peri.webp"),
                true,
                250,
                "IN_STOCK"
        );

        ProductDetailResponse response = new ProductDetailResponse(
                UUID.randomUUID(),
                "artisanal-roasted-peri-peri-jumbo-makhana",
                "Artisanal Roasted Peri Peri Jumbo Makhana",
                "Mito Crunch",
                "Crispy foxnuts",
                "Full description",
                UUID.randomUUID(),
                "roasted-makhana",
                "19041090",
                new BigDecimal("5.00"),
                Map.of("dietary", List.of("Gluten-Free")),
                true,
                "USD",
                List.of(variant)
        );

        when(catalogService.getProductBySlug(eq("artisanal-roasted-peri-peri-jumbo-makhana"), eq("USD")))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/catalog/products/artisanal-roasted-peri-peri-jumbo-makhana")
                        .param("currency", "USD")
                        .header("X-Tenant-ID", "mito_crunch")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.slug").value("artisanal-roasted-peri-peri-jumbo-makhana"))
                .andExpect(jsonPath("$.data.currency").value("USD"))
                .andExpect(jsonPath("$.data.variants[0].activePrice.salePrice").value(3.99))
                .andExpect(jsonPath("$.data.variants[0].stockStatus").value("IN_STOCK"));
    }

    @Test
    @DisplayName("GET /api/v1/catalog/products/{slug} returns 404 NOT FOUND when slug does not exist")
    void shouldReturn404WhenProductNotFound() throws Exception {
        when(catalogService.getProductBySlug(eq("non-existent-product"), any()))
                .thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Product not found: non-existent-product"));

        mockMvc.perform(get("/api/v1/catalog/products/non-existent-product")
                        .header("X-Tenant-ID", "mito_crunch")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("MAITO_4040"));
    }

    @Test
    @DisplayName("GET /api/v1/store/settings returns 200 OK with active store settings")
    void shouldReturnStoreSettings() throws Exception {
        StoreSettingsDto settings = new StoreSettingsDto(
                UUID.randomUUID(),
                "Mito Crunch",
                "support@mitocrunch.com",
                "+91 98765 43210",
                "INR",
                List.of("INR", "USD"),
                "Asia/Kolkata",
                Map.of("freeShippingThreshold", 499)
        );

        when(storeService.getStoreSettings()).thenReturn(settings);

        mockMvc.perform(get("/api/v1/store/settings")
                        .header("X-Tenant-ID", "mito_crunch")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.storeName").value("Mito Crunch"))
                .andExpect(jsonPath("$.data.baseCurrency").value("INR"));
    }
}
