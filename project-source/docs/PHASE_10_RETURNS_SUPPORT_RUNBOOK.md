# Phase 10: Returns, Reverse Logistics & Customer Support Helpdesk Runbook

## 1. Executive Summary
Phase 10 delivers a tenant-isolated, automated **Reverse Logistics Engine** (`com.maito.returns`) and a **Customer Support Helpdesk Engine** (`com.maito.support`) on `maito-backend-monolith`. It enables customers to initiate returns on delivered orders with photographic proof, allows warehouse administrators to triage and inspect returned merchandise, automates atomic inventory restocking, seamlessly credits customer wallets with instant refunds, and facilitates two-way customer support ticket threading.

---

## 2. Reverse Logistics & Return State Machine

### 2.1 State Transitions
```
                +---------------+
                |   REQUESTED   |  (Customer initiates on DELIVERED order)
                +-------+-------+
                        |
                        v (Admin approves claim)
                +---------------+
                |   APPROVED    |
                +-------+-------+
                        |
                        v (Logistics pickup & warehouse inspection)
        +---------------+---------------+
        |                               |
        v (QC Passed)                   v (QC Failed)
+---------------+               +---------------+
|    SETTLED    |               |   REJECTED    |
+---------------+               +---------------+
  | - Auto-restock inventory      | - No restock
  | - Auto-credit wallet          | - No wallet credit
```

### 2.2 Return Business Rules
1. **Order Status Eligibility**:
   - Only orders with status `DELIVERED` are eligible for return. Attempting to return `PENDING_PAYMENT`, `PAID`, `PROCESSING`, `SHIPPED`, or `CANCELLED` orders immediately raises `BusinessException(ErrorCode.ORDER_NOT_DELIVERED)`.
2. **Customer Ownership**:
   - The order's `customerProfileId` must match the authenticated customer. Attempting to return another customer's order triggers `BusinessException(ErrorCode.ORDER_NOT_ELIGIBLE_FOR_RETURN)`.
3. **Item Line Quantity Validation**:
   - The quantity requested for return must be `> 0` and `<= purchased quantity` on the order item line.
4. **Automated Refund Calculation**:
   - Refund is calculated dynamically based on line unit prices: `refundAmount = sum(item.unitPrice * item.quantity)`.
5. **QC Pass Execution**:
   - Restocks items in `inventory_levels` for the variant at warehouse `DEFAULT_WH` via `InventoryService.adjustStock`.
   - Issues wallet credit for `refundAmount` via `WalletService.credit` with category `REFUND`.
   - Transitions state to `SETTLED` with timestamp recorded in `settled_at`.
6. **QC Fail Execution**:
   - Transitions state to `REJECTED`. 0 inventory adjustments made and 0 wallet credits issued.

---

## 3. Customer Support Helpdesk System

### 3.1 Helpdesk State Machine
```
   +----------+
   |   OPEN   |  (Initial state upon customer creation)
   +----+-----+
        |
        +-----------------------+
        |                       |
        v (Agent replies)       v (Customer replies)
+--------------------+   +---------------+
| WAITING_ON_CUSTOMER|   |  IN_PROGRESS  |
+---------+----------+   +-------+-------+
          |                      |
          +----------+-----------+
                     |
                     v (Admin overrides)
             +---------------+
             |   RESOLVED    |
             +-------+-------+
                     |
                     v
             +---------------+
             |    CLOSED     |
             +---------------+
```

### 3.2 Threading Rules
1. **Initial Message**:
   - Every support ticket is created with an initial `ticket_messages` row linked directly to the creator (`senderRole = CUSTOMER`).
2. **Turn-based State Switching**:
   - When an `AGENT` responds, the ticket status automatically switches to `WAITING_ON_CUSTOMER`.
   - When the `CUSTOMER` follows up, the ticket status automatically transitions to `IN_PROGRESS`.
3. **Admin Lifecycle Controls**:
   - Administrators can override status to `RESOLVED` or `CLOSED` via `PUT /api/v1/admin/support/tickets/{id}/status`.

---

## 4. Multi-Tenant Database Schema Isolation

All Phase 10 tables exist strictly in tenant databases (`db_{tenant_slug}`). Master database `db_global_master` is untouched.

### 4.1 Schema Tables
1. `return_requests`:
   - `id` (UUID PK), `return_number` (VARCHAR(64) UNIQUE), `order_id` (UUID FK orders), `customer_profile_id` (UUID FK tenant_user_profiles), `status` (VARCHAR(32)), `reason_category` (VARCHAR(64)), `customer_notes` (TEXT), `proof_media_urls` (JSONB), `qc_notes` (TEXT), `refund_amount` (NUMERIC(12,2)), `refund_mode` (VARCHAR(32)), `settled_at` (TIMESTAMPTZ), `created_at`, `updated_at`, `version`.
