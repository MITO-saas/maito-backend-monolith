# Phase 8: Interactive Storefront Client, Reactive State Machine & Visual Tracking Stepper Runbook

**Module Focus:** Storefront Presentation Layer & Client Systems Architecture  
**Assets Location:** `src/main/resources/static/` (`index.html`, `assets/js/store.js`)  
**Target Golden Release:** Phase 8 Interactive Client  
**Multi-Tenant Target:** `mito_crunch` (`db_mito_crunch` tenant database)  

---

## 1. Architectural Overview

Phase 8 elevates the Server-Driven UI (SDUI) from a static visual layout engine to a fully reactive, interactive e-commerce single-page client. Operating without heavy Node runtime dependencies in production, it utilizes a modular, native JavaScript reactive state management engine (`MaitoStore`) paired with Tailwind CSS and Lucide icons.

### System Architecture Diagram

```
+-----------------------------------------------------------------------------------------+
|                                    BROWSER CLIENT                                       |
|                                                                                         |
|   +-------------------+      +---------------------+      +-------------------------+   |
|   |    Auth Modal     |      |  Reactive Cart      |      |   One-Page Checkout     |   |
|   | (Login/Register/  |      |  Slide-Over Drawer  |      |   & Payment Callback    |   |
|   |  Guest Merge)     |      |  (Qty, Coupons)     |      |   (Auto-paid, Stepper)  |   |
|   +---------+---------+      +----------+----------+      +------------+------------+   |
|             |                           |                              |                |
|             +-------------------+       |       +----------------------+                |
|                                 |       |       |                                       |
|                                 v       v       v                                       |
|                  +----------------------------------------------+                       |
|                  |     MaitoStore Reactive State Manager        |                       |
|                  |     (store.js - Singleton Event Bus)         |                       |
|                  |  - cartId: UUID (localStorage)               |                       |
|                  |  - authToken: JWT (localStorage)             |                       |
|                  |  - activeTenant: 'mito_crunch'               |                       |
|                  +----------------------+-----------------------+                       |
+-----------------------------------------|-----------------------------------------------+
                                          | HTTP / JSON
                                          | Headers: X-Tenant-ID, X-Cart-ID, Authorization
                                          v
+-----------------------------------------------------------------------------------------+
|                              SPRING BOOT MONOLITH RUNTIME                               |
|                                                                                         |
|   +-------------------+  +--------------------+  +------------------+  +-------------+  |
|   | /api/v1/auth/*    |  | /api/v1/cart/*     |  | /api/v1/checkout |  | /api/v1/    |  |
|   | - login           |  | - items (POST/PUT) |  |   /create-order  |  |  fulfillment|  |
|   | - register        |  | - merge            |  | - payment-       |  |  /track/    |  |
|   |                   |  | - apply coupon     |  |   callback       |  |   {orderNo} |  |
|   +---------+---------+  +---------+----------+  +--------+---------+  +------+------+  |
|             |                      |                      |                   |         |
+-------------|----------------------|----------------------|-------------------|---------+
              |                      |                      |                   |
              v                      v                      v                   v
+-----------------------------------------------------------------------------------------+
|                           TENANT DATABASE (db_mito_crunch)                              |
|   - user_profiles, auth_credentials                                                     |
|   - cart, cart_items                                                                    |
|   - orders, order_items, payments                                                       |
|   - shipments, shipment_checkpoints, tracking_events                                    |
+-----------------------------------------------------------------------------------------+
```

---

## 2. Client-Side State Machine (`store.js`)

The `MaitoStore` class provides centralized, deterministic state management across all UI components:

### Key State Properties
- `activeTenant` (`string`): Injected as `X-Tenant-ID: mito_crunch` on every outbound API call.
- `cartId` (`string`): Persistent guest UUID in `localStorage` under `maito_cart_id`. Auto-generated using `crypto.randomUUID()` if uninitialized.
- `authToken` (`string | null`): Bearer JWT stored in `localStorage` under `maito_auth_token`. Automatically added as `Authorization: Bearer <token>` on authenticated requests.
- `userProfile` (`object | null`): Current customer profile (`firstName`, `lastName`, `email`, `role`) cached under `maito_user_profile`.
- `cart` (`object`): Live cart structure containing items, quantities, subtotal, and total amount.
- `appliedCoupon` (`object | null`): Active promotion envelope (`code`, `discountAmount`, `freeShippingSavings`).

