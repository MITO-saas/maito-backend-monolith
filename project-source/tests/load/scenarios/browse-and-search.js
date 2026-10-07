import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const searchLatency = new Trend('search_latency', true);
const suggestLatency = new Trend('suggest_latency', true);
const catalogLatency = new Trend('catalog_latency', true);
const successfulQueries = new Rate('successful_queries');

export const options = {
  scenarios: {
    sustained_browse_traffic: {
      executor: 'constant-arrival-rate',
      rate: 5000, // 5,000 RPS sustained
      timeUnit: '1s',
      duration: '5m',
      preAllocatedVUs: 500,
      maxVUs: 2000,
    },
  },
  thresholds: {
    'http_req_duration{type:read}': ['p(95)<150'],
    'search_latency': ['p(95)<150', 'p(99)<250'],
    'suggest_latency': ['p(95)<80', 'p(99)<150'],
    'catalog_latency': ['p(95)<100'],
    'successful_queries': ['rate>0.999'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TENANT_ID = __ENV.TENANT_ID || 'mito_crunch';

const SEARCH_TERMS = [
  'makhana', 'peri peri', 'roasted', 'salt', 'mint',
  'organic', 'crunch', 'superfood', 'fox nut', 'healthy'
];

export default function () {
  const query = SEARCH_TERMS[Math.floor(Math.random() * SEARCH_TERMS.length)];
  const headers = {
    'Accept': 'application/json',
    'X-Tenant-ID': TENANT_ID,
  };

  const rand = Math.random();

  if (rand < 0.40) {
    // 40% Traffic: Search-as-You-Type Suggestions (Elasticsearch edge-ngram)
    const res = http.get(BASE_URL + '/api/v1/search/suggest?q=' + encodeURIComponent(query.substring(0, 4)), {
      headers: headers,
      tags: { type: 'read', name: 'TypeaheadSuggest' },
    });
    suggestLatency.add(res.timings.duration, { type: 'read' });
    const passed = check(res, { 'suggest status 200': (r) => r.status === 200 });
    successfulQueries.add(passed);
  } else if (rand < 0.80) {
    // 40% Traffic: Full-Text Product Search with Faceted Filters
    const res = http.get(BASE_URL + '/api/v1/search/products?q=' + encodeURIComponent(query) + '&page=0&size=10&sort=relevance', {
      headers: headers,
      tags: { type: 'read', name: 'FacetedProductSearch' },
    });
    searchLatency.add(res.timings.duration, { type: 'read' });
    const passed = check(res, { 'search status 200': (r) => r.status === 200 });
    successfulQueries.add(passed);
  } else {
    // 20% Traffic: Product Catalog Detail (Redis Caching Layer)
    const res = http.get(BASE_URL + '/api/v1/catalog/products/p1000000-0000-0000-0000-000000000001', {
      headers: headers,
      tags: { type: 'read', name: 'CatalogProductDetail' },
    });
    catalogLatency.add(res.timings.duration, { type: 'read' });
    const passed = check(res, { 'catalog detail status 200 or 404': (r) => r.status === 200 || r.status === 404 });
    successfulQueries.add(passed);
  }
}
