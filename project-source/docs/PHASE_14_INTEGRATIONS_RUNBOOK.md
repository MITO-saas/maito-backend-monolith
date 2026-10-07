# Phase 14: Resilient 3rd-Party Integrations Runbook

## 1. Executive Summary & Integration Architecture

Phase 14 establishes resilient, production-ready adapters for external SaaS providers across:
1. **Payments**: Razorpay (India/UPI/Cards) & Stripe (International Cards/SEPA).
2. **Logistics & 3PL Fulfillment**: Delhivery (Surface/Express) & Shiprocket (Multi-courier Aggregator).
3. **Communications**: Meta WhatsApp Cloud API, AWS SES / SMTP Email, and Twilio SMS.

All inbound webhooks enforce **cryptographic signature verification (HMAC-SHA256)**, **replay attack mitigation**, and **distributed idempotency deduplication (Redis key `webhook:processed:{eventId}` with 24h TTL)**. When third-party API credentials are not set, all adapters gracefully fall back to **sandbox mode**, ensuring continuous local development and zero test regressions.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       EXTERNAL 3RD-PARTY PROVIDERS                          │
│        (Razorpay, Stripe, Delhivery, Shiprocket, WhatsApp, Twilio, SES)     │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │ Inbound Webhooks
                                       ▼
                     ┌───────────────────────────────────┐
                     │          Nginx Gateway            │
                     │    (SSL Termination & Ingress)    │
                     └─────────────────┬─────────────────┘
                                       │
                                       ▼
                     ┌───────────────────────────────────┐
                     │     Spring Boot Monolith Core     │
                     │ ┌───────────────────────────────┐ │
                     │ │ Webhook Cryptographic Verifier│ │
                     │ │ (HMAC-SHA256 & Replay Defense)│ │
                     │ └───────────────┬───────────────┘ │
                     │                 ▼                 │
                     │ ┌───────────────────────────────┐ │
                     │ │ WebhookIdempotencyService     │ │
                     │ │ (Redis Key 24h TTL / Memory)  │ │
                     │ └───────────────┬───────────────┘ │
                     │                 ▼                 │
                     │ ┌───────────────────────────────┐ │
                     │ │ State Transition & Handlers   │ │
                     │ │ (Payment / Order / Shipment)  │ │
                     │ └───────────────┬───────────────┘ │
                     │                 ▼                 │
                     │ ┌───────────────────────────────┐ │
                     │ │ NotificationDispatchService   │ │
                     │ │ (notification-worker- Pool)   │ │
                     │ └───────────────────────────────┘ │
                     └───────────────────────────────────┘
