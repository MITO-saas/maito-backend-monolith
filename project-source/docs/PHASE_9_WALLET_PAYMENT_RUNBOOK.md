# Phase 9: Financial Core — Customer Wallet, Gamified Loyalty Coins & Real Payment Gateway Runbook

**Module Packages:** `com.maito.wallet`, `com.maito.payment`  
**Target Golden Release:** `v9.0.0-phase9-wallet`  
**Isolation Architecture:** Tenant-Isolated Database Schema (`wallets`, `wallet_transactions`, `payment_transactions`)  

---

## 1. Architectural Overview

Phase 9 establishes the mission-critical financial core of the platform:
- **Customer Financial Wallet (`com.maito.wallet`):** Double-entry audit ledger maintaining balances and immutable transactional histories for customer cashbacks, manual customer-support adjustments, refunds, and checkout coin redemptions.
- **Gamified Loyalty Coins Integration (`com.maito.order`):** Customers can redeem stored loyalty coins at checkout to reduce order payables. Concurrency-safe atomic balance checks and compensating rollback transactions protect against race conditions and checkout failure leaks.
- **Payment Gateway Architecture (`com.maito.payment`):** Supports multi-provider payment integrations (Razorpay, Stripe, Mock). Implements constant-time HMAC-SHA256 cryptographic signature validation on inbound webhook notifications to prevent payment forgery and tampering.

### Architectural Interaction Diagram

```
+---------------------------------------------------------------------------------------------------+
|                                      CLIENT / FRONTEND                                            |
|                                                                                                   |
|   +-----------------------+     +------------------------+     +------------------------------+   |
|   | Wallet Balance Badge  |     | "Redeem Coins" Input   |     | Razorpay Webhook Dispatch    |   |
|   | (Profile Dropdown)    |     | (Cart Drawer/Checkout) |     | (X-Razorpay-Signature)       |   |
|   +-----------+-----------+     +-----------+------------+     +--------------+---------------+   |
+---------------|-----------------------------|---------------------------------|-------------------+
                |                             |                                 |
                | GET /api/v1/wallet/balance  | POST /checkout/create-order     | POST /payments/webhook
                v                             v                                 v
+---------------------------------------------------------------------------------------------------+
|                                 SPRING BOOT MONOLITH RUNTIME                                      |
|                                                                                                   |
|   +-----------------------+     +------------------------+     +------------------------------+   |
|   | CustomerWallet        |     | StorefrontOrder        |     | PaymentWebhook               |   |
|   | Controller            |     | Controller             |     | Controller                   |   |
|   +-----------+-----------+     +-----------+------------+     +--------------+---------------+   |
|               |                             |                                 |                   |
|               v                             v                                 v                   |
|   +-----------------------+     +------------------------+     +------------------------------+   |
|   | WalletService         |     | OrderService           |     | PaymentGatewayService        |   |
|   | (Credit, Debit, Ledger|<--->| (Coins Redemption &    |<--->| (HMAC-SHA256 Constant-Time   |   |
|   |  Double-Entry Audit)  |     |  Compensating Rollback)|     |  Verification & Settlement)  |   |
|   +-----------+-----------+     +-----------+------------+     +--------------+---------------+   |
+---------------|-----------------------------|---------------------------------|-------------------+
                |                             |                                 |
                v                             v                                 v
+---------------------------------------------------------------------------------------------------+
|                              TENANT DATABASE (db_{tenant_slug})                                   |
|                                                                                                   |
|   - wallets (balance, check >= 0, unique profile_id)                                              |
|   - wallet_transactions (double-entry audit ledger, amount > 0, balance_after)                    |
|   - orders (coins_redeemed, discount_amount, total_amount)                                        |
|   - payment_transactions (gateway_order_id, gateway_payment_id, gateway_signature, status)        |
+---------------------------------------------------------------------------------------------------+
```

---

## 2. Invariants & Security Guarantees

1. **Non-Negative Balance Invariant:** The database enforces `CHECK (balance >= 0)` on table `wallets`. The application validates current balance prior to any debit execution.
2. **Double-Entry Ledger Integrity:** Every balance mutation produces an immutable record in `wallet_transactions` capturing `amount`, `transaction_type` (`CREDIT` or `DEBIT`), `category`, `reference_id`, `description`, and `balance_after`.
3. **Compensating Rollback Guarantee:** If an order creation fails after coins have been debited (e.g. inventory constraint, gateway fault), the checkout engine triggers an automated compensating credit refund (`REFUND`, `FAILED_ORDER`) restoring customer coins.
4. **Constant-Time HMAC-SHA256 Verification:** Signatures are computed using `HmacSHA256` and compared using `MessageDigest.isEqual()` to defend against timing attacks.
5. **Multi-Tenant Database Isolation:** All wallet and payment ledger tables reside strictly within tenant databases (`db_{tenant_slug}`), isolated per tenant.

