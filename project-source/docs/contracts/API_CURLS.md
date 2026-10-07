# Maito Multi-Tenant SaaS Platform - cURL Execution & Verification Guide

Base URL: `http://localhost:8080`  
Swagger UI: `http://localhost:8080/swagger-ui.html`  
OpenAPI 3 Spec: `http://localhost:8080/v3/api-docs`  
Actuator Telemetry: `http://localhost:8080/actuator/tenants`  

---

## 1. Automated Zero-Deploy Tenant Provisioning

### Provision New Isolated Tenant (`MITO_CRUNCH`)
Creates physical database `db_mitocrunch`, runs tenant Liquibase migrations, registers dedicated HikariCP pool, and pre-warms Redis routing cache:
```bash
curl -X POST "http://localhost:8080/api/v1/internal/platform/tenants" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -d '{
    "tenantId": "mito_crunch",
    "tenantSlug": "mitocrunch",
    "legalName": "Mito Crunch Superfoods Pvt Ltd",
    "primaryDomain": "store.mitocrunch.com",
    "countryCode": "IN",
    "currencyCode": "INR",
    "initialConfig": {
      "tier": "ENTERPRISE",
      "max_pool_size": 20
    }
  }'
```

**Expected Response (HTTP 201 Created):**
```json
{
  "success": true,
  "data": {
    "tenantId": "mito_crunch",
    "tenantSlug": "mitocrunch",
    "primaryDomain": "store.mitocrunch.com",
    "databaseName": "db_mitocrunch",
    "accountState": "ACTIVE",
    "provisionedAt": "2026-10-05T01:40:00.000Z",
    "message": "Tenant successfully provisioned with isolated database and live HikariCP pool."
  },
  "timestamp": "2026-10-05T01:40:00.000Z"
}
```

---

## 2. Inspect Tenant Control Plane Metadata

### Get Tenant Health & Routing Details
```bash
curl -X GET "http://localhost:8080/api/v1/internal/platform/tenants/mito_crunch" \
  -H "Accept: application/json"
```

---

## 3. Real-Time Connection Pool Telemetry

### Inspect Active Tenant Connection Pools (Zero-Credential Leakage)
```bash
curl -X GET "http://localhost:8080/api/v1/internal/platform/tenants/telemetry" \
  -H "Accept: application/json"
```
*Also available via Spring Boot Actuator*:
```bash
curl -X GET "http://localhost:8080/actuator/tenants"
```

---

## 4. Decommission Tenant & Evict Pool

### Decommission Tenant, Close Pool, and Purge Redis Cache
```bash
curl -X DELETE "http://localhost:8080/api/v1/internal/platform/tenants/mito_crunch" \
  -H "Accept: application/json"
```

---

## 5. Verify Multi-Tenant Dynamic Database Routing & Security Gates

### A. Route via `X-Tenant-ID` Header
```bash
curl -i -X GET "http://localhost:8080/api/v1/orders" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Accept: application/json"
```
*Response Header:* `X-Tenant-ID: mito_crunch`  
*Routing:* Connection dispatched to dedicated HikariPool for `db_mitocrunch`.

### B. Route via Custom Domain `Host` Header
```bash
curl -i -X GET "http://localhost:8080/api/v1/orders" \
  -H "Host: store.mitocrunch.com" \
  -H "Accept: application/json"
```
*Response Header:* `X-Tenant-ID: mito_crunch` (dynamically resolved from domain).

### C. Verify Suspended Tenant Rejection (Security Gate)
```bash
curl -i -X GET "http://localhost:8080/api/v1/orders" \
  -H "X-Tenant-ID: suspended_tenant" \
  -H "Accept: application/json"
```
*Expected Response (HTTP 403 Forbidden):*
```json
{
  "success": false,
  "error": {
    "code": "TENANT_SUSPENDED",
    "message": "Tenant account is currently suspended. Please contact platform administration."
  }
}
```

### D. Verify Inactive/Unknown Tenant Rejection (Security Gate)
```bash
curl -i -X GET "http://localhost:8080/api/v1/orders" \
  -H "X-Tenant-ID: invalid_tenant_sku" \
  -H "Accept: application/json"
```
*Expected Response (HTTP 404 Not Found):*
```json
{
  "success": false,
  "error": {
    "code": "TENANT_RESOLUTION_FAILED",
    "message": "Invalid or unresolvable tenant. Verify X-Tenant-ID header or Host mapping."
  }
}
```
## Phase 2: Headless CMS & Server-Driven UI (SDUI) Endpoints

### 1. Storefront: Fetch Published Page Layout
```bash
curl -X GET http://localhost:8080/api/v1/cms/pages/home \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Accept-Language: en" \
  -H "Accept: application/json"
```