```

---

## 2. Cryptographic Security & Replay Attack Defense

### A. Razorpay Signature Verification
- **Header**: `X-Razorpay-Signature`
- **Algorithm**: `HMAC-SHA256(rawBody, webhookSecret)`
- **Constant-Time Comparison**: `MessageDigest.isEqual` prevents timing attacks.

### B. Stripe Signature & Replay Attack Mitigation
- **Header**: `Stripe-Signature` (e.g. `t=1791337506,v1=abc123...`)
- **Timestamp Tolerance**: Enforces `|currentEpoch - timestamp| <= 300` seconds (5 minutes). Replay payloads older than 5 minutes fail immediately with `PAYMENT_4001`.
- **Signed Payload**: `{timestamp}.{rawBody}`
- **Algorithm**: `HMAC-SHA256(signedPayload, stripeWebhookSecret)`

### C. Carrier Logistics Webhooks (Delhivery & Shiprocket)
- **Header**: `X-Webhook-Signature` or `X-Delhivery-Signature` / `X-Shiprocket-Token`
- **Algorithm**: `HMAC-SHA256(rawBody, fulfillmentWebhookSecret)`

---

## 3. Idempotency & Deduplication Engine

Managed by `WebhookIdempotencyService` (`com.maito.shared.idempotency`):
- **Redis Key Pattern**: `webhook:processed:{eventId}`
- **TTL**: 24 Hours (`Duration.ofHours(24)`)
- **Mechanism**: Atomic `SET key "PROCESSED" NX EX 86400`
- **Resilient Fallback**: Automatic in-memory TTL map if Redis is temporarily offline or in test environments.
- **Duplicate Behavior**: Returns HTTP 200 with `status: "ALREADY_PROCESSED"` or `idempotent: true`, preventing duplicate ledger charges, multiple stock deductions, or redundant notification blasts.

---

## 4. Payment Gateway Webhook Specifications

### Razorpay Inbound Webhook
- **Route**: `POST /api/v1/payments/webhook/razorpay`
- **Supported Events**:
  - `payment.captured`: Transitions `PaymentTransaction` to `SUCCESS` and confirms order as `PAID`.
  - `payment.failed`: Transitions `PaymentTransaction` to `FAILED` and marks order as `FAILED`.
  - `refund.processed`: Transitions `PaymentTransaction` to `REFUNDED` and marks order as `REFUNDED`.

### Stripe Inbound Webhook
- **Route**: `POST /api/v1/payments/webhook/stripe`
- **Supported Events**:
  - `payment_intent.succeeded`: Transitions `PaymentTransaction` to `SUCCESS` and marks order as `PAID`.
  - `payment_intent.payment_failed`: Transitions transaction to `FAILED` and marks order as `FAILED`.
  - `charge.refunded`: Transitions transaction to `REFUNDED` and marks order as `REFUNDED`.

---

## 5. Logistics & 3PL Fulfillment Webhook Specifications

- **Route**: `POST /api/v1/fulfillment/webhooks/{carrier}`
- **Supported Carriers**: `delhivery`, `shiprocket`, `bluedart`, `self_fleet`
- **Payload Normalization**:
  - Waybill keys: `waybill`, `awb`, `trackingNumber`
  - Status mapping:
    - `In Transit` / `DISPATCHED` -> `ShipmentStatus.IN_TRANSIT` (sets `dispatchedAt`)
    - `OUT_FOR_DELIVERY` -> `ShipmentStatus.OUT_FOR_DELIVERY`
    - `Delivered` -> `ShipmentStatus.DELIVERED` (sets `deliveredAt`)
    - `RTO` / `Undelivered` -> `ShipmentStatus.RTO`
- **Automated Checkpoints**: Appends new `ShipmentCheckpoint` records to database.
- **Customer Notification**: Automatically fires async notification (`SHIPMENT_TRACKING_UPDATE`) via `NotificationDispatchService`.

---

## 6. Communication Engine & Notification Channels

- **Asynchronous Execution Pool**: Dedicated Spring bean `notificationTaskExecutor` running threads with prefix `notification-worker-` (core: 4, max: 16, queue: 200).
- **Email Channel**: AWS SES API integration with SMTP and sandbox logging.
- **WhatsApp Channel**: Meta WhatsApp Cloud Graph API v19.0 client with interactive template dispatch and sandbox logging.
- **SMS Channel**: Twilio Messages REST API client with basic authentication and sandbox logging.

---

## 7. Configuration Reference (`application.yml`)

```yaml
maito:
  payment:
    razorpay:
      key-id: ${RAZORPAY_KEY_ID:rzp_test_mock_key_2026}
      secret: ${RAZORPAY_SECRET:secret_mock_test_2026}
      webhook-secret: ${RAZORPAY_WEBHOOK_SECRET:rzp_webhook_secret_default_2026}
    stripe:
      publishable-key: ${STRIPE_PUBLISHABLE_KEY:pk_test_mock_key_2026}
      secret-key: ${STRIPE_SECRET_KEY:sk_test_mock_secret_2026}
      webhook-secret: ${STRIPE_WEBHOOK_SECRET:whsec_mock_test_stripe_2026}
  fulfillment:
    webhook-secret: ${FULFILLMENT_WEBHOOK_SECRET:whsec_fulfillment_default_2026}
    delhivery:
      api-token: ${DELHIVERY_API_TOKEN:dummy_delhivery_token}
      base-url: ${DELHIVERY_BASE_URL:https://track.delhivery.com}
      sandbox: ${DELHIVERY_SANDBOX:true}
    shiprocket:
      api-token: ${SHIPROCKET_API_TOKEN:dummy_shiprocket_token}
      base-url: ${SHIPROCKET_BASE_URL:https://apiv2.shiprocket.in}
      sandbox: ${SHIPROCKET_SANDBOX:true}
  notification:
    email:
      provider: ${EMAIL_PROVIDER:SANDBOX}
      ses-region: ${AWS_SES_REGION:ap-south-1}
      from-address: ${EMAIL_FROM:notifications@mitocrunch.com}
    whatsapp:
      provider: ${WHATSAPP_PROVIDER:SANDBOX}
      phone-number-id: ${META_WHATSAPP_PHONE_ID:1234567890}
      access-token: ${META_WHATSAPP_ACCESS_TOKEN:dummy_token}
    sms:
      provider: ${SMS_PROVIDER:SANDBOX}
      twilio-account-sid: ${TWILIO_ACCOUNT_SID:dummy_sid}
      twilio-auth-token: ${TWILIO_AUTH_TOKEN:dummy_token}
      twilio-from-number: ${TWILIO_FROM_NUMBER:+1234567890}
```
