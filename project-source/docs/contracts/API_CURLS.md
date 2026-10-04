# Maito Multi-Tenant SaaS Platform - cURL Execution & Verification Guide

Base URL: `http://localhost:8080`  
Swagger UI: `http://localhost:8080/swagger-ui.html`  
OpenAPI 3 Spec: `http://localhost:8080/v3/api-docs`  

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
    "status": "ACTIVE",
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

## 3. Verify Multi-Tenant Dynamic Database Routing

### A. Route via `X-Tenant-ID` Header
```bash
curl -i -X GET "http://localhost:8080/api/v1/health" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Accept: application/json"
```
*Response Header:* `X-Tenant-ID: mito_crunch`  
*Routing:* Connection dispatched to dedicated HikariPool for `db_mitocrunch`.

### B. Route via Custom Domain `Host` Header
```bash
curl -i -X GET "http://localhost:8080/api/v1/health" \
  -H "Host: store.mitocrunch.com" \
  -H "Accept: application/json"
```
*Response Header:* `X-Tenant-ID: mito_crunch` (dynamically resolved from domain).

### C. Verify Inactive/Unknown Tenant Rejection (Security Gate)
```bash
curl -i -X GET "http://localhost:8080/api/v1/health" \
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