### 2. Admin: Create or Update Page Layout
```bash
curl -X POST http://localhost:8080/api/v1/admin/cms/pages \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Content-Type: application/json" \
  -d '{
    "pageSlug": "about-us",
    "title": "About Mito Crunch",
    "seoMetadata": {
      "metaTitle": "About Us | Mito Crunch"
    },
    "isPublished": true,
    "sections": [
      {
        "componentType": "BRAND_STORY",
        "displayOrder": 1,
        "isActive": true,
        "visibilityRules": {},
        "contentPayload": {
          "heading": "From Pond to Pack",
          "body": "Directly sourced from Bihar farmers, roasted with zero trans-fats."
        }
      }
    ]
  }'
```

### 3. Admin: Hot-Update Section Payload & Purge Layout Cache
```bash
curl -X PUT http://localhost:8080/api/v1/admin/cms/sections/44444444-4444-4444-4444-444444444441 \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Content-Type: application/json" \
  -d '{
    "displayOrder": 1,
    "isActive": true,
    "visibilityRules": {},
    "contentPayload": {
      "text": "Flash Sale: 25% Off Today Only!",
      "backgroundColor": "#B91C1C",
      "textColor": "#FFFFFF"
    }
  }'
```
---

## 4. Multi-Tenant Authentication & Identity RBAC (Phase 3)

### Register Customer Identity & Tenant Profile
Creates global master identity in `db_global_master` and tenant-scoped profile in tenant database:
```bash
curl -X POST "http://localhost:8080/api/v1/auth/register" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -H "X-Tenant-ID: mito_crunch" \
  -d '{
    "email": "newcustomer@mitocrunch.com",
    "password": "SecurePassword@2026",
    "firstName": "Aarav",
    "lastName": "Sharma",
    "phone": "+919876543299"
  }'
```

### Authenticate Admin (Mito Crunch)
```bash
curl -X POST "http://localhost:8080/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -H "X-Tenant-ID: mito_crunch" \
  -d '{
    "email": "admin@mitocrunch.com",
    "password": "CrunchAdmin@2026"
  }'
```

### Authenticate Customer (Mito Crunch)
```bash
curl -X POST "http://localhost:8080/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -H "X-Tenant-ID: mito_crunch" \
  -d '{
    "email": "customer@mitocrunch.com",
    "password": "Customer@2026"
  }'
```

### Refresh Access Token
```bash
curl -X POST "http://localhost:8080/api/v1/auth/refresh" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -H "X-Tenant-ID: mito_crunch" \
  -d '{
    "refreshToken": "<REFRESH_TOKEN>"
  }'
```

### Get Current Security Context (`/me`)
```bash
curl -X GET "http://localhost:8080/api/v1/auth/me" \
  -H "Accept: application/json" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer <ACCESS_TOKEN>"
```

### Customer Add Shipping Address
```bash
curl -X POST "http://localhost:8080/api/v1/account/addresses" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer <CUSTOMER_ACCESS_TOKEN>" \
  -d '{
    "addressType": "SHIPPING",
    "recipientName": "Aarav Sharma",
    "phone": "+919876543299",
    "addressLine1": "Tower 4, Sector 62",
    "city": "Noida",
    "state": "Uttar Pradesh",
    "postalCode": "201309",
    "countryCode": "IN",
    "isDefault": true
  }'
```

### Customer List Shipping Addresses
```bash
curl -X GET "http://localhost:8080/api/v1/account/addresses" \
  -H "Accept: application/json" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer <CUSTOMER_ACCESS_TOKEN>"
```

---

## 8. Phase 4: Store Configurations, Catalog Hierarchy & Real-Time Concurrency-Safe Inventory Engine

### 8.1 Storefront Public Ingress (Anonymous / Customer Traffic)

#### Fetch Store Settings
```bash
curl -X GET "http://localhost:8080/api/v1/store/settings" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Accept: application/json"
```

#### Fetch Categories Tree
```bash
curl -X GET "http://localhost:8080/api/v1/catalog/categories" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Accept: application/json"
```

#### Search Catalog Products (with Dietary & Currency Filtering)
```bash
curl -X GET "http://localhost:8080/api/v1/catalog/products?currency=INR&categorySlug=roasted-makhana&dietary=Gluten-Free" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Accept: application/json"
```

#### Fetch Product Details by Slug (INR Pricing)
```bash
curl -X GET "http://localhost:8080/api/v1/catalog/products/artisanal-roasted-peri-peri-jumbo-makhana?currency=INR" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Accept: application/json"
```

#### Fetch Product Details by Slug (USD Pricing)
```bash
curl -X GET "http://localhost:8080/api/v1/catalog/products/artisanal-roasted-peri-peri-jumbo-makhana?currency=USD" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Accept: application/json"
```

### 8.2 Admin Management Ingress (Strictly Gated: ROLE_TENANT_ADMIN)

#### Admin: Create New Product with Variants & Pricing Tiers
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

#### Admin: Adjust Stock Levels
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

#### Admin: Update Store Settings
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

---

