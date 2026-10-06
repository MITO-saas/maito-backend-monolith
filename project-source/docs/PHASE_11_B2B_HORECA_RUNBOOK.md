# Phase 11: B2B Wholesale Portal, HoReCa Engine & Credit Term Invoicing Runbook

## 1. Executive Summary
Phase 11 implements the enterprise B2B Wholesale Portal, HoReCa (Hotel, Restaurant, Catering) bulk supply engine, and Credit Term Invoicing architecture (`com.maito.b2b`) on `maito-backend-monolith`. It enables corporate and HoReCa clients to register with statutory Indian tax identifiers (GSTIN, PAN, FSSAI), gain automated tiered volume pricing (e.g., standard ₹150 vs wholesale ₹95), place bulk purchase orders on net credit terms (`NET_30`, `NET_60`, `PREPAID`), receive compliant GST tax invoices with tax breakdowns (CGST, SGST, IGST), and manage credit line limits with immutable double-entry credit ledgers.

---

## 2. Indian GSTIN & KYC Verification Rules

### 2.1 Statutory Identification Standard
- **GSTIN Format Pattern:** `^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$`
  * 2 Digits: State code (e.g. `10` for Bihar, `27` for Maharashtra).
  * 10 Characters: Permanent Account Number (PAN) of the business entity.
  * 1 Character: Entity number of the same PAN holder within the state.
  * 1 Character: Default alphabet `Z`.
  * 1 Character: Checksum digit.
- **PAN Derivation:**
  * If PAN is not explicitly supplied during registration, it is automatically derived from characters 3–12 of the validated GSTIN (`gstin.substring(2, 12)`).
- **FSSAI License:**
  * Validated for food business operators (14-digit alphanumeric license registration).

---

## 3. Wholesale Tiered Volume Pricing Algorithm

### 3.1 Tier Resolution Logic
Wholesale pricing is resolved dynamically per line item:
```
Matching Tier = SELECT * FROM b2b_price_tiers
                WHERE variant_id = :variantId
                  AND is_active = true
                  AND min_quantity <= :requestedQuantity
                ORDER BY min_quantity DESC
                LIMIT 1;
```
- If a matching tier exists, `wholesale_unit_price` replaces the catalog retail price.
- If no tier matches (e.g. order quantity < minimum tier threshold), the standard catalog price is used as fallback.

---

## 4. Enterprise Credit Line State Machine & Ledgers

### 4.1 Credit Line Lifecycle
```
                +-----------------------+
                | PENDING_VERIFICATION  |  (Initial partner registration)
                +-----------+-----------+
                            |
                            v (Admin KYC verification + Credit Limit)
                +-----------------------+
                |       VERIFIED        |  (Active credit line: credit_limit, payment_terms_days)
                +-----------+-----------+
                            |
            +---------------+---------------+
            |                               |
    (Bulk Order Placed)             (Invoice Paid)
            |                               |
            v                               v
    [CREDIT_HOLD]                   [CREDIT_RELEASE]
    used_credit += orderTotal       used_credit -= invoiceTotal
    available = limit - used        available = limit - used
```

### 4.2 Invariant Verification
1. **Credit Sufficiency Check:** `orderTotal <= (partner.creditLimit - partner.usedCredit)`
2. **Breach Handling:** If order total exceeds available credit line, raises `BusinessException(ErrorCode.INSUFFICIENT_B2B_CREDIT)`.
3. **Audit Trail:** Every hold, release, limit increase, or limit decrease records an immutable row in `b2b_credit_ledgers`.

---

## 5. Tax Invoicing & Settlement Lifecycle

- Invoices (`b2b_invoices`) are automatically generated upon bulk order confirmation.
- **Due Date:** Calculated based on terms: `due_at = now() + (30 or 60 days)`.
- **Tax Breakdown:** Standard food wholesale 5% GST (2.5% CGST + 2.5% SGST) stored in JSONB along with HSN code `19041090`.
- **Settlement:** Calling `POST /api/v1/b2b/invoices/{id}/pay` marks the invoice `PAID`, sets `paid_at`, and atomically restores partner credit.

---

## 6. End-to-End API cURL Examples

### 6.1 Register B2B Partner (Customer Ingress)
```bash
curl -X POST "http://localhost:8080/api/v1/b2b/register-partner" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer <CUSTOMER_JWT_TOKEN>" \
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

### 6.2 Admin Verify Partner & Assign Credit Line
```bash
curl -X PUT "http://localhost:8080/api/v1/admin/b2b/partners/<PARTNER_UUID>/verify" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer <ADMIN_JWT_TOKEN>" \
  -d '{
    "verificationStatus": "VERIFIED",
    "creditLimit": 500000.00,
    "paymentTermsDays": 30,
    "notes": "Verified KYC documents and bank statement"
  }'
```

### 6.3 Admin Configure Volume Pricing Tier
```bash
curl -X POST "http://localhost:8080/api/v1/admin/b2b/pricing-tiers" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer <ADMIN_JWT_TOKEN>" \
  -d '{
    "variantId": "<VARIANT_UUID>",
    "minQuantity": 50,
    "wholesaleUnitPrice": 95.00,
    "currencyCode": "INR",
    "isActive": true
  }'
```

### 6.4 Place B2B Bulk Order with Net-30 Terms
```bash
curl -X POST "http://localhost:8080/api/v1/b2b/bulk-orders" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer <CUSTOMER_JWT_TOKEN>" \
  -d '{
    "items": [
      {
        "variantId": "<VARIANT_UUID>",
        "quantity": 100
      }
    ],
    "paymentTerms": "NET_30",
    "notes": "Monthly banquet supplies order"
  }'
```

### 6.5 Settle Tax Invoice & Restore Credit Line
```bash
curl -X POST "http://localhost:8080/api/v1/b2b/invoices/<INVOICE_UUID>/pay" \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Authorization: Bearer <CUSTOMER_JWT_TOKEN>"
```