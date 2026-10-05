package com.maito.catalog;

import com.maito.catalog.api.dto.CreateProductCommand;
import com.maito.catalog.api.dto.CreateVariantCommand;
import com.maito.catalog.api.dto.InventoryLevelDto;
import com.maito.catalog.api.dto.PriceTierDto;
import com.maito.catalog.api.dto.ProductDetailResponse;
import com.maito.catalog.api.service.CatalogService;
import com.maito.catalog.api.service.InventoryService;
import com.maito.shared.exception.BusinessException;
import com.maito.shared.exception.ErrorCode;
import com.maito.tenant.routing.TenantContext;
import com.maito.tenant.routing.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
class InventoryConcurrencyIntegrationTest {

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private InventoryService inventoryService;

    private final TenantContext tenantContext = new TenantContext(
            "mito_crunch",
            "mitocrunch",
            "IN",
            "INR",
            "en_IN",
            "db_mitocrunch"
    );

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(tenantContext);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Assert zero overselling: 50 concurrent threads reserving stock of 20 units -> exactly 20 succeed, 30 fail with INSUFFICIENT_STOCK")
    void shouldPreventOversellingUnderHighConcurrency() throws Exception {
        // 1. Create a new test product with exactly 20 units of stock
        String uniqueSlug = "concurrency-test-" + UUID.randomUUID().toString().substring(0, 8);
        CreateVariantCommand variantCmd = new CreateVariantCommand(
                uniqueSlug.toUpperCase() + "-SKU",
                "1234567890",
                100,
                Map.of("flavor", "Jalapeno"),
                Map.of("INR", new PriceTierDto(new BigDecimal("199.00"), new BigDecimal("149.00"))),
                List.of(),
                true,
                20, // initial stock = 20
                "DEFAULT_WH"
        );

        CreateProductCommand prodCmd = new CreateProductCommand(
                uniqueSlug,
                "Concurrency Test Product",
                "Mito Crunch",
                "Concurrency test",
                "Detailed description",
                null,
                "19041090",
                new BigDecimal("5.00"),
                Map.of(),
                true,
                List.of(variantCmd)
        );

        ProductDetailResponse created = catalogService.createProduct(prodCmd);
        UUID variantId = created.variants().get(0).id();

        // 2. Prepare 50 concurrent threads competing to reserve 1 unit each
        int totalThreads = 50;
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(totalThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger insufficientStockCount = new AtomicInteger(0);
        AtomicInteger otherFailures = new AtomicInteger(0);

        for (int i = 0; i < totalThreads; i++) {
            executor.submit(() -> {
                try {
                    // Set tenant context for this worker thread
                    TenantContextHolder.set(tenantContext);
                    startGate.await(); // Synchronize all threads to fire simultaneously

                    inventoryService.reserveStock(variantId, "DEFAULT_WH", 1);
                    successCount.incrementAndGet();
                } catch (BusinessException be) {
                    if (be.getErrorCode() == ErrorCode.INSUFFICIENT_STOCK) {
                        insufficientStockCount.incrementAndGet();
                    } else {
                        otherFailures.incrementAndGet();
                    }
                } catch (Exception ex) {
                    otherFailures.incrementAndGet();
                } finally {
                    TenantContextHolder.clear();
                    endGate.countDown();
                }
            });
        }

        // Fire all threads simultaneously
        startGate.countDown();
        boolean completed = endGate.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(otherFailures.get()).isEqualTo(0);

        // 3. Assert exact counts: exactly 20 succeeded, exactly 30 rejected
        assertThat(successCount.get()).isEqualTo(20);
        assertThat(insufficientStockCount.get()).isEqualTo(30);

        // 4. Assert inventory level in database
        TenantContextHolder.set(tenantContext);
        try {
            InventoryLevelDto finalLevel = inventoryService.getInventoryLevel(variantId, "DEFAULT_WH");
            assertThat(finalLevel.availableStock()).isEqualTo(0);
            assertThat(finalLevel.reservedStock()).isEqualTo(20);
            assertThat(finalLevel.stockStatus()).isEqualTo("OUT_OF_STOCK");
        } finally {
            TenantContextHolder.clear();
        }
    }
}