## 9. Phase 5: Cart State Engine, Discount/Promotions Engine & Concurrency-Safe Order Lifecycle

### 9.1 Storefront Cart Ingress (Anonymous / Guest or Authenticated)

#### Retrieve / Initialize Cart (Guest or Authenticated)
```bash
curl -X GET "http://localhost:8080/api/v1/cart" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "X-Cart-ID: guest-cart-12345" \
  -H "Accept: application/json"
```

#### Add Product Variant (SKU) to Cart
```bash
curl -X POST "http://localhost:8080/api/v1/cart/items" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "X-Cart-ID: guest-cart-12345" \
  -H "Content-Type: application/json" \
  -d '{
    "variantId": "f1000000-0000-0000-0000-000000000001",
    "quantity": 2
  }'
```

#### Update Cart Item Quantity
```bash
curl -X PUT "http://localhost:8080/api/v1/cart/items/ITEM_UUID" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "X-Cart-ID: guest-cart-12345" \
  -H "Content-Type: application/json" \
  -d '{
    "quantity": 4
  }'
```

#### Remove Cart Item
```bash
curl -X DELETE "http://localhost:8080/api/v1/cart/items/ITEM_UUID" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "X-Cart-ID: guest-cart-12345" \
  -H "Accept: application/json"
```

#### Merge Anonymous Guest Cart into Customer Account (ROLE_TENANT_CUSTOMER)
```bash
curl -X POST "http://localhost:8080/api/v1/cart/merge" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "guestCartId": "guest-cart-12345"
  }'
```

---

### 9.2 Storefront Promotions Ingress

#### Evaluate and Apply Coupon Code
```bash
curl -X POST "http://localhost:8080/api/v1/promotions/apply" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Content-Type: application/json" \
  -d '{
    "code": "CRUNCH20",
    "subtotal": 600.00
  }'
```

---

### 9.3 Storefront Checkout & Order Lifecycle Ingress (ROLE_TENANT_CUSTOMER)

#### Create Order from Active Cart (Atomic Stock Reservation)
```bash
curl -X POST "http://localhost:8080/api/v1/checkout/create-order" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "shippingAddress": {
      "fullName": "Rahul Sharma",
      "addressLine1": "Flat 402, Lotus Towers, 100 Feet Road",
      "city": "Bengaluru",
      "state": "Karnataka",
      "postalCode": "560038",
      "country": "IN",
      "phone": "+91 98765 43210"
    },
    "couponCode": "CRUNCH20"
  }'
```

#### Payment Gateway Callback / Webhook Simulation
```bash
curl -X POST "http://localhost:8080/api/v1/checkout/payment-callback" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "ORDER_UUID",
    "paymentReference": "pay_rzp_mock_12345678",
    "status": "SUCCESS"
  }'
```

#### Customer Order History
```bash
curl -X GET "http://localhost:8080/api/v1/account/orders?page=0&size=10" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H "Accept: application/json"
```

#### Customer Order Details
```bash
curl -X GET "http://localhost:8080/api/v1/account/orders/MC-2026-XXXXX" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H "Accept: application/json"
```

---

### 9.4 Admin Management Ingress (ROLE_TENANT_ADMIN)

#### Admin: List Orders with Status Filter
```bash
curl -X GET "http://localhost:8080/api/v1/admin/orders?status=PAID&page=0&size=20" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Accept: application/json"
```

#### Admin: Update Order Status
```bash
curl -X PUT "http://localhost:8080/api/v1/admin/orders/ORDER_UUID/status" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "status": "SHIPPED"
  }'
```

#### Admin: List Promotions
```bash
curl -X GET "http://localhost:8080/api/v1/admin/promotions" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Accept: application/json"
```

#### Admin: Create Promotion
```bash
curl -X POST "http://localhost:8080/api/v1/admin/promotions" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "code": "FESTIVE30",
    "description": "Flat ₹30 off on festive snacking",
    "discountType": "FLAT",
    "discountValue": 30.00,
    "minimumOrderAmount": 250.00,
    "validFrom": "2026-10-01T00:00:00Z",
    "validTo": "2026-12-31T23:59:59Z",
    "isActive": true
  }'
```

---

## 10. Phase 6: Pluggable Carrier Logistics, Self-Delivery Engine & Real-Time Tracking

### 10.1 Public Storefront Tracking Ingress (Anonymous & Authenticated)

#### Track Package Timeline by Order Number
```bash
curl -X GET "http://localhost:8080/api/v1/fulfillment/track/MC-2026-XXXXX" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Accept: application/json"
```

#### Track Package Timeline by AWB / Tracking Number
```bash
curl -X GET "http://localhost:8080/api/v1/fulfillment/track/awb/SELF-SHP-12345" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Accept: application/json"
```

---

### 10.2 Admin Logistics & Fulfillment Ingress (ROLE_TENANT_ADMIN)

#### Admin: List Configured Logistics Carriers
```bash
curl -X GET "http://localhost:8080/api/v1/admin/fulfillment/carriers" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Accept: application/json"
```

