package com.maito.catalog.internal.seed;

import com.maito.catalog.internal.domain.CatalogCategory;
import com.maito.catalog.internal.domain.CatalogProduct;
import com.maito.catalog.internal.domain.CatalogProductVariant;
import com.maito.catalog.internal.domain.InventoryLevel;
import com.maito.catalog.internal.repository.CatalogCategoryRepository;
import com.maito.catalog.internal.repository.CatalogProductRepository;
import com.maito.catalog.internal.repository.CatalogProductVariantRepository;
import com.maito.catalog.internal.repository.InventoryLevelRepository;
import com.maito.tenant.routing.TenantContext;
import com.maito.tenant.routing.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@Order(10)
@RequiredArgsConstructor
@Slf4j
public class MultiTenantCatalogSeeder implements CommandLineRunner {

    private final CatalogCategoryRepository categoryRepository;
    private final CatalogProductRepository productRepository;
    private final CatalogProductVariantRepository variantRepository;
    private final InventoryLevelRepository inventoryRepository;

    @Override
    public void run(String... args) {
        log.info("Starting MultiTenantCatalogSeeder verification across tenant databases...");

        TenantContext mitoContext = new TenantContext("mito_crunch", "mitocrunch", "IN", "INR", "en_IN", "db_mitocrunch");
        TenantContext solarContext = new TenantContext("vijiya_solar", "vijiyasolar", "IN", "INR", "en_IN", "db_vijiyasolar");
        TenantContext everritesContext = new TenantContext("everrites", "everrites", "IN", "INR", "en_IN", "db_everrites");

        seedMitoCrunch(mitoContext);
        seedVijiyaSolar(solarContext);
        seedEverrites(everritesContext);

        log.info("MultiTenantCatalogSeeder completed successfully.");
    }

    private void seedMitoCrunch(TenantContext context) {
        TenantContextHolder.set(context);
        try {
            CatalogCategory snacks = ensureCategory(UUID.fromString("e1000000-0000-0000-0000-000000000001"), "snacks", "Snacks", "Crunchy artisan superfood snacks", "/snacks", 1);
            CatalogCategory gourmet = ensureCategory(UUID.fromString("e1000000-0000-0000-0000-000000000002"), "gourmet", "Gourmet", "Luxury imported herbs & truffle delicacies", "/gourmet", 2);
            CatalogCategory healthy = ensureCategory(UUID.fromString("e1000000-0000-0000-0000-000000000003"), "healthy-nibbles", "Healthy Nibbles", "Clean, zero preservative wellness snacks", "/healthy-nibbles", 3);

            UUID truffleId = UUID.fromString("f0000000-0000-0000-0000-000000000004");
            if (productRepository.findById(truffleId).isEmpty()) {
                CatalogProduct truffle = CatalogProduct.builder()
                        .id(truffleId)
                        .slug("truffle-crunch")
                        .name("Truffle Crunch")
                        .brand("Mito Crunch")
                        .shortDescription("Exquisite European black truffle and sea salt roasted luxury makhana.")
                        .description("Gourmet reserve foxnuts infused with authentic black summer truffle oil.")
                        .categoryId(gourmet.getId())
                        .hsnCode("19041090")
                        .taxRatePercent(new BigDecimal("5.00"))
                        .attributes(Map.of("dietary", List.of("Gluten-Free", "Gourmet")))
                        .isPublished(true)
                        .build();
                productRepository.save(truffle);

                seedVariant(UUID.fromString("f1000000-0000-0000-0000-000000000041"), truffleId, "MITO-MAK-TRUF-100G", 100,
                        Map.of("size", "100g", "title", "100g Pouch", "flavor", "Black Truffle"),
                        Map.of("INR", Map.of("mrp", 299.00, "salePrice", 249.00)),
                        List.of("/assets/brands/mito_crunch/products/makhana_truffle.svg"), 60, "WH-MITO_CRUNCH", new BigDecimal("0.05"), "19041090");

                seedVariant(UUID.fromString("f1000000-0000-0000-0000-000000000042"), truffleId, "MITO-MAK-TRUF-250G", 250,
                        Map.of("size", "250g", "title", "250g Pouch", "flavor", "Black Truffle"),
                        Map.of("INR", Map.of("mrp", 699.00, "salePrice", 549.00)),
                        List.of("/assets/brands/mito_crunch/products/makhana_truffle.svg"), 20, "WH-MITO_CRUNCH", new BigDecimal("0.05"), "19041090");

                seedVariant(UUID.fromString("f1000000-0000-0000-0000-000000000043"), truffleId, "MITO-MAK-TRUF-500G", 500,
                        Map.of("size", "500g", "title", "500g Pouch", "flavor", "Black Truffle"),
                        Map.of("INR", Map.of("mrp", 1299.00, "salePrice", 999.00)),
                        List.of("/assets/brands/mito_crunch/products/makhana_truffle.svg"), 5, "WH-MITO_CRUNCH", new BigDecimal("0.05"), "19041090");
            }
        } catch (Exception e) {
            log.warn("Mito Crunch catalog check notice: {}", e.getMessage());
        } finally {
            TenantContextHolder.clear();
        }
    }