### Event Bus & Reactive Subscriptions
Components register listeners via `store.subscribe(fn)`. The store emits events to trigger instantaneous UI re-renders:
- `AUTH_CHANGED`: Fired on login, registration, and logout. Updates header navigation, profile display, and checkout forms.
- `CART_UPDATED`: Fired on item additions, quantity modifications, item removals, and post-order cart clears.
- `COUPON_APPLIED` / `COUPON_REMOVED`: Fired on promotion evaluations, recalculating discounts and shipping waivers in real time.

---

## 3. Core Storefront Components

### 3.1 Auth Modal & Session Management
- **Trigger:** "Sign In" button in header or automatically when proceeding to checkout as guest.
- **Features:**
  - Login tab and Register tab with live form validation.
  - "Demo Fill (Customer)" shortcut button prefilling `customer@mitocrunch.com` / `Customer@2026`.
  - On login/register:
    1. Authenticates via `POST /api/v1/auth/login`.
    2. Caches JWT and profile in `localStorage`.
    3. Calls `POST /api/v1/cart/merge` with `guestCartId` to merge anonymous items into the user's permanent profile.
    4. Transforms header button to a user avatar displaying `Hi, <Name>` with a dropdown menu and "Sign Out" option.

### 3.2 Dynamic "Add to Cart" Card Binding
- **Trigger:** "Add to Cart" button on any product variant card.
- **Features:**
  - Automatically captures the variant ID (e.g. `f1000000-0000-0000-0000-000000000001` for Peri Peri Makhana).
  - Dispatches `POST /api/v1/cart/items` with headers `X-Tenant-ID` and `X-Cart-ID`.
  - Button animates to green "Added!" state with checkmark icon for 1.2 seconds.
  - Cart counter badge on the header floats, pulses, and increments immediately.

### 3.3 Slide-Over Cart Drawer
- **Trigger:** Clicking the shopping cart icon in the header.
- **Features:**
  - Smooth slide-in drawer from the right edge with a blurred backdrop overlay.
  - Line items display: product thumbnail image, title, variant SKU, unit price, and item subtotal.
  - Quantity controls:
    - `+` button increments item quantity via `PUT /api/v1/cart/items/{itemId}`.
    - `-` button decrements item quantity (removes if reduced to 0).
    - Trash icon deletes item via `DELETE /api/v1/cart/items/{itemId}`.
  - Coupon Input Box:
    - Supports promotional codes (e.g. `CRUNCH20` for 20% off orders >= ₹500, `CRUNCHFREE` for free shipping).
    - Dispatches `POST /api/v1/promotions/apply`.
    - Renders green savings tag with "Remove" action.
  - Live Financial Summary:
    - Subtotal
    - Discount (if coupon applied)
    - Delivery Fee (Free on orders >= ₹499 or with free shipping voucher)
    - Total Payable Amount

### 3.4 One-Page Checkout View
- **Trigger:** "Proceed to Checkout" button inside Cart Drawer.
- **Features:**
  - If unauthenticated, displays prompt to sign in with single-click demo login.
  - Pre-fills user contact details (`name`, `phone`).
  - Shipping address form: Full Name, Phone, Address Line 1, City, State, Postal Code.
  - Live summary of order contents and final payable amount.
  - "Place Order & Pay" button:
    1. Dispatches `POST /api/v1/checkout/create-order`.
    2. Receives confirmed `OrderResponse` with order number (`MC-2026-XXXXX`).
    3. Simulates instant payment callback via `POST /api/v1/checkout/payment-callback` with status `PAID`.
    4. Automatically deducts inventory stock in tenant database.
    5. Clears active cart state and presents the Order Confirmation view.
    6. Provides direct action to open the Live Tracking Stepper.

### 3.5 Live Order Tracking Modal & Progress Stepper
- **Trigger:** "Track Order" button from confirmation screen or clicking "Track Order" in header.
- **Features:**
  - Allows entering any valid order number (prefilled from latest checkout).
  - Queries `GET /api/v1/fulfillment/track/{orderNumber}`.
  - Renders a 5-Stage Visual Progress Stepper:
    1. `[Order Placed]` -> Placed and confirmed
    2. `[Processing]` -> Warehouse packing in progress
    3. `[Dispatched]` -> Manifested with carrier
    4. `[Out for Delivery]` -> Rider assigned / in transit
    5. `[Delivered]` -> Delivered to customer
  - Displays: Carrier Name, Tracking AWB Number, Estimated Delivery Date, and Chronological Checkpoint Timeline with timestamped hub updates.