#### Admin: Book Shipment with Self-Fleet (Generates Verification OTP)
```bash
curl -X POST "http://localhost:8080/api/v1/admin/fulfillment/shipments" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "ORDER_UUID",
    "carrierType": "SELF_FLEET",
    "assignedRiderName": "Ramesh Singh",
    "assignedRiderPhone": "+91 98765 11223",
    "totalWeightGrams": 400,
    "volumetricWeightGrams": 400
  }'
```

#### Admin: Book Shipment with 3PL Courier (Delhivery / Blue Dart)
```bash
curl -X POST "http://localhost:8080/api/v1/admin/fulfillment/shipments" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "ORDER_UUID",
    "carrierType": "DELHIVERY",
    "totalWeightGrams": 500,
    "volumetricWeightGrams": 500
  }'
```

#### Admin: Update Shipment Status (Dispatch / Out for Delivery)
```bash
curl -X PUT "http://localhost:8080/api/v1/admin/fulfillment/shipments/SHIPMENT_UUID/status" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "newStatus": "OUT_FOR_DELIVERY",
    "locationHub": "PATNA_CENTRAL_HUB",
    "statusDescription": "Package out for delivery with associate"
  }'
```

#### Admin: Mark Delivered with Delivery Verification OTP
```bash
curl -X PUT "http://localhost:8080/api/v1/admin/fulfillment/shipments/SHIPMENT_UUID/status" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "newStatus": "DELIVERED",
    "locationHub": "CUSTOMER_DOORSTEP",
    "statusDescription": "Delivered successfully after physical OTP verification",
    "deliveryOtp": "549123"
  }'
```

#### Admin: Cancel Shipment
```bash
curl -X POST "http://localhost:8080/api/v1/admin/fulfillment/shipments/SHIPMENT_UUID/cancel" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Accept: application/json"
```

#### Admin: Configure Carrier Settings and Credentials
```bash
curl -X PUT "http://localhost:8080/api/v1/admin/fulfillment/carriers/SELF_FLEET" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "isEnabled": true,
    "settings": {
      "defaultHub": "PATNA_CENTRAL",
      "otpRequired": true,
      "maxRadiusKm": 30
    }
  }'
```

---

## 11. Commercial Analytics, Regulatory Audit & Notifications (Phase 7)

### Admin: Get Executive Dashboard KPIs (GMV, AOV, Top SKUs, Stock Risks)
```bash
curl -X GET "http://localhost:8080/api/v1/admin/analytics/kpis?startDate=2026-09-01&endDate=2026-10-05" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Accept: application/json"
```

**Expected Response (HTTP 200 OK):**
```json
{
  "success": true,
  "data": {
    "grossMerchandiseValue": 250000.00,
    "totalPaidOrders": 125,
    "averageOrderValue": 2000.00,
    "topSellingVariants": [
      {
        "variantId": "f1000000-0000-0000-0000-000000000001",
        "sku": "MKH-PERI-100G",
        "productName": "Roasted Makhana - Peri Peri 100g",
        "unitsSold": 340,
        "totalRevenue": 68000.00
      }
    ],
    "stockRiskItems": [
      {
        "variantId": "f1000000-0000-0000-0000-000000000001",
        "warehouseCode": "DEFAULT_WH",
        "availableStock": 5,
        "reorderThreshold": 10,
        "isStockDepleted": false
      }
    ]
  }
}
```

### Admin: Get Daily Sales Revenue and Order Count Trend
```bash
curl -X GET "http://localhost:8080/api/v1/admin/analytics/sales-trend?startDate=2026-10-01&endDate=2026-10-05" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Accept: application/json"
```

### Admin: Query Compliance Audit Logs (with Filtering & Pagination)
```bash
curl -X GET "http://localhost:8080/api/v1/admin/audit/logs?actionType=INVENTORY_ADJUST&page=0&size=20" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Accept: application/json"
```

**Expected Response (HTTP 200 OK):**
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": "8b4d6e28-712f-47f8-8df2-c87c8b3bfc30",
        "actorId": "d221f308-d020-482f-9c69-2122f21734de",
        "actorEmail": "admin@mitocrunch.com",
        "actorRole": "ROLE_TENANT_ADMIN",
        "actionType": "INVENTORY_ADJUST",
        "entityType": "INVENTORY",
        "entityId": "17df119e-95b2-42e6-bcd8-5aea93b27d46",
        "ipAddress": "192.168.1.100",
        "detailsBefore": { "availableStock": 100 },
        "detailsAfter": { "availableStock": 150 },
        "createdAt": "2026-10-05T19:55:54.896Z"
      }
    ],
    "page": {
      "size": 20,
      "number": 0,
      "totalElements": 1,
      "totalPages": 1
    }
  }
}
```

---

## Phase 9: Financial Core, Customer Wallet & Payment Gateway

### 1. Get Customer Wallet Balance
```bash
curl -X GET "http://localhost:8080/api/v1/wallet/balance" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer <CUSTOMER_JWT_TOKEN>" \
  -H "Accept: application/json"