2. `return_items`:
   - `id` (UUID PK), `return_id` (UUID FK return_requests), `order_item_id` (UUID FK order_items), `variant_id` (UUID FK catalog_product_variants), `quantity` (INT), `unit_price` (NUMERIC(10,2)), `created_at`, `updated_at`, `version`.
3. `support_tickets`:
   - `id` (UUID PK), `ticket_number` (VARCHAR(64) UNIQUE), `customer_profile_id` (UUID FK tenant_user_profiles), `order_id` (UUID FK orders NULLABLE), `category` (VARCHAR(64)), `subject` (VARCHAR(255)), `priority` (VARCHAR(32)), `status` (VARCHAR(32)), `created_at`, `updated_at`, `version`.
4. `ticket_messages`:
   - `id` (UUID PK), `ticket_id` (UUID FK support_tickets), `sender_profile_id` (UUID), `sender_role` (VARCHAR(32)), `message` (TEXT), `attachment_urls` (JSONB), `created_at`, `updated_at`, `version`.

Liquibase Changelog:
- File: `src/main/resources/db/changelog/tenant/009-returns-support-schema.xml`
- Master Include: `src/main/resources/db/changelog/tenant/tenant-base-schema.xml`

---

## 5. Security & RBAC Access Ingress Matrix

| Endpoint | Method | Authority | Description |
| :--- | :--- | :--- | :--- |
| `/api/v1/returns/request` | `POST` | `ROLE_TENANT_CUSTOMER` | Initiate return claim on delivered order |
| `/api/v1/returns/my-requests` | `GET` | `ROLE_TENANT_CUSTOMER` | List customer's returns |
| `/api/v1/returns/{id}` | `GET` | `ROLE_TENANT_CUSTOMER` | View return claim details (customer-scoped) |
| `/api/v1/admin/returns` | `GET` | `ROLE_TENANT_ADMIN` | List all returns with status/order filters |
| `/api/v1/admin/returns/{id}/approve` | `PUT` | `ROLE_TENANT_ADMIN` | Approve return claim |
| `/api/v1/admin/returns/{id}/qc-submit` | `POST` | `ROLE_TENANT_ADMIN` | Submit QC review, trigger restock & refund |
| `/api/v1/support/tickets` | `POST` | `ROLE_TENANT_CUSTOMER` | Create new support ticket |
| `/api/v1/support/tickets` | `GET` | `ROLE_TENANT_CUSTOMER` | List customer tickets |
| `/api/v1/support/tickets/{id}` | `GET` | `ROLE_TENANT_CUSTOMER` | View ticket details and thread history |
| `/api/v1/support/tickets/{id}/messages` | `POST` | `ROLE_TENANT_CUSTOMER` | Add customer message to conversation |
| `/api/v1/admin/support/tickets` | `GET` | `ROLE_TENANT_ADMIN` | List all tenant support tickets |
| `/api/v1/admin/support/tickets/{id}/status` | `PUT` | `ROLE_TENANT_ADMIN` | Update ticket status (`RESOLVED`, `CLOSED`) |
| `/api/v1/admin/support/tickets/{id}/messages` | `POST` | `ROLE_TENANT_ADMIN` | Agent reply to customer ticket |

---

## 6. Regression Testing & Certification Verification

### 6.1 Test Execution Report
- JDK Version: **Eclipse Adoptium OpenJDK 21.0.12**
- Total Tests: **126**
- Failures: **0**
- Errors: **0**
- Skipped: **0**

### 6.2 Key Test Suites
1. `ReturnLifecycleIntegrationTest`:
   - Validates non-delivered orders reject returns with `ORDER_NOT_DELIVERED`.
   - Proves request creation -> approval -> QC pass automatically restocks inventory and credits wallet.
   - Proves failing QC transitions to `REJECTED` with zero stock or wallet modifications.
2. `SupportTicketIntegrationTest`:
   - Validates ticket creation with linked order context.
   - Proves bidirectional conversation threading (`AGENT` -> `WAITING_ON_CUSTOMER`, `CUSTOMER` -> `IN_PROGRESS`).
   - Proves administrator resolution.
3. `AdminReturnSecurityTest`:
   - Validates anonymous requests are rejected with HTTP 401.
   - Validates customers attempting admin operations are rejected with HTTP 403.
   - Validates tenant administrators successfully access admin endpoints with HTTP 200.