# Phase 7: Commercial Analytics, Regulatory Audit Trail & Notification Communications Engine Runbook

**Module Packages:** `com.maito.analytics`, `com.maito.audit`, `com.maito.notification`  
**Version:** `7.0.0-phase7-analytics`  
**Isolation Architecture:** Tenant-Isolated Database Schema (`audit_logs`, `notification_logs`)  

---

## 1. Architectural Overview

Phase 7 delivers enterprise commercial intelligence, compliance audit trails, and multi-channel customer communications:
- **Commercial Analytics (`com.maito.analytics`):** Executive KPI dashboard calculations including Gross Merchandise Value (GMV), Total Paid Orders, Average Order Value (AOV), Top 5 Best-Selling Variants (by units and revenue), Stock Risk Monitor, and Daily Sales Trends.
- **Regulatory Audit Trail (`com.maito.audit`):** Immutable compliance activity ledger recording actor identity, role, action type, entity diffs (`details_before` and `details_after` JSONB), and client IP address.
- **Notification Engine (`com.maito.notification`):** Pluggable omnichannel dispatchers supporting Email, SMS, and WhatsApp with delivery status audit logging.

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                                    SPRING BOOT MONOLITH                                │
│                                                                                        │
│   ┌────────────────────────┐  ┌─────────────────────────┐  ┌───────────────────────┐   │
│   │   AdminAnalytics       │  │     AdminAudit          │  │ NotificationDispatch  │   │
│   │     Controller         │  │     Controller          │  │       Service         │   │
│   └───────────┬────────────┘  └────────────┬────────────┘  └───────────┬───────────┘   │
│               ▼                            ▼                           ▼               │
│   ┌────────────────────────┐  ┌─────────────────────────┐  ┌───────────────────────┐   │
│   │   AnalyticsService     │  │    AuditLogService      │  │ NotificationChannels  │   │
│   │ (GMV, AOV, Top SKUs)   │  │   (Compliance Logger)   │  │ (Email, SMS, WhatsApp)│   │
│   └───────────┬────────────┘  └────────────┬────────────┘  └───────────┬───────────┘   │
└───────────────┼────────────────────────────┼───────────────────────────┼───────────────┘
                │                            │                           │
                ▼                            ▼                           ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                        TENANT DATABASE (db_{tenant_slug})                              │
│   ┌─────────────────────┐      ┌────────────────────────┐     ┌─────────────────────┐  │
│   │ orders, order_items │      │      audit_logs        │     │  notification_logs  │  │
│   │  inventory_levels   │      │ (JSONB before/after)   │     │ (JSONB payload)     │  │
│   └─────────────────────┘      └────────────────────────┘     └─────────────────────┘  │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Analytics Rollup Queries & Calculation Formulas

### 2.1 Executive KPI Metrics
1. **Gross Merchandise Value (GMV):**
   ```sql
   SELECT COALESCE(SUM(total_amount), 0) AS gmv
   FROM orders
   WHERE order_status IN ('PAID', 'PROCESSING', 'SHIPPED', 'DELIVERED')
     AND created_at >= :startDate AND created_at < :endDate;
   ```
2. **Total Paid Orders:**
   ```sql
   SELECT COUNT(*) AS total_paid_orders
   FROM orders
   WHERE order_status IN ('PAID', 'PROCESSING', 'SHIPPED', 'DELIVERED')
     AND created_at >= :startDate AND created_at < :endDate;
   ```
3. **Average Order Value (AOV):**
   ```java
   BigDecimal aov = totalPaidOrders > 0
       ? gmv.divide(BigDecimal.valueOf(totalPaidOrders), 2, RoundingMode.HALF_UP)
       : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
   ```

4. **Top 5 Best-Selling Variants:**
   ```sql
   SELECT oi.variant_id, oi.sku_snapshot, oi.product_name_snapshot,
          SUM(oi.quantity) AS units_sold,
          SUM(oi.quantity * oi.unit_price) AS total_revenue
   FROM order_items oi
   JOIN orders o ON oi.order_id = o.id
   WHERE o.order_status IN ('PAID', 'PROCESSING', 'SHIPPED', 'DELIVERED')
     AND o.created_at >= :startDate AND o.created_at < :endDate
   GROUP BY oi.variant_id, oi.sku_snapshot, oi.product_name_snapshot
   ORDER BY units_sold DESC, total_revenue DESC
   LIMIT 5;
   ```

5. **Stock Risk Monitor:**
   ```sql
   SELECT variant_id, warehouse_code, available_stock, reorder_threshold,
          (available_stock <= 0) AS is_stock_depleted
   FROM inventory_levels
   WHERE available_stock <= reorder_threshold
   ORDER BY available_stock ASC;
   ```

---

## 3. Regulatory Audit Trail & Compliance Standards

1. **Immutability Invariant:** `audit_logs` records cannot be updated or deleted by normal application endpoints.
2. **Context Capture:**
   - `actor_id` & `actor_email`: Extracted from `UserPrincipal` authenticated token.
   - `actor_role`: `ROLE_TENANT_ADMIN` or `SYSTEM`.
   - `action_type`: Standard audit action codes (`INVENTORY_ADJUST`, `PROMOTION_CREATE`, `ORDER_CANCEL`, `CARRIER_CONFIG_UPDATE`).
   - `ip_address`: Extracted from HTTP request client IP.
   - `details_before` & `details_after`: Structured JSONB diff captures before and after mutation state.
3. **Retention Policy:** Standard regulatory retention requires keeping `audit_logs` indefinitely for SOC2/ISO27001 auditability.

---

## 4. Multi-Channel Notification Engine

| Channel | Handler Class | Template Codes | Destination Format |
|---|---|---|---|
| **EMAIL** | `EmailNotificationChannel` | `ORDER_CONFIRMED`, `ORDER_DISPATCHED` | RFC 5322 Email (`customer@domain.com`) |
| **SMS** | `SmsNotificationChannel` | `ORDER_DISPATCHED`, `DELIVERY_OTP` | E.164 Phone (`+919876543210`) |
| **WHATSAPP** | `WhatsAppNotificationChannel` | `DELIVERY_OTP`, `DELIVERY_UPDATE` | E.164 Phone (`+919876543210`) |

- **Asynchronous Execution:** `notificationDispatchService.dispatchAsync(message)` executes via `CompletableFuture` to prevent blocking request threads.
- **Audit Persistence:** Every notification attempt writes to `notification_logs` with `SENT` or `FAILED` status and a full JSONB payload snapshot.

---

## 5. Security & RBAC Configuration

- **Public Access:** None.
- **Admin Ingress (`ROLE_TENANT_ADMIN`):**
  * `GET /api/v1/admin/analytics/kpis`
  * `GET /api/v1/admin/analytics/sales-trend`
  * `GET /api/v1/admin/audit/logs`
- **Unauthorized / Customer Tokens:** Return HTTP 401 Unauthorized or HTTP 403 Forbidden.
