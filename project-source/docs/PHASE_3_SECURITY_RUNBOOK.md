# Phase 3: Multi-Tenant Identity, User Profiles & Spring Security 6 RBAC Runbook

**Monorepo Baseline**: Java 21 LTS, Spring Boot 3.3.4, Spring Security 6.3+, PostgreSQL 16 (port 5432), Liquibase 4.27.0  
**Phase Baseline Tags**:  
- Phase 1: `v1.0.0-phase1-core` (SaaS Multi-Tenancy Core Foundation)  
- Phase 2: `v2.0.0-phase2-cms` (Headless CMS & SDUI Engine)  
- Phase 3: `v3.0.0-phase3-security` (Multi-Tenant Identity & RBAC Engine)  

---

## 1. Domain-Driven Design (DDD) & Module Isolation

Module boundaries are enforced with strict encapsulation. Cross-module interactions occur **exclusively** through public API interfaces and DTOs (`com.maito.<module>.api.*`). Direct coupling to another module's `internal.domain` or `internal.repository` is strictly prohibited.

```
                                  ┌───────────────────────────────────────────────┐
                                  │      Inbound HTTP Request (with X-Tenant-ID)   │
                                  └──────────────────────┬────────────────────────┘
                                                         │
                                                         ▼
                                          [ TenantResolutionFilter ]
                                         (Binds TenantContextHolder)
                                                         │
                                                         ▼
                                          [ JwtAuthenticationFilter ]
                                       (Validates HMAC-SHA256 Token)
                                        (Enforces Tenant Isolation)
                                                         │
                        ┌────────────────────────────────┼────────────────────────────────┐
                        ▼                                ▼                                ▼
         ┌──────────────────────────────┐ ┌──────────────────────────────┐ ┌──────────────────────────────┐
         │     com.maito.identity       │ │       com.maito.user         │ │       com.maito.auth         │
         │  (Master Control Plane)      │ │   (Tenant-Isolated Domain)   │ │ (Security Gatekeeper Engine) │
         ├──────────────────────────────┤ ├──────────────────────────────┤ ├──────────────────────────────┤
         │ • GlobalUser (Credentials)   │ │ • TenantUserProfile          │ │ • JwtTokenProvider           │
         │ • BCrypt (Strength 12)       │ │ • Dynamic Role & JSONB Perms │ │ • SecurityFilterChain        │
         │ • Failed Lockout (5 attempts)│ │ • TenantUserAddress          │ │ • UserPrincipal              │
         ├──────────────────────────────┤ ├──────────────────────────────┤ ├──────────────────────────────┤
         │ Target DB: db_global_master  │ │ Target DB: db_{tenant_slug}  │ │ Orchestrates Identity & User │
         └──────────────────────────────┘ └──────────────────────────────┘ └──────────────────────────────┘
```

### 1.1 Module: `com.maito.identity` (Master Control Plane)
- **Target DB**: Exclusively Master Control Plane DB (`maito_db` / `db_global_master`).
- **Entity**: `GlobalUser` extending `BaseAuditableEntity` (`id`, `email`, `password_hash`, `phone_number`, `account_status`, `failed_login_attempts`, `last_login_at`).
- **Public API**: `IdentityService` (`createIdentity`, `authenticate`, `findByEmail`, `findById`).
- **Security Logic**: Passwords hashed with BCrypt (strength 12). Account locks after 5 consecutive failed login attempts. Automatically executes transactions on Master DB via `MasterIdentityTxService`.

### 1.2 Module: `com.maito.user` (Tenant-Isolated Domain)
- **Target DB**: Exclusively tenant-dedicated databases (`db_{tenant_slug}`, e.g., `db_mitocrunch`).
- **Entities**: 
  - `TenantUserProfile`: (`id`, `global_user_id`, `first_name`, `last_name`, `role`, `permission_matrix` JSONB, `is_active`).
  - `TenantUserAddress`: (`id`, `profile_id`, `address_type`, `recipient_name`, `phone`, address lines, city, state, postal code, `is_default`).
- **Public API**: `UserService` (`createProfile`, `getProfileByGlobalUserId`, `getProfileById`, `getUserAddresses`, `addAddress`).

### 1.3 Module: `com.maito.auth` (Security & Gatekeeper Engine)
- **JWT Provider**: Issues HMAC-SHA256 signed JWTs with claims:
  - `sub`: Global User ID (UUID)
  - `tenantId`: Active tenant slug (e.g. `mito_crunch`)
  - `role`: Tenant-scoped role (e.g. `ROLE_TENANT_ADMIN`, `ROLE_TENANT_CUSTOMER`)
  - `permissions`: List of granular permissions (e.g. `["cms:manage", "catalog:manage"]`)
  - `profileId`: Tenant profile UUID
- **Token Lifecycles**:
  - **Access Token**: 15 minutes TTL (900 seconds)
  - **Refresh Token**: 7 days TTL (604,800 seconds)
- **Multi-Tenant Token Replay Protection**: `JwtAuthenticationFilter` validates that token's `tenantId` claim strictly matches `TenantContextHolder.getTenantId()`. If mismatched, requests are rejected with **HTTP 403 Forbidden** (`CROSS_TENANT_VIOLATION`).

