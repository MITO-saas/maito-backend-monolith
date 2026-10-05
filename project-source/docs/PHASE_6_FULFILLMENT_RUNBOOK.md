# Phase 6: Pluggable Carrier Logistics, Self-Delivery Engine & Real-Time Tracking Timeline

## 1. Architectural Overview & Tenant Isolation

Phase 6 introduces enterprise multi-carrier logistics, tenant-owned self-delivery fleet operations with delivery verification OTPs, and public real-time package tracking timelines on the **maito-backend-monolith**.

### 1.1 Strict Multi-Tenant Isolation
All Phase 6 logistics database tables exist **strictly within tenant-isolated databases** (`db_{tenant_slug}`, e.g., `db_mitocrunch` and `db_vijiyasolar`).
The master control plane (`maito_db`) contains **zero Phase 6 tables**, strictly preserving multi-tenant segregation.

```
                          Tenant Ingress Request
                                   │
                         [TenantResolutionFilter]
                                   │ (resolves X-Tenant-ID)
                         [TenantContextHolder]
                                   │
               ┌───────────────────┴───────────────────┐
               ▼                                       ▼
      [Tenant: mito_crunch]                   [Tenant: vijiyasolar]
         (db_mitocrunch)                         (db_vijiyasolar)
    ├── carrier_configurations              ├── carrier_configurations
    ├── shipments                           ├── shipments
    └── shipment_checkpoints                └── shipment_checkpoints
```

### 1.2 Liquibase Schema Integration
- **Changelog**: `src/main/resources/db/changelog/tenant/006-fulfillment-schema.xml`
- **Master Include**: Registered inside `src/main/resources/db/changelog/tenant/tenant-base-schema.xml`
- **Primary Keys**: 100% UUID generated via `gen_random_uuid()`.
- **Relational Integrity**:
  - `shipments.order_id` -> `orders(id) ON DELETE CASCADE`
  - `shipment_checkpoints.shipment_id` -> `shipments(id) ON DELETE CASCADE`
  - Unique constraint on `carrier_configurations.carrier_type`
  - Unique constraint on `shipments.shipment_number`
  - Indexes on `shipments.tracking_number`, `shipments.order_id`, `shipments.status`, `shipment_checkpoints.shipment_id`, `shipment_checkpoints.event_timestamp`

---

## 2. Pluggable Carrier Strategy Architecture

Logistics execution is completely decoupled from order management via the **Carrier Strategy Pattern**:

```
                          [FulfillmentService]
                                   │
                        [CarrierAdapterFactory]
                                   │
          ┌────────────────────────┼────────────────────────┬────────────────────────┐
          ▼                        ▼                        ▼                        ▼
[SelfDeliveryCarrierAdapter] [DelhiveryCarrierAdapter] [BlueDartCarrierAdapter] [ShiprocketCarrierAdapter]
   (SELF_FLEET: OTP Auth)     (DELHIVERY: 3PL AWB)      (BLUEDART: Air Waybill)  (SHIPROCKET: Aggregator)
```

### Built-in Carrier Adapters:
1. **`SelfDeliveryCarrierAdapter` (`SELF_FLEET`)**:
   - For tenant's internal delivery fleet/riders.
   - Generates cryptographically secure 6-digit Delivery Verification OTP.
   - Local tracking code (`SELF-SHP-...`) and local shipping label generation.
2. **`DelhiveryCarrierAdapter` (`DELHIVERY`)**:
   - Standard Indian 3PL courier with automated AWB generation (`DEL-...-IN`) and tracking hub updates.
3. **`BlueDartCarrierAdapter` (`BLUEDART`)**:
   - Enterprise express courier with electronic Air Waybill allocation (`BD-...99`).
4. **`ShiprocketCarrierAdapter` (`SHIPROCKET`)**:
   - Multi-carrier logistics aggregator routing (`SR-...-AGG`).

---

## 3. Self-Delivery OTP Verification & Security Barrier

To guarantee physical delivery integrity and eliminate fraud, `SELF_FLEET` shipments enforce OTP validation:

```
  [Order Manifested] ──► Generates 6-Digit OTP (e.g. 549123)
           │
           ▼
  [Assigned to Rider] (Ramesh Singh, +91 98765 11223)
           │
           ▼
  [Out for Delivery] (Checkpoint: PATNA_CENTRAL_HUB)
           │
           ▼
  [Customer Doorstep] ──► Customer shares OTP with Rider
           │
  PUT /api/v1/admin/fulfillment/shipments/:id/status
  { "newStatus": "DELIVERED", "deliveryOtp": "549123" }
           │
     ┌─────┴─────────────────────┐
     ▼                           ▼
[Invalid OTP]              [Valid OTP]
HTTP 400 Bad Request       Status: DELIVERED
VALIDATION_FAILED          Order Status: DELIVERED
                           delivered_at timestamp recorded
```

---

## 4. Step-by-Step E2E cURL Runbook

### Step 1: Admin Inspects Configured Carriers
```bash
curl -X GET "http://localhost:8080/api/v1/admin/fulfillment/carriers" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Accept: application/json"
```

### Step 2: Admin Books Self-Fleet Delivery for Paid Order
```bash
curl -X POST "http://localhost:8080/api/v1/admin/fulfillment/shipments" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "'$ORDER_ID'",
    "carrierType": "SELF_FLEET",
    "assignedRiderName": "Ramesh Singh",
    "assignedRiderPhone": "+91 98765 11223",
    "totalWeightGrams": 400,
    "volumetricWeightGrams": 400
  }'
```
*(Returns shipment details, tracking number `SELF-SHP-...`, and 6-digit `deliveryOtp`)*

### Step 3: Public / Anonymous Customer Real-Time Order Tracking
```bash
curl -X GET "http://localhost:8080/api/v1/fulfillment/track/'$ORDER_NUMBER'" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Accept: application/json"
```

### Step 4: Public Tracking by AWB / Tracking Number
```bash
curl -X GET "http://localhost:8080/api/v1/fulfillment/track/awb/'$TRACKING_NUMBER'" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Accept: application/json"
```

### Step 5: Admin Dispatches Shipment (Out for Delivery)
```bash
curl -X PUT "http://localhost:8080/api/v1/admin/fulfillment/shipments/'$SHIPMENT_ID'/status" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "newStatus": "OUT_FOR_DELIVERY",
    "locationHub": "PATNA_CENTRAL_HUB",
    "statusDescription": "Package out for delivery with associate Ramesh Singh"
  }'
```

### Step 6: Complete Delivery with Verification OTP
```bash
curl -X PUT "http://localhost:8080/api/v1/admin/fulfillment/shipments/'$SHIPMENT_ID'/status" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "newStatus": "DELIVERED",
    "locationHub": "CUSTOMER_DOORSTEP",
    "statusDescription": "Delivered successfully after physical OTP verification",
    "deliveryOtp": "'$DELIVERY_OTP'"
  }'
```

### Step 7: Admin Books 3PL Express Shipment (Delhivery / Blue Dart)
```bash
curl -X POST "http://localhost:8080/api/v1/admin/fulfillment/shipments" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "'$SECOND_ORDER_ID'",
    "carrierType": "DELHIVERY",
    "totalWeightGrams": 600,
    "volumetricWeightGrams": 600
  }'
```

---

## 5. Test Suite Certification Matrix

| Test Class | Focus Area | Executed Tests | Result |
|:---|:---|:---:|:---:|
| `CarrierAdapterFactoryIntegrationTest` | Factory Resolution, Self-Fleet OTP, 3PL AWB Generation | 3 | **PASS** |
| `FulfillmentShipmentLifecycleIntegrationTest` | Full Lifecycle: Manifest $\rightarrow$ Tracking $\rightarrow$ Out for delivery $\rightarrow$ OTP Auth $\rightarrow$ Delivered, 3PL Booking & Cancellation | 2 | **PASS** |
| `FulfillmentSecurityTest` | Public Tracking Ingress (200), Anonymous Admin Block (401), Customer Admin Block (403), Admin Dispatch (201) | 4 | **PASS** |
| **All Platform Modules (Phases 1-6)** | Full Monolith Test Suite | **87** | **PASS (0 failures, 0 errors, 0 skipped)** |