```

### 2. Get Wallet Transaction Ledger
```bash
curl -X GET "http://localhost:8080/api/v1/wallet/transactions?page=0&size=20" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer <CUSTOMER_JWT_TOKEN>" \
  -H "Accept: application/json"
```

### 3. Admin Manual Wallet Adjustment
```bash
curl -X POST "http://localhost:8080/api/v1/admin/wallet/adjust" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer <TENANT_ADMIN_JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "customerProfileId": "cc6d58ae-0280-4a88-bf1b-4bf6cc1bc9c7",
    "transactionType": "CREDIT",
    "amount": 100.00,
    "category": "CUSTOMER_SUPPORT_GOODWILL",
    "referenceId": "TICKET-1049",
    "reason": "Goodwill compensation for delayed fulfillment"
  }'
```

### 4. Initialize Payment Transaction
```bash
curl -X POST "http://localhost:8080/api/v1/payments/initialize" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "9f8b6123-8f7a-4acf-bfcf-dea623d00405",
    "amount": 525.80,
    "currency": "INR"
  }'
```

### 5. Inbound Razorpay Webhook Settlement
```bash
curl -X POST "http://localhost:8080/api/v1/payments/webhook/razorpay" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "X-Razorpay-Signature: <CALCULATED_HMAC_SHA256>" \
  -H "Content-Type: application/json" \
  -d '{
    "event": "payment.captured",
    "order_id": "order_rzp_mock_123456",
    "payment_id": "pay_mock_789101"
  }'
```

### 6. Checkout with Loyalty Coins Redemption
```bash
curl -X POST "http://localhost:8080/api/v1/checkout/create-order" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "X-Cart-ID: <STORED_CART_UUID>" \
  -H "Authorization: Bearer <CUSTOMER_JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "shippingAddress": {
      "line1": "Flat 402, Lotus Towers",
      "city": "Patna",
      "state": "Bihar",
      "pincode": "800001"
    },
    "couponCode": "CRUNCH20",
    "coinsToRedeem": 100.00
  }'
```


---

## PHASE 10: RETURNS, REVERSE LOGISTICS & CUSTOMER SUPPORT HELPDESK

### 1. Initiate Customer Return Request
```bash
curl -X POST "http://localhost:8080/api/v1/returns/request" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer <CUSTOMER_JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "<DELIVERED_ORDER_UUID>",
    "reasonCategory": "DEFECTIVE_PRODUCT",
    "customerNotes": "Package damaged upon delivery and pouch torn.",
    "proofMediaUrls": [
      "https://cdn.mitocrunch.com/returns/damage_proof_1.jpg"
    ],
    "items": [
      {
        "orderItemId": "<ORDER_ITEM_UUID>",
        "quantity": 2
      }
    ]
  }'
```

### 2. View Customer Return Claims
```bash
curl -X GET "http://localhost:8080/api/v1/returns/my-requests?page=0&size=20" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer <CUSTOMER_JWT_TOKEN>"
```

### 3. Get Return Claim Details
```bash
curl -X GET "http://localhost:8080/api/v1/returns/<RETURN_REQUEST_UUID>" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer <CUSTOMER_JWT_TOKEN>"
```

### 4. Admin: List All Tenant Returns (Filterable)
```bash
curl -X GET "http://localhost:8080/api/v1/admin/returns?status=REQUESTED&page=0&size=20" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer <ADMIN_JWT_TOKEN>"
```

### 5. Admin: Approve Return Claim
```bash
curl -X PUT "http://localhost:8080/api/v1/admin/returns/<RETURN_REQUEST_UUID>/approve" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer <ADMIN_JWT_TOKEN>"
```

### 6. Admin: Submit QC Evaluation (PASS/FAIL -> Auto Restock & Refund)
```bash
curl -X POST "http://localhost:8080/api/v1/admin/returns/<RETURN_REQUEST_UUID>/qc-submit" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer <ADMIN_JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "passed": true,
    "qcNotes": "Item returned sealed in original package. Inspection passed.",
    "refundMode": "WALLET"
  }'
```

### 7. Customer: Create Support Ticket
```bash
curl -X POST "http://localhost:8080/api/v1/support/tickets" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer <CUSTOMER_JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "<OPTIONAL_ORDER_UUID>",
    "category": "ORDER_DELIVERY_ISSUE",
    "subject": "Delay in delivery slot dispatch",
    "priority": "HIGH",
    "message": "My scheduled delivery slot has passed and no courier update has arrived.",
    "attachmentUrls": []
  }'
