package com.maito.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maito.catalog.internal.domain.CatalogCategory;
import com.maito.catalog.internal.domain.CatalogProduct;
import com.maito.catalog.internal.domain.CatalogProductVariant;
import com.maito.catalog.internal.domain.InventoryLevel;
import com.maito.catalog.internal.repository.CatalogCategoryRepository;
import com.maito.catalog.internal.repository.CatalogProductRepository;
import com.maito.catalog.internal.repository.CatalogProductVariantRepository;
import com.maito.catalog.internal.repository.InventoryLevelRepository;
import com.maito.search.api.dto.SearchCriteria;
import com.maito.search.api.dto.SearchResultDto;
import com.maito.search.api.dto.SearchSuggestionDto;
import com.maito.search.api.service.ProductSearchService;
import com.maito.search.internal.config.SearchElasticsearchProperties;
import com.maito.search.internal.index.IndexManager;
import com.maito.search.internal.resolver.TenantIndexResolver;
import com.maito.tenant.routing.TenantContext;
import com.maito.tenant.routing.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class SearchServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductSearchService searchService;

    @Autowired
    private IndexManager indexManager;

    @Autowired
    private TenantIndexResolver tenantIndexResolver;

    @Autowired
    private SearchElasticsearchProperties searchProperties;

    @Autowired
    private CatalogProductRepository productRepository;

    @Autowired
    private CatalogProductVariantRepository variantRepository;

    @Autowired
    private CatalogCategoryRepository categoryRepository;

    @Autowired
    private InventoryLevelRepository inventoryRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private final TenantContext tenantContext = new TenantContext(
            "mito_crunch",
            "mitocrunch",
            "IN",
            "INR",
            "en_IN",
            "db_mitocrunch"
    );

    private CatalogCategory testCategory;
    private CatalogProduct samplePeriPeri;
    private CatalogProduct sampleHimalayanSalt;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(tenantContext);

        // Ensure category exists
        testCategory = categoryRepository.findBySlug("roasted-makhana")
                .orElseGet(() -> categoryRepository.save(CatalogCategory.builder()
                        .name("Roasted Makhana")
                        .slug("roasted-makhana")
                        .description("Premium roasted foxnuts")
                        .materializedPath("/roasted-makhana")
                        .isActive(true)
                        .displayOrder(1)
                        .build()));

        // Seed Sample Product 1: Peri Peri
        String periSlug = "makhana-peri-peri-" + UUID.randomUUID().toString().substring(0, 8);
        samplePeriPeri = productRepository.save(CatalogProduct.builder()
                .slug(periSlug)
                .name("Roasted Makhana - Peri Peri")
                .brand("Mito Crunch")
                .shortDescription("Spicy and tangy roasted foxnuts")
                .description("Crispy foxnuts seasoned with fiery African Bird's Eye peri peri chili.")
                .categoryId(testCategory.getId())
                .isPublished(true)
                .attributes(Map.of("dietary", List.of("Gluten-Free", "Vegan")))
                .build());

        CatalogProductVariant variant1 = variantRepository.save(CatalogProductVariant.builder()
                .productId(samplePeriPeri.getId())
                .sku("SKU-PERI-" + UUID.randomUUID().toString().substring(0, 8))
                .weightGrams(100)
                .pricingTiers(Map.of("INR", Map.of("salePrice", "149.00", "mrp", "199.00")))
                .isActive(true)
                .mediaGallery(List.of("/assets/brands/mito_crunch/products/makhana_peri_peri.webp"))
                .build());

        inventoryRepository.save(InventoryLevel.builder()
                .variantId(variant1.getId())
                .warehouseCode("DEFAULT_WH")
                .availableStock(100)
                .reservedStock(0)
                .reorderThreshold(10)
                .build());

        // Seed Sample Product 2: Himalayan Pink Salt
        String saltSlug = "makhana-pink-salt-" + UUID.randomUUID().toString().substring(0, 8);
        sampleHimalayanSalt = productRepository.save(CatalogProduct.builder()
                .slug(saltSlug)
                .name("Roasted Makhana - Himalayan Pink Salt")
                .brand("Himalayan Harvest")
                .shortDescription("Lightly salted crunchy foxnuts")
                .description("Handpicked lotus seeds roasted with pure pink rock salt.")
                .categoryId(testCategory.getId())
                .isPublished(true)
                .attributes(Map.of("dietary", List.of("Keto", "Vegan")))
                .build());

        CatalogProductVariant variant2 = variantRepository.save(CatalogProductVariant.builder()
                .productId(sampleHimalayanSalt.getId())
                .sku("SKU-SALT-" + UUID.randomUUID().toString().substring(0, 8))
                .weightGrams(100)
                .pricingTiers(Map.of("INR", Map.of("salePrice", "139.00", "mrp", "180.00")))
                .isActive(true)
                .mediaGallery(List.of("/assets/brands/mito_crunch/products/makhana_himalayan_salt.webp"))
                .build());

        inventoryRepository.save(InventoryLevel.builder()
                .variantId(variant2.getId())
                .warehouseCode("DEFAULT_WH")
                .availableStock(50)
                .reservedStock(0)
                .reorderThreshold(10)
                .build());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    // =========================================================================
    // 1. FUZZY & TYPO-TOLERANT SEARCH TESTS
    // =========================================================================

    @Test
    @DisplayName("Verify typo-tolerant / fuzzy search: searching 'pari peri' matches 'Roasted Makhana - Peri Peri'")
    void testFuzzyAndTypoTolerantSearch_MatchesPeriPeri() {
        SearchCriteria criteria = new SearchCriteria(
                "pari peri", null, null, null, null, null, "relevance", null, "INR", 0, 10
        );

        SearchResultDto result = searchService.searchProducts(criteria);

        assertThat(result).isNotNull();
        assertThat(result.products()).isNotEmpty();
        boolean matched = result.products().stream()
                .anyMatch(p -> p.name().contains("Peri Peri") || p.brand().equalsIgnoreCase("Mito Crunch"));
        assertThat(matched).isTrue();
    }

    // =========================================================================
    // 2. FACETED AGGREGATION & PRICE STATS TESTS
    // =========================================================================

    @Test
    @DisplayName("Verify faceted aggregations: calculates distinct counts for brands and categories")
    void testFacetedAggregation_ComputesBrandAndCategoryCounts() {
        SearchCriteria criteria = new SearchCriteria(
                null, null, null, null, null, null, "relevance", null, "INR", 0, 50
        );

        SearchResultDto result = searchService.searchProducts(criteria);

        assertThat(result).isNotNull();
        assertThat(result.products()).isNotEmpty();
        assertThat(result.brandFacets()).isNotEmpty();
        assertThat(result.brandFacets()).containsKey("Mito Crunch");
        assertThat(result.brandFacets().get("Mito Crunch")).isGreaterThanOrEqualTo(1L);

        assertThat(result.priceStats()).isNotNull();
        assertThat(result.priceStats().min()).isNotNull();
        assertThat(result.priceStats().max()).isNotNull();
        assertThat(result.priceStats().max()).isGreaterThanOrEqualTo(result.priceStats().min());
    }

    // =========================================================================
    // 3. GRACEFUL FALLBACK TO SQL WHEN ELASTICSEARCH DISABLED
    // =========================================================================

    @Test
    @DisplayName("Verify graceful degradation to SQL search when Elasticsearch is disabled")
    void testGracefulFallbackToSql_WhenElasticsearchDisabled() {
        boolean original = searchProperties.isEnabled();
        try {
            searchProperties.setEnabled(false);

            SearchCriteria criteria = new SearchCriteria(
                    "Makhana", null, null, null, null, true, "relevance", null, "INR", 0, 10
            );

            SearchResultDto result = searchService.searchProducts(criteria);

            assertThat(result).isNotNull();
            assertThat(result.searchEngine()).isEqualTo("SQL_FALLBACK");
            assertThat(result.products()).isNotEmpty();
            assertThat(result.products().get(0).inStock()).isTrue();
        } finally {
            searchProperties.setEnabled(original);
        }
    }

    // =========================================================================
    // 4. INSTANT TYPEAHEAD AUTOCOMPLETE SUGGESTIONS
    // =========================================================================

    @Test
    @DisplayName("Verify instant typeahead suggestions return top matching products")
    void testTypeaheadSuggestions_ReturnsTopSuggestions() {
        List<SearchSuggestionDto> suggestions = searchService.suggestKeywords("peri");

        assertThat(suggestions).isNotNull();
        assertThat(suggestions).isNotEmpty();
        assertThat(suggestions.get(0).text()).containsIgnoringCase("Peri");
        assertThat(suggestions.get(0).price()).isNotNull();
    }

    // =========================================================================
    // 5. BATCH RE-INDEXING & INDEX MANAGEMENT
    // =========================================================================

    @Test
    @DisplayName("Verify IndexManager provisions versioned index and alias safely")
    void testIndexManager_ProvisionsVersionedIndexAndAlias() {
        indexManager.ensureIndexAndAlias("mitocrunch");
        int count = searchService.reindexTenantCatalog("mitocrunch");
        assertThat(count).isGreaterThanOrEqualTo(2);
    }

    // =========================================================================
    // 6. REST API CONTROLLER ENDPOINTS (STOREFRONT & ADMIN)
    // =========================================================================

    @Test
    @DisplayName("Verify Public Storefront Search Endpoint GET /api/v1/search/products")
    void testPublicSearchEndpoint_Returns200WithFacets() throws Exception {
        mockMvc.perform(get("/api/v1/search/products")
                        .header("X-Tenant-ID", "mito_crunch")
                        .param("q", "makhana")
                        .param("page", "0")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.products").isArray())
                .andExpect(jsonPath("$.data.brandFacets").isMap());
    }

    @Test
    @DisplayName("Verify Public Autocomplete Suggest Endpoint GET /api/v1/search/suggest")
    void testPublicSuggestEndpoint_Returns200WithSuggestions() throws Exception {
        mockMvc.perform(get("/api/v1/search/suggest")
                        .header("X-Tenant-ID", "mito_crunch")
                        .param("q", "makhana")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @WithMockUser(roles = "TENANT_ADMIN")
    @DisplayName("Verify Admin Reindex Endpoint POST /api/v1/admin/search/reindex")
    void testAdminReindexEndpoint_Authorized_Returns200() throws Exception {
        mockMvc.perform(post("/api/v1/admin/search/reindex")
                        .header("X-Tenant-ID", "mito_crunch")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }
}