    private void seedVijiyaSolar(TenantContext context) {
        TenantContextHolder.set(context);
        try {
            CatalogCategory onGrid = ensureCategory(UUID.fromString("e2000000-0000-0000-0000-000000000001"), "on-grid-rooftop", "On-Grid Rooftop", "Grid-tied rooftop solar packages with net metering", "/on-grid-rooftop", 1);
            CatalogCategory hybrid = ensureCategory(UUID.fromString("e2000000-0000-0000-0000-000000000002"), "hybrid-solar", "Hybrid Solar", "Hybrid energy storage systems with smart backup", "/hybrid-solar", 2);
            CatalogCategory inverters = ensureCategory(UUID.fromString("e2000000-0000-0000-0000-000000000003"), "inverters-storage", "Inverters & Storage", "Heavy-duty industrial inverters and modular storage", "/inverters-storage", 3);

            UUID solar3kwId = UUID.fromString("f0000000-0000-0000-0000-000000000011");
            if (productRepository.findById(solar3kwId).isEmpty()) {
                CatalogProduct prod = CatalogProduct.builder()
                        .id(solar3kwId)
                        .slug("3kw-monocrystalline-rooftop-system")
                        .name("3kW Monocrystalline Rooftop System")
                        .brand("Vijiya Solar")
                        .shortDescription("High-efficiency Tier 1 monocrystalline rooftop solar installation for residences.")
                        .description("Complete residential rooftop solar plant featuring high-efficiency Half-Cut Mono PERC panels.")
                        .categoryId(onGrid.getId())
                        .hsnCode("85414011")
                        .taxRatePercent(new BigDecimal("12.00"))
                        .attributes(Map.of("panelType", "Mono PERC"))
                        .isPublished(true)
                        .build();
                productRepository.save(prod);

                seedVariant(UUID.fromString("b2000000-0000-0000-0000-000000000011"), solar3kwId, "VS-3KW-KIT", 150000,
                        Map.of("package", "Complete Kit", "title", "Complete Kit"),
                        Map.of("INR", Map.of("mrp", 195000.00, "salePrice", 165000.00)),
                        List.of("/assets/brands/vijiya_solar/products/solar_rooftop_3kw.svg"), 15, "WH-VIJIYA_SOLAR", new BigDecimal("0.12"), "85414011");
            }
        } catch (Exception e) {
            log.warn("Vijiya Solar catalog check notice: {}", e.getMessage());
        } finally {
            TenantContextHolder.clear();
        }
    }

    private void seedEverrites(TenantContext context) {
        TenantContextHolder.set(context);
        try {
            CatalogCategory pantry = ensureCategory(UUID.fromString("e3000000-0000-0000-0000-000000000001"), "pantry-essentials", "Pantry Essentials", "Natural pure raw pantry ingredients and sweeteners", "/pantry-essentials", 1);
            CatalogCategory oils = ensureCategory(UUID.fromString("e3000000-0000-0000-0000-000000000002"), "cold-pressed-oils", "Cold-Pressed Oils", "Traditional wood-pressed kachi ghani culinary oils", "/cold-pressed-oils", 2);
            CatalogCategory staples = ensureCategory(UUID.fromString("e3000000-0000-0000-0000-000000000003"), "organic-staples", "Organic Staples", "Stone-ground whole grains, millets, and flours", "/organic-staples", 3);

            UUID oilId = UUID.fromString("f3000000-0000-0000-0000-000000000001");
            if (productRepository.findById(oilId).isEmpty()) {
                CatalogProduct prod = CatalogProduct.builder()
                        .id(oilId)
                        .slug("cold-pressed-yellow-mustard-oil")
                        .name("Cold-Pressed Yellow Mustard Oil")
                        .brand("Everrites")
                        .shortDescription("Traditional kachi ghani cold-pressed mustard oil, unfiltered and rich in natural nutrients.")
                        .description("Wood-pressed at low temperatures to preserve natural antioxidants, pungency, and vital fatty acids.")
                        .categoryId(oils.getId())
                        .hsnCode("15149110")
                        .taxRatePercent(new BigDecimal("5.00"))
                        .attributes(Map.of("extraction", "Cold Pressed"))
                        .isPublished(true)
                        .build();
                productRepository.save(prod);

                seedVariant(UUID.fromString("b3000000-0000-0000-0000-000000000011"), oilId, "EV-MO-1L", 1000,
                        Map.of("size", "1L", "title", "1L Bottle"),
                        Map.of("INR", Map.of("mrp", 290.00, "salePrice", 245.00)),
                        List.of("/assets/brands/everrites/products/mustard_oil.svg"), 75, "WH-EVERRITES", new BigDecimal("0.05"), "15149110");
            }
        } catch (Exception e) {
            log.warn("Everrites catalog check notice: {}", e.getMessage());
        } finally {
            TenantContextHolder.clear();
        }
    }

    private CatalogCategory ensureCategory(UUID id, String slug, String name, String description, String path, int order) {
        return categoryRepository.findById(id).orElseGet(() -> {
            CatalogCategory cat = CatalogCategory.builder()
                    .id(id)
                    .slug(slug)
                    .name(name)
                    .description(description)
                    .materializedPath(path)
                    .displayOrder(order)
                    .isActive(true)
                    .build();
            return categoryRepository.save(cat);
        });
    }

    private void seedVariant(UUID id, UUID productId, String sku, int weightGrams, Map<String, Object> attrs,
                             Map<String, Object> pricing, List<String> media, int stock, String wh,
                             BigDecimal taxRate, String hsn) {
        if (variantRepository.findById(id).isEmpty()) {
            CatalogProductVariant variant = CatalogProductVariant.builder()
                    .id(id)
                    .productId(productId)
                    .sku(sku)
                    .weightGrams(weightGrams)
                    .variantAttributes(attrs)
                    .pricingTiers(pricing)
                    .mediaGallery(media)
                    .isActive(true)
                    .taxRate(taxRate)
                    .hsnCode(hsn)
                    .build();
            variantRepository.save(variant);

            InventoryLevel level = InventoryLevel.builder()
                    .variantId(id)
                    .warehouseCode(wh)
                    .availableStock(stock)
                    .reservedStock(0)
                    .reorderThreshold(10)
                    .build();
            inventoryRepository.save(level);
        }
    }
}
