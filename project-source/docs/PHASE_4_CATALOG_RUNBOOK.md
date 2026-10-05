# Phase 4: Store Configurations, Catalog Hierarchy & Real-Time Concurrency-Safe Inventory Engine

## 1. Architectural Overview & Domain Model

Phase 4 establishes high-throughput e-commerce catalog hierarchies, multi-currency variant matrix, and atomic concurrency-safe inventory controls for all tenant storefronts.

### Domain Entity Architecture (Tenant Isolated)

All Phase 4 entities reside exclusively within tenant-isolated databases (`db_{tenant_slug}`), strictly isolated from the master control plane.

1. **`store_settings`**:
   - Stores tenant-specific configuration, brand name, support channels, timezone, base currency (`INR`), supported currencies (`["INR", "USD"]`), and commercial rules (tax-inclusive flags, free shipping thresholds).
   - Extends `BaseAuditableEntity`.

2. **`catalog_categories`**:
   - Materialized Path Category Tree (e.g., `/snacks/roasted-makhana`) enabling $O(1)$ prefix searches for nested category subtrees without recursive database queries.
   - Enforces unique slugs, display order sorting, and active toggling.

3. **`catalog_products`**:
   - Master product definition with HSN code (`19041090`), GST tax rate (e.g. `5.00%`), and JSONB `attributes` (dietary tags like `Gluten-Free`, `Vegan`, roasting process, shelf life).
   - Indexed via PostgreSQL GIN index on `attributes` for ultra-fast multi-facet JSON filtering.

4. **`catalog_product_variants` (SKUs)**:
   - SKU variant matrix with barcoding, net weight (grams), attribute variations (`{"flavor": "Peri Peri", "packSize": "100g"}`).
   - Dynamic JSONB `pricing_tiers` supporting multi-currency pricing (e.g. `{"INR": {"mrp": 199.00, "salePrice": 149.00}, "USD": {"mrp": 4.99, "salePrice": 3.99}}`).
   - Media gallery storing 10-year immutable localized asset paths.

5. **`inventory_levels`**:
   - Real-time stock levels partitioned by SKU variant and warehouse code (`DEFAULT_WH`).
   - Enforces physical integrity with DB check constraints (`available_stock >= 0`, `reserved_stock >= 0`).
   - Optimistic concurrency control via `@Version private Long version;`.

---

## 2. Real-Time Concurrency-Safe Atomic Stock Engine

To prevent overselling during flash sales, holiday promotions, and concurrent cart checkouts, inventory reservation executes via atomic database updates without heavy table-level pessimistic locks:

```java
@Modifying
@Query("UPDATE InventoryLevel i SET i.availableStock = i.availableStock - :qty, i.reservedStock = i.reservedStock + :qty " +
       "WHERE i.variantId = :variantId AND i.warehouseCode = :wh AND i.availableStock >= :qty")
int reserveStockAtomic(@Param("variantId") UUID variantId, @Param("wh") String wh, @Param("qty") int qty);
```

- **Zero-Overselling Guarantee**:
  - The `WHERE available_stock >= :qty` condition acts as an atomic barrier inside PostgreSQL MVCC row-level write locks.
  - If updated rows is `0`, the engine instantly rejects the request with `BusinessException(ErrorCode.INSUFFICIENT_STOCK)` (HTTP 409 Conflict).
  - Verified under automated multithreaded test (`InventoryConcurrencyIntegrationTest`): 50 concurrent threads reserving 20 available units resulting in exactly 20 successes, exactly 30 rejections, and zero negative inventory.

---

## 3. High-Performance Cache-Aside Topology

- **Redis Cache Key**: `tenant:{tenantId}:catalog:product:{slug}:{currency}`
- **TTL**: 10 Minutes (`Duration.ofMinutes(10)`)
- **Resilient Fallback**: Redis connection failures are gracefully caught, automatically falling back to PostgreSQL without interrupting customer traffic.
- **Cache Invalidation**: Automated wildcard eviction (`tenant:{tenantId}:catalog:product:{slug}:*`) triggered on admin product and inventory updates.