---

## 4. API Request & Response Contracts

| Component | Method | Endpoint | Headers | Request Payload | Response Attributes |
|---|---|---|---|---|---|
| Customer Login | `POST` | `/api/v1/auth/login` | `X-Tenant-ID` | `{"email":"...","password":"..."}` | `accessToken`, `userProfile` |
| Add Cart Item | `POST` | `/api/v1/cart/items` | `X-Tenant-ID`, `X-Cart-ID` | `{"variantId":"<UUID>","quantity":1}` | `CartResponse` with `items[]` |
| Update Quantity | `PUT` | `/api/v1/cart/items/{id}` | `X-Tenant-ID`, `X-Cart-ID` | `{"quantity":N}` | Updated `CartResponse` |
| Remove Item | `DELETE` | `/api/v1/cart/items/{id}` | `X-Tenant-ID`, `X-Cart-ID` | None | Updated `CartResponse` |
| Apply Coupon | `POST` | `/api/v1/promotions/apply` | `X-Tenant-ID` | `{"code":"CRUNCH20","cartSubtotal":596.00}` | `applied`, `discountAmount` |
| Merge Cart | `POST` | `/api/v1/cart/merge` | `X-Tenant-ID`, `Authorization` | `{"guestCartId":"<UUID>"}` | Merged `CartResponse` |
| Create Order | `POST` | `/api/v1/checkout/create-order` | `X-Tenant-ID`, `Authorization`, `X-Cart-ID` | `{"shippingAddress":{...},"couponCode":"..."}` | `OrderResponse` (`id`, `orderNumber`, `orderStatus`) |
| Mock Payment | `POST` | `/api/v1/checkout/payment-callback` | `X-Tenant-ID` | `{"orderId":"...","status":"PAID"}` | Confirmed `PAID` `OrderResponse` |
| Track Order | `GET` | `/api/v1/fulfillment/track/{orderNo}` | `X-Tenant-ID` | None | `TrackingTimelineResponse` with stages & checkpoints |

---

## 5. End-to-End Verification & Manual Browser Testing Steps

To test the complete storefront workflow in any desktop or mobile browser:

1. **Start Application:**
   ```powershell
   cmd.exe /c "set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot&& mvnw.cmd spring-boot:run"
   ```
2. **Open Browser:**
   Navigate to `http://localhost:8080/`.
3. **Add Items as Guest:**
   - Scroll to "Trending Flavors" section.
   - Click "Add to Cart" on *Peri Peri Makhana* 4 times (or click once, open drawer, and click `+` to increase quantity to 4 for total >= ₹500).
   - Observe button feedback and badge counter in header.
4. **Apply Promotion:**
   - Click the header Cart button to open the Slide-Over Cart Drawer.
   - Enter `CRUNCH20` in the Promo Code field and click "Apply".
   - Verify green badge appears: "Coupon CRUNCH20 applied (₹119.20 saved)".
   - Verify Subtotal: ₹596.00, Discount: -₹119.20, Shipping: FREE, Total: ₹476.80.
5. **Proceed to Checkout:**
   - Click "Proceed to Checkout".
   - If not signed in, click "Sign In to Checkout" -> click "Demo Fill (Customer)" -> click "Sign In".
   - Notice cart items are seamlessly merged and your name appears in the header.
   - Fill in shipping address or use defaults (MG Road, Patna, Bihar, 800001).
   - Click "Place Order & Pay".
6. **Order Confirmation & Tracking:**
   - Notice Order Confirmed modal appears with Order Number (e.g. `MC-2026-539186`).
   - Mock payment is automatically triggered in the background, transitioning status to `PAID`.
   - Click "Track Order Live".
   - Visual Stepper timeline renders with the `PROCESSING` milestone highlighted and checkpoint details displayed.

---

## 6. Certification & Automated Test Suite Results

Full regression testing executed under JDK 21:
```powershell
cmd.exe /c "set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot&& mvnw.cmd clean test"
```

**Outcome:**
```
[INFO] Results:
[INFO] 
[INFO] Tests run: 105, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] -------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] -------------------------------------------------------
[INFO] Total time:  53.766 s
```
All 105 domain, security, concurrency, multi-tenancy, and fulfillment tests pass with zero errors.
