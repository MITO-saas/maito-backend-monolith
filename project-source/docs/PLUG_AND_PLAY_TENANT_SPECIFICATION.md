# Plug-and-Play Multi-Tenant Architecture Specification

## 1. Executive Summary & Architectural Philosophy
The `maito-backend-monolith` platform employs a comprehensive, runtime-dynamic **Plug-and-Play Multi-Tenant Architecture**. Rather than enforcing monolithic, statically hardcoded credentials or uniform platform-wide operational configurations, each tenant operates with complete data isolation, dedicated connection pools, isolated third-party gateway credentials, itemized tax configurations, customized fulfillment pipelines, and dynamic storefront branding.

The operational principle is **Zero-Downtime Tenant Autonomy**:
- Onboarding or modifying a tenant requires **zero server restarts or redeployments**.
- Tenant business operations (payments, fulfillment, catalog, taxes, communication) adapt dynamically to tenant-specific database entities and metadata.
- Third-party webhook callbacks are dispatched directly to the tenant's isolated ledger and cryptographic keys.

---

## 2. Multi-Tier Dynamic Isolation Model

### 2.1 Persistence Layer: Database-per-Tenant Routing
- **Routing Engine**: `DynamicTenantRoutingDataSource` backed by `HikariPoolManager`.
- **Master Control Plane**: `maito_db` stores global tenant definitions (`global_tenants`, `global_tenant_domains`).
- **Tenant Data Isolation**: Each tenant has an independent PostgreSQL database (e.g., `db_mitocrunch`, `db_everrites`).
- **Lazy Pool Provisioning**: Inbound requests dynamically provision and cache a dedicated HikariCP pool if not in memory.
- **Liquibase Migrations**: Master changelog executes `tenant-base-schema.xml` on newly provisioned databases, including `012-tenant-payment-config-schema.xml` for payment isolation.

### 2.2 Context Propagation
- **Servlet Filter**: `TenantResolutionFilter` resolves tenant identity from:
  1. Header: `X-Tenant-ID: {tenantId}`
  2. Inbound Domain: Host header mapping to `global_tenant_domains`
  3. Bypass rules for public webhook callbacks and health probes.
- **Thread Context**: `TenantContextHolder` (InheritableThreadLocal) carries the active `TenantContext` across service, repository, and transaction layers.

---

## 3. Dynamic Payments & Settlement Engine

### 3.1 Per-Tenant Gateway Configuration
Merchant gateway credentials are saved per tenant in the tenant's isolated database table `tenant_payment_configs`:
- Entity: `TenantPaymentConfig` (audited with `BaseAuditableEntity`, optimistic locking `version`)
- Supported Providers: `RAZORPAY`, `STRIPE`
- Secure Attributes: `key_id`, `secret_key`, `webhook_secret`, `merchant_account_id`, `is_test_mode`, `is_enabled`
- Cache/Lookup: `TenantPaymentConfigRepository.findByProviderAndIsEnabledTrue(provider)` resolved dynamically within the tenant's context.

### 3.2 Dynamic Webhook Ingress & Cryptographic Verification
Gateways hit tenant-specific webhook ingress routes:
- `POST /api/v1/payments/webhook/{tenantSlug}/razorpay` (Signature Header: `X-Razorpay-Signature`)
- `POST /api/v1/payments/webhook/{tenantSlug}/stripe` (Signature Header: `Stripe-Signature`)
- Cryptographic verification computes HMAC-SHA256 signature using the specific tenant's `webhook_secret`.
- Non-matching signatures yield `HTTP 400 Bad Request` (`PAYMENT_4001`).
- Verified settlements trigger idempotent order state transitions (`PENDING_PAYMENT` -> `PAID`).

---

## 4. Dynamic Logistics, Fulfillment & Automated Failover

### 4.1 Pluggable Carrier Adapters
Carrier implementations resolve credentials and hub endpoints directly from the dynamic shipment request:
- **Delhivery (`DelhiveryCarrierAdapter`)**: Reads `request.carrierCredentials().get("apiToken")` and `request.carrierSettings().get("baseUrl")` dynamically.
- **Shiprocket (`ShiprocketCarrierAdapter`)**: Authenticates using dynamic credentials from `request.carrierCredentials()`.
- **Self-Delivery (`SelfDeliveryCarrierAdapter`)**: Dispatches from `request.pickupLocation()`, dynamically populated from tenant warehouse or hub configuration (e.g., `PATNA_WEST_HUB`).

### 4.2 Automated Resilient Carrier Failover
In `FulfillmentServiceImpl`, carrier dispatch executes with automatic failover:
1. Primary carrier attempted based on tenant configuration or algorithm ranking.
2. If primary carrier booking fails (network, API limit, or credential error), the engine catches the exception, logs a warning, and automatically attempts secondary carrier fallback.
3. Consignment tracking, AWB, and label URLs are persisted upon successful dispatch.

---

## 5. Catalog, Itemized Product Taxes & Order Checkout

### 5.1 Variant-Level Tax Slabs & HSN Codes
Replaces fixed global GST with granular variant-level tax slabs:
- Schema: Added `tax_rate` (DECIMAL 5,4) and `hsn_code` (VARCHAR 32) to `catalog_product_variants`.
- Dynamic Itemized Computation: `OrderServiceImpl` computes each line item's tax:
  Line Tax = Line Total * Item Tax Rate
- Zero-tax items (exempt 0%) and standard items (e.g., 18% GST) coexist in the same order accurately.

### 5.2 Dynamic Commercial Policies (`StoreSettings`)
- **Minimum Order Amount**: Enforced during checkout via `StoreSettingsDto.getMinOrderAmount()`.
- **Free Shipping Threshold**: Dynamic shipping fee waiver via `StoreSettingsDto.getFreeShippingThreshold()`.
- **Default Warehouse Allocation**: Replaces hardcoded `"DEFAULT_WH"` with tenant-configured warehouse or `cmd.warehouseCode()`.

---

## 6. Distributed Auth & Multi-Channel Notifications

### 6.1 Redis-Backed Distributed OTP
- **Storage**: Key format `maito:tenant:{tenantId}:otp:{phone}` with 5-minute TTL.
- **Fail-Safe In-Memory Cache**: Resilient local memory fallback if Redis daemon is offline.
- **Sandbox Test Bypass**: Predictable code `123456` enabled exclusively in `local` / `test` profiles for automated integration tests.
- **Async SMS Dispatch**: Dispatches OTP via `SmsNotificationChannel` asynchronously through worker pool.

### 6.2 Dynamic Communication Profiles
- **Email Notification Channel**: `EmailNotificationChannel` reads `supportEmail` and `storeName` from tenant `StoreSettings`, applying custom sender headers (`From: "{storeName}" <{supportEmail}>`).

---

## 7. Dynamic Storefront & Runtime Re-hydration

### 7.1 Multi-Tier Tenant Resolution (`store.js`)
Resolution precedence order:
1. URL Query Parameter: `?tenant={slug}`
2. Subdomain: `{slug}.maito.io` (excluding `www`)
3. `localStorage`: `maito_tenant_id`
4. Default Fallback: `mito_crunch`

### 7.2 Dynamic Branding Re-hydration (`index.html`)
- On initial page load, `store.js` queries `GET /api/v1/store/settings` and `GET /api/v1/cms/theme`.
- CSS custom properties (`--brand-primary`, `--brand-secondary`, `--brand-accent`, typography) are dynamically injected into document root.
- Document title, favicon, hero banners, and support contact details re-hydrate without DOM flash.