---

## 4. Ready-to-Use cURL Commands

### 4.1 Storefront Public APIs (No Auth Required)

#### A. Fetch Store Settings
```bash
curl -X GET "http://localhost:8080/api/v1/store/settings" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Accept: application/json"
```

#### B. Fetch Category Hierarchy Tree
```bash
curl -X GET "http://localhost:8080/api/v1/catalog/categories" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Accept: application/json"
```

#### C. Search Products with Filters
```bash
curl -X GET "http://localhost:8080/api/v1/catalog/products?currency=INR&dietary=Gluten-Free" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Accept: application/json"
```

#### D. Get Product Details (INR Currency)
```bash
curl -X GET "http://localhost:8080/api/v1/catalog/products/artisanal-roasted-peri-peri-jumbo-makhana?currency=INR" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Accept: application/json"
```

#### E. Get Product Details (USD Currency)
```bash
curl -X GET "http://localhost:8080/api/v1/catalog/products/artisanal-roasted-peri-peri-jumbo-makhana?currency=USD" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Accept: application/json"
```

---

### 4.2 Admin Management APIs (`ROLE_TENANT_ADMIN` Required)

#### Obtain Tenant Admin Token
```bash
ADMIN_TOKEN=$(curl -s -X POST "http://localhost:8080/api/v1/auth/login" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "admin@mitocrunch.com",
    "password": "Password123!"
  }' | jq -r '.data.accessToken')
```

#### A. Create New Catalog Product with Variants & Pricing Tiers
```bash
curl -X POST "http://localhost:8080/api/v1/admin/catalog/products" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "slug": "smoky-barbeque-crunch-makhana",
    "name": "Smoky Barbeque Crunch Makhana",
    "brand": "Mito Crunch",
    "shortDescription": "Sweet and smoky Texas barbeque spiced roasted makhana",
    "description": "Slow-roasted jumbo foxnuts glazed with artisanal barbeque rub.",
    "hsnCode": "19041090",
    "taxRatePercent": 5.00,
    "attributes": {
      "dietary": ["Gluten-Free", "Vegetarian"],
      "process": "Slow Roasted"
    },
    "isPublished": true,
    "variants": [
      {
        "sku": "MITO-MAK-BBQ-100G",
        "barcode": "8901234567895",
        "weightGrams": 100,
        "variantAttributes": {
          "flavor": "Smoky Barbeque",
          "packSize": "100g"
        },
        "pricingTiers": {
          "INR": {"mrp": 199.00, "salePrice": 149.00},
          "USD": {"mrp": 4.99, "salePrice": 3.99}
        },
        "mediaGallery": ["/assets/brands/mito_crunch/products/makhana_peri_peri.webp"],
        "isActive": true,
        "initialStock": 200,
        "warehouseCode": "DEFAULT_WH"
      }
    ]
  }'
```

#### B. Adjust / Replenish Inventory Stock
```bash
curl -X POST "http://localhost:8080/api/v1/admin/inventory/adjust" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "variantId": "f1000000-0000-0000-0000-000000000001",
    "warehouseCode": "DEFAULT_WH",
    "quantityDelta": 50,
    "reason": "Restocked from batch M-2026-10"
  }'
```

#### C. Update Store Settings
```bash
curl -X PUT "http://localhost:8080/api/v1/admin/store/settings" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "storeName": "Mito Crunch - Artisanal Roasted Makhana Superstore",
    "supportEmail": "care@mitocrunch.com",
    "supportPhone": "+91 98765 43210",
    "baseCurrency": "INR",
    "supportedCurrencies": ["INR", "USD", "EUR"],
    "timezone": "Asia/Kolkata",
    "commercialSettings": {
      "freeShippingThreshold": 399,
      "taxIncluded": true
    }
  }'
```
