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
