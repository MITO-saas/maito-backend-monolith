import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// Custom Metrics
const ordersCreated = new Counter('orders_created');
const outOfStockConflicts = new Counter('out_of_stock_conflicts');
const oversellViolations = new Counter('oversell_violations');
const checkoutDuration = new Trend('checkout_duration', true);

// Global flash sale stock limit
const STOCK_LIMIT = 10;
let localSuccessfulOrders = 0;

export const options = {
  scenarios: {
    flash_sale_spike: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 2000 },
        { duration: '1m30s', target: 10000 },
        { duration: '30s', target: 10000 },
        { duration: '30s', target: 0 },
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    'http_req_duration{type:read}': ['p(95)<150'],
    'http_req_duration{type:write}': ['p(99)<350'],
    'oversell_violations': ['count===0'],
    'orders_created': ['count<=10'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TENANT_ID = __ENV.TENANT_ID || 'mito_crunch';
const LIMITED_SKU = 'f1000000-0000-0000-0000-000000000001';

export default function () {
  const vuId = __VU;
  const iterId = __ITER;
  const cartId = 'guest-cart-vu-' + vuId + '-' + iterId + '-' + Date.now();

  const headers = {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
    'X-Tenant-ID': TENANT_ID,
    'X-Cart-ID': cartId,
  };

  // STEP 1: Search Product Catalog (Read Endpoint)
  const searchStart = Date.now();
  const searchRes = http.get(BASE_URL + '/api/v1/search/products?q=makhana&page=0&size=5', {
    headers: headers,
    tags: { type: 'read', name: 'SearchProducts' },
  });

  check(searchRes, {
    'search status is 200': (r) => r.status === 200,
  });

  // Small think time (simulate user clicking product)
  sleep(0.05);

  // STEP 2: Add Limited-Inventory Variant to Cart (Write Endpoint)
  const addToCartPayload = JSON.stringify({
    variantId: LIMITED_SKU,
    quantity: 1,
  });

  const cartRes = http.post(BASE_URL + '/api/v1/cart/items', addToCartPayload, {
    headers: headers,
    tags: { type: 'write', name: 'AddToCart' },
  });

  check(cartRes, {
    'cart add status is 200 or 201': (r) => r.status === 200 || r.status === 201,
  });

  // STEP 3: Atomic Flash-Sale Checkout & Stock Reservation
  const checkoutPayload = JSON.stringify({
    shippingAddress: {
      fullName: 'Flash Sale Buyer ' + vuId,
      addressLine1: 'Flat ' + vuId + ', High Concurrency Towers',
      city: 'Bengaluru',
      state: 'Karnataka',
      postalCode: '560001',
      country: 'IN',
      phone: '+9198765' + String(10000 + (vuId % 90000)),
    },
    couponCode: 'FLASH10',
  });

  const checkoutRes = http.post(BASE_URL + '/api/v1/checkout/create-order', checkoutPayload, {
    headers: headers,
    tags: { type: 'write', name: 'CreateOrder' },
  });

  checkoutDuration.add(checkoutRes.timings.duration, { type: 'write' });

  if (checkoutRes.status === 201) {
    ordersCreated.add(1);
    localSuccessfulOrders++;
    if (localSuccessfulOrders > STOCK_LIMIT) {
      oversellViolations.add(1);
      console.error('🚨 CRITICAL OVERSELL VIOLATION: Order created beyond available inventory limit of ' + STOCK_LIMIT);
    }
  } else if (checkoutRes.status === 409) {
    outOfStockConflicts.add(1);
    check(checkoutRes, {
      'conflict error indicates out of stock': (r) => {
        const body = r.body || '';
        return body.includes('OUT_OF_STOCK') || body.includes('insufficient') || body.includes('Inventory');
      },
    });
  } else {
    check(checkoutRes, {
      'expected either 201 Created or 409 Conflict': (r) => r.status === 201 || r.status === 409,
    });
  }

  sleep(0.1);
}