```

### 8. Customer: List Tickets
```bash
curl -X GET "http://localhost:8080/api/v1/support/tickets?page=0&size=20" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer <CUSTOMER_JWT_TOKEN>"
```

### 9. Customer: View Ticket Details & Conversation History
```bash
curl -X GET "http://localhost:8080/api/v1/support/tickets/<TICKET_UUID_OR_NUMBER>" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer <CUSTOMER_JWT_TOKEN>"
```

### 10. Customer: Reply To Support Ticket
```bash
curl -X POST "http://localhost:8080/api/v1/support/tickets/<TICKET_UUID>/messages" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer <CUSTOMER_JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Thank you for the update, courier has contacted me.",
    "attachmentUrls": []
  }'
```

### 11. Admin: View All Tenant Support Tickets
```bash
curl -X GET "http://localhost:8080/api/v1/admin/support/tickets?status=OPEN&page=0&size=20" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer <ADMIN_JWT_TOKEN>"
```

### 12. Admin: Update Ticket Status
```bash
curl -X PUT "http://localhost:8080/api/v1/admin/support/tickets/<TICKET_UUID>/status" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer <ADMIN_JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "status": "RESOLVED"
  }'
```

### 13. Admin: Agent Reply To Ticket
```bash
curl -X POST "http://localhost:8080/api/v1/admin/support/tickets/<TICKET_UUID>/messages" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer <ADMIN_JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Our logistics coordinator has expedited your delivery.",
    "attachmentUrls": []
  }'
```

---

## Phase 11: B2B Wholesale Portal, HoReCa Engine & Credit Term Invoicing

### 1. Customer: Register B2B Partner
```bash
curl -X POST "http://localhost:8080/api/v1/b2b/register-partner" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer <CUSTOMER_JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "companyLegalName": "Grand Hyatt Patna Hospitality Pvt Ltd",
    "tradeName": "Grand Hyatt",
    "gstin": "10ABCDE1234F1Z5",
    "fssaiLicenseNumber": "FSSAI-11223344556677",
    "billingAddress": {
      "addressLine1": "Fraser Road",
      "city": "Patna",
      "state": "Bihar",
      "postalCode": "800001"
    }
  }'
```

### 2. Customer: Get B2B Profile & Credit Line
```bash
curl -X GET "http://localhost:8080/api/v1/b2b/profile" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer <CUSTOMER_JWT_TOKEN>"
```

### 3. Customer: View Wholesale Tiered Pricing
```bash
curl -X GET "http://localhost:8080/api/v1/b2b/pricing?variantId=<VARIANT_UUID>" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer <CUSTOMER_JWT_TOKEN>"
```

### 4. Customer: Place B2B Bulk Order (Net-30 / Net-60 / Prepaid)
```bash
curl -X POST "http://localhost:8080/api/v1/b2b/bulk-orders" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer <CUSTOMER_JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "items": [
      {
        "variantId": "<VARIANT_UUID>",
        "quantity": 100
      }
    ],
    "paymentTerms": "NET_30",
    "notes": "Bulk supply for restaurant pantry"
  }'
```

### 5. Customer: View Tax Invoices
```bash
curl -X GET "http://localhost:8080/api/v1/b2b/invoices?page=0&size=20" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer <CUSTOMER_JWT_TOKEN>"
```

### 6. Customer: Settle Invoice & Restore Credit
```bash
curl -X POST "http://localhost:8080/api/v1/b2b/invoices/<INVOICE_UUID>/pay" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer <CUSTOMER_JWT_TOKEN>"
```

### 7. Customer: View Credit Ledger Audit Trail
```bash
curl -X GET "http://localhost:8080/api/v1/b2b/credit-ledger" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer <CUSTOMER_JWT_TOKEN>"
```

### 8. Admin: List B2B Partners
```bash
curl -X GET "http://localhost:8080/api/v1/admin/b2b/partners?status=PENDING_VERIFICATION&page=0&size=20" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer <ADMIN_JWT_TOKEN>"
```

### 9. Admin: Verify Partner, Set Credit Limit & Terms
```bash
curl -X PUT "http://localhost:8080/api/v1/admin/b2b/partners/<PARTNER_UUID>/verify" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer <ADMIN_JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "verificationStatus": "VERIFIED",
    "creditLimit": 500000.00,
    "paymentTermsDays": 30,
    "notes": "Risk audit verified successfully"
  }'
```

### 10. Admin: Configure Volume Price Tier
```bash
curl -X POST "http://localhost:8080/api/v1/admin/b2b/pricing-tiers" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer <ADMIN_JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "variantId": "<VARIANT_UUID>",
    "minQuantity": 50,
    "wholesaleUnitPrice": 95.00,
    "currencyCode": "INR",
    "isActive": true
  }'
```
---

## Phase 12: Production Infrastructure, Container Health Probes & Gateway Rate Limiting

### 1. Actuator: General Application Health Check
```bash
curl -X GET "http://localhost:8080/actuator/health" \
  -H "Accept: application/json"