---

## 2. Ingress Gates & Authorization Matrix

| Path Pattern | HTTP Method | Permitted Roles / Ingress Gate | Authentication |
| :--- | :--- | :--- | :--- |
| `/assets/**`, `/`, `/index.html` | GET | Public Storefront Assets | Anonymous |
| `/api/v1/cms/pages/**` | GET | Public Storefront SDUI Layouts | Anonymous |
| `/api/v1/health/**`, `/api/v1/help/**` | GET | Platform Health & Guidance | Anonymous |
| `/api/v1/auth/login` | POST | Credentials Verification & Token Issue | Anonymous |
| `/api/v1/auth/register` | POST | Customer Registration & Profile Creation | Anonymous |
| `/api/v1/auth/refresh` | POST | Token Refresh | Anonymous (Valid Refresh Token) |
| `/api/v1/auth/me` | GET | Security Context Resolution | Authenticated |
| `/api/v1/admin/**` | ANY | Tenant Admin Access | `ROLE_TENANT_ADMIN` |
| `/api/v1/account/**` | ANY | Customer Account Management | `ROLE_TENANT_CUSTOMER`, `ROLE_TENANT_ADMIN` |

---

## 3. Seeded Accounts for Mito Crunch

The application automatically seeds verified default accounts for Mito Crunch on startup:

1. **Tenant Admin**:
   - **Email**: `admin@mitocrunch.com`
   - **Password**: `CrunchAdmin@2026`
   - **Role**: `ROLE_TENANT_ADMIN`
   - **Permissions**: `["cms:manage", "catalog:manage", "orders:manage"]`
   - **Redirect Target**: `ADMIN_DASHBOARD`

2. **Customer**:
   - **Email**: `customer@mitocrunch.com`
   - **Password**: `Customer@2026`
   - **Role**: `ROLE_TENANT_CUSTOMER`
   - **Permissions**: `[]`
   - **Redirect Target**: `STOREFRONT`

---

## 4. End-to-End Verification & cURL Execution Guide

### 4.1 Authenticate as Tenant Admin
```bash
curl -X POST "http://localhost:8080/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: mito_crunch" \
  -d '{
    "email": "admin@mitocrunch.com",
    "password": "CrunchAdmin@2026"
  }'
```
**Expected Response (HTTP 200 OK):**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "expiresInSeconds": 900,
    "profile": {
      "role": "ROLE_TENANT_ADMIN",
      "permissions": ["cms:manage", "catalog:manage", "orders:manage"]
    },
    "redirectTarget": "ADMIN_DASHBOARD"
  }
}
```

---

### 4.2 Authenticate as Customer
```bash
curl -X POST "http://localhost:8080/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: mito_crunch" \
  -d '{
    "email": "customer@mitocrunch.com",
    "password": "Customer@2026"
  }'
```
**Expected Response (HTTP 200 OK):**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "expiresInSeconds": 900,
    "profile": {
      "role": "ROLE_TENANT_CUSTOMER",
      "permissions": []
    },
    "redirectTarget": "STOREFRONT"
  }
}
```

---

### 4.3 Verify RBAC Gate: Admin Endpoint Protection
1. **Unauthenticated access (No Token)**:
```bash
curl -i -X GET "http://localhost:8080/api/v1/admin/cms/pages" \
  -H "X-Tenant-ID: mito_crunch"
```
**Expected Status**: `HTTP 401 Unauthorized`

2. **Customer Token Accessing Admin Ingress (Privilege Escalation Prevention)**:
```bash
curl -i -X GET "http://localhost:8080/api/v1/admin/cms/pages" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer <CUSTOMER_ACCESS_TOKEN>"
```
**Expected Status**: `HTTP 403 Forbidden` (`ACCESS_DENIED`)

---

### 4.4 Add Customer Shipping Address
```bash
curl -X POST "http://localhost:8080/api/v1/account/addresses" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer <CUSTOMER_ACCESS_TOKEN>" \
  -d '{
    "addressType": "SHIPPING",
    "recipientName": "Mithila Crunchy Customer",
    "phone": "+919876543211",
    "addressLine1": "Flat 302, Lotus Residency",
    "addressLine2": "Frazer Road",
    "city": "Patna",
    "state": "Bihar",
    "postalCode": "800001",
    "countryCode": "IN",
    "isDefault": true
  }'
```
**Expected Response (HTTP 201 Created):**
```json
{
  "success": true,
  "data": {
    "addressType": "SHIPPING",
    "recipientName": "Mithila Crunchy Customer",
    "city": "Patna",
    "isDefault": true
  }
}
```

---

### 4.5 Cross-Tenant Token Replay Attack Verification
Attempting to use a token issued for `tenant_a` against `mito_crunch`:
```bash
curl -i -X GET "http://localhost:8080/api/v1/account/profile" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer <TOKEN_ISSUED_FOR_ANOTHER_TENANT>"
```
**Expected Status**: `HTTP 403 Forbidden` (`CROSS_TENANT_VIOLATION`)