---

## 3. Database Schema (`008-wallet-payment-schema.xml`)

### 3.1 `wallets`
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | UUID | PRIMARY KEY | Unique wallet ID |
| `customer_profile_id` | UUID | NOT NULL, UNIQUE, FK -> `tenant_user_profiles(id)` | Customer owner |
| `balance` | NUMERIC(12,2) | NOT NULL, DEFAULT 0.00, CHECK (balance >= 0) | Active balance |
| `currency_code` | VARCHAR(8) | NOT NULL, DEFAULT 'INR' | Currency identifier |
| `is_active` | BOOLEAN | NOT NULL, DEFAULT TRUE | Status flag |
| `version` | BIGINT | NOT NULL, DEFAULT 0 | Optimistic concurrency control |

### 3.2 `wallet_transactions`
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | UUID | PRIMARY KEY | Transaction ledger ID |
| `wallet_id` | UUID | NOT NULL, FK -> `wallets(id)` | Associated wallet |
| `transaction_type` | VARCHAR(32) | NOT NULL | `CREDIT`, `DEBIT` |
| `category` | VARCHAR(64) | NOT NULL | `ORDER_CASHBACK`, `CHECKOUT_REDEMPTION`, `MANUAL_ADJUSTMENT`, `REFUND` |
| `amount` | NUMERIC(12,2) | NOT NULL, CHECK (amount > 0) | Mutation amount |
| `balance_after` | NUMERIC(12,2) | NOT NULL | Snapshot balance after mutation |
| `reference_id` | VARCHAR(128) | NULLABLE | Order ID, ticket ID, or external ref |
| `description` | VARCHAR(255) | NOT NULL | Human-readable audit narrative |

### 3.3 `payment_transactions`
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | UUID | PRIMARY KEY | Payment transaction ID |
| `order_id` | UUID | NOT NULL, FK -> `orders(id)` | Order reference |
| `gateway_provider` | VARCHAR(32) | NOT NULL | `RAZORPAY`, `STRIPE`, `MOCK` |
| `gateway_order_id` | VARCHAR(128) | NOT NULL | Gateway-issued order ID |
| `gateway_payment_id` | VARCHAR(128) | NULLABLE | Gateway-issued payment ID |
| `gateway_signature` | VARCHAR(256) | NULLABLE | Inbound verification signature |
| `amount` | NUMERIC(12,2) | NOT NULL | Transaction value |
| `status` | VARCHAR(32) | NOT NULL | `INITIATED`, `SUCCESS`, `FAILED`, `REFUNDED` |

---

## 4. API Endpoints & Request Contracts

| Method | Path | Security / Role | Request Payload | Response |
|---|---|---|---|---|
| `GET` | `/api/v1/wallet/balance` | `ROLE_TENANT_CUSTOMER` | None | `WalletDto` with active balance |
| `GET` | `/api/v1/wallet/transactions` | `ROLE_TENANT_CUSTOMER` | Query params: `page`, `size` | `Page<WalletTransactionDto>` |
| `POST` | `/api/v1/admin/wallet/adjust` | `ROLE_TENANT_ADMIN` | `{"customerProfileId":"...","transactionType":"CREDIT","amount":50.00,"category":"BONUS","reason":"..."}` | `WalletDto` with updated balance |
| `POST` | `/api/v1/payments/initialize` | Public / Storefront | `{"orderId":"...","amount":525.80,"currency":"INR"}` | `PaymentInitResponse` with gateway order ID |
| `POST` | `/api/v1/payments/webhook/razorpay` | Public (HMAC Verified) | JSON webhook payload + `X-Razorpay-Signature` | `WebhookProcessResult` with settlement status |

---

## 5. Automated Verification Test Suite

- `WalletDoubleEntryIntegrationTest`: Proves initialization with zero balance, atomic credit/debit calculation, rejection of overdraft debits, and thread-safe balance consistency under 10 concurrent threads.
- `PaymentHmacSignatureVerificationTest`: Proves constant-time HMAC-SHA256 signature verification, rejection of tampered signatures (HTTP 400), and acceptance of valid signatures (HTTP 200).
- `CheckoutWithCoinsIntegrationTest`: Validates order placement with coins deduction, reduction in final order total, wallet balance debit, and rejection of orders when requested coins exceed wallet balance.
- `AdminWalletSecurityTest`: Proves that anonymous users (HTTP 401) and customer tokens (HTTP 403) are strictly forbidden from invoking administrative wallet adjustment endpoints.