```

**Expected Response (HTTP 200 OK):**
```json
{
  "status": "UP"
}
```

### 2. Actuator: Kubernetes Liveness Probe
```bash
curl -X GET "http://localhost:8080/actuator/health/liveness" \
  -H "Accept: application/json"
```

**Expected Response (HTTP 200 OK):**
```json
{
  "status": "UP"
}
```

### 3. Actuator: Kubernetes Readiness Probe
```bash
curl -X GET "http://localhost:8080/actuator/health/readiness" \
  -H "Accept: application/json"
```

**Expected Response (HTTP 200 OK):**
```json
{
  "status": "UP"
}
```

### 4. Gateway: Rate-Limited Auth Login Burst
```bash
curl -X POST "http://localhost:8080/api/v1/auth/login" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "customer@mitocrunch.com",
    "password": "Password123!"
  }'
```

**Rate-Limit Success Headers (HTTP 200 OK):**
```
X-RateLimit-Limit: 10
X-RateLimit-Remaining: 9
```

**Rate-Limit Exceeded Response (HTTP 429 Too Many Requests):**
```http
HTTP/1.1 429 Too Many Requests
Content-Type: application/json;charset=UTF-8
X-RateLimit-Limit: 10
X-RateLimit-Remaining: 0
Retry-After: 60
```
```json
{
  "success": false,
  "error": {
    "code": "GATEWAY_4290",
    "message": "Too many requests. Rate limit exceeded"
  }
}
```

### 5. Edge Nginx: Subdomain Tenant Resolution (mitocrunch.maito.io)
```bash
curl -X GET "http://mitocrunch.maito.io/api/v1/catalog/products" \
  -H "Host: mitocrunch.maito.io" \
  -H "Accept: application/json"
```

### 6. Edge Nginx: Fallback Default Tenant Resolution
```bash
curl -X GET "http://localhost/api/v1/catalog/products" \
  -H "Accept: application/json"
```

---

# SECTION 13: OBSERVABILITY, METRICS & DISTRIBUTED TRACING (PHASE 13)

### 1. Scrape Prometheus Metrics (Internal / Admin)
```bash
curl -X GET "http://localhost:8080/actuator/prometheus" \
  -H "Accept: text/plain"
```

**Response (Sample OpenMetrics):**
```
# HELP http_server_requests_seconds Duration of HTTP server request handling
# TYPE http_server_requests_seconds summary
http_server_requests_seconds_count{application="maito-backend-monolith",error="none",exception="none",method="GET",outcome="SUCCESS",status="200",tenant="mito_crunch",uri="/api/v1/catalog/products"} 12.0
http_server_requests_seconds_sum{application="maito-backend-monolith",error="none",exception="none",method="GET",outcome="SUCCESS",status="200",tenant="mito_crunch",uri="/api/v1/catalog/products"} 0.142
```

### 2. Query Specific HTTP Request Metric
```bash
curl -X GET "http://localhost:8080/actuator/metrics/http.server.requests" \
  -H "Accept: application/json"
```

### 3. Query Custom Business Metrics (Domain Counters & Gauges)
```bash
# Order Creation Counter
curl -X GET "http://localhost:8080/actuator/metrics/maito.orders.created" \
  -H "Accept: application/json"

# GMV Revenue Total
curl -X GET "http://localhost:8080/actuator/metrics/maito.gmv.revenue" \
  -H "Accept: application/json"

# Rate Limit Rejections Total
curl -X GET "http://localhost:8080/actuator/metrics/maito.rate.limit.rejections" \
  -H "Accept: application/json"
```

### 4. Query HikariCP Database Pool Metrics
```bash
curl -X GET "http://localhost:8080/actuator/metrics/hikaricp.connections.active?tag=pool:db_mitocrunch" \
  -H "Accept: application/json"
```

### 5. Multi-Tenant Tagged Request Verification
```bash
# Request tagged for Mito Crunch
curl -X GET "http://localhost:8080/api/v1/catalog/products" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Accept: application/json"

# Request tagged for Vijiyasolar
curl -X GET "http://localhost:8080/api/v1/catalog/products" \
  -H "X-Tenant-ID: vijiyasolar" \
  -H "Accept: application/json"
```

### 6. Health Probes (Public)
```bash
curl -X GET "http://localhost:8080/actuator/health/readiness"
curl -X GET "http://localhost:8080/actuator/health/liveness"
```


---

# SECTION 14: 3RD-PARTY LIVE INTEGRATIONS & WEBHOOKS (PHASE 14)

### 1. Initialize Stripe Payment Intent
```bash
curl -X POST "http://localhost:8080/api/v1/payments/initialize" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "b182cb12-88ec-4ee3-9e4f-2df302919fa3",
    "amount": 999.00,
    "currency": "INR",
    "gatewayProvider": "STRIPE"
  }'
```

### 2. Inbound Stripe Webhook Settlement
```bash
curl -X POST "http://localhost:8080/api/v1/payments/webhook/stripe" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Stripe-Signature: t=1791337506,v1=a1b2c3d4e5f6..." \
  -H "Content-Type: application/json" \
  -d '{
    "id": "evt_stripe_12345",
    "type": "payment_intent.succeeded",
    "data": {
      "object": {
        "id": "pi_stripe_12345",
        "amount": 99900,
        "currency": "inr",
        "metadata": {
          "gatewayOrderId": "pi_stripe_12345"
        }
      }
    }
  }'
```

### 3. Inbound Delhivery Fulfillment Webhook
```bash
curl -X POST "http://localhost:8080/api/v1/fulfillment/webhooks/delhivery" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "X-Delhivery-Signature: 799d2ca2cde39..." \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "evt_dlv_998877",
    "waybill": "DLV-987654-IN",
    "status": "In Transit",
    "location": "PATNA_CENTRAL_HUB",
    "remarks": "Package arrived at regional sorting facility"
  }'
```

### 4. Inbound Shiprocket Fulfillment Webhook
```bash
curl -X POST "http://localhost:8080/api/v1/fulfillment/webhooks/shiprocket" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "X-Webhook-Signature: f0e1d2c3b4a5..." \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "evt_sr_554433",
    "awb": "SR-112233-AGG",
    "current_status": "DELIVERED",
    "location": "PATNA_DOORSTEP"
  }'
```

---

## SECTION 15: ELASTICSEARCH FULL-TEXT SEARCH & FACETED DISCOVERY ENGINE

### 1. Storefront Product Search with Typo Tolerance & Facets
```bash
curl -X GET "http://localhost:8080/api/v1/search/products?q=pari+peri&page=0&size=10&sort=relevance" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Accept: application/json"
```

### 2. Search-as-You-Type Autocomplete Suggestions
```bash
curl -X GET "http://localhost:8080/api/v1/search/suggest?q=peri" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Accept: application/json"
```

### 3. Faceted Filter Query (Brand, Category & Price Range)
```bash
curl -X GET "http://localhost:8080/api/v1/search/products?category=roasted-makhana&brand=Mito%20Crunch&minPrice=100&maxPrice=200&inStock=true&sort=price_asc" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Accept: application/json"
```

### 4. Admin Batch Re-Indexing Catalog Trigger
```bash
curl -X POST "http://localhost:8080/api/v1/admin/search/reindex" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer <TENANT_ADMIN_JWT>" \
  -H "Content-Type: application/json"
```


---

## SECTION 16: KUBERNETES INGRESS & MULTI-TENANT WILDCARD ROUTING

### 1. Root Domain Public Health & Platform Info
```bash
curl -X GET "https://maito.io/actuator/info" \
  -H "Accept: application/json"
```

### 2. Multi-Tenant Storefront Catalog Query via Wildcard Subdomain
```bash
curl -X GET "https://mito-crunch.maito.io/api/v1/search/products?q=peri+peri" \
  -H "Accept: application/json"
```

### 3. Tenant Actuator Readiness Probe via Ingress
```bash
curl -X GET "https://mito-crunch.maito.io/actuator/health/readiness" \
  -H "Accept: application/json"
```

### 4. Admin API Endpoint via Dedicated Tenant Subdomain
```bash
curl -X GET "https://mito-crunch.maito.io/api/v1/admin/orders" \
  -H "Authorization: Bearer <TENANT_ADMIN_JWT>" \
  -H "Accept: application/json"
```

### 5. Ingress SSL/TLS Certificate Verification (SNI Inspection)
```bash
curl -Iv --resolve "mito-crunch.maito.io:443:127.0.0.1" "https://mito-crunch.maito.io/actuator/health/liveness"
```


---

## SECTION 17: CI/CD PIPELINE & DEPLOYMENT HEALTH VERIFICATION

### 1. Trigger Manual Deployment via GitHub Actions REST API
```bash
curl -X POST "https://api.github.com/repos/MITO-saas/maito-backend-monolith/actions/workflows/cd-deploy-helm.yml/dispatches" \
  -H "Authorization: Bearer <GITHUB_PAT>" \
  -H "Accept: application/vnd.github.v3+json" \
  -H "Content-Type: application/json" \
  -d '{
    "ref": "uat",
    "inputs": {
      "environment": "staging"
    }
  }'
```

### 2. Inspect Published Image Manifest in GitHub Container Registry
```bash
curl -X GET "https://ghcr.io/v2/mito-saas/maito-backend-monolith/tags/list" \
  -H "Authorization: Bearer <GHCR_TOKEN>" \
  -H "Accept: application/json"
```

### 3. Verify Staging Health Post-Rollout
```bash
curl -X GET "https://staging.maito.io/actuator/health/readiness" \
  -H "Accept: application/json"
```

### 4. Verify Production High-Availability Cluster State Post-Rollout
```bash
curl -X GET "https://maito.io/actuator/info" \
  -H "Accept: application/json"
```
