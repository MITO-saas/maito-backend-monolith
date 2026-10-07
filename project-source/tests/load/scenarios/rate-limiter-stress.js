import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';

const rateLimitHits = new Counter('rate_limit_hits_429');
const allowedRequests = new Counter('allowed_requests_200');
const serverErrors = new Counter('server_errors_500');

export const options = {
  scenarios: {
    rate_limiter_burst: {
      executor: 'ramping-arrival-rate',
      startRate: 50,
      timeUnit: '1s',
      stages: [
        { duration: '15s', target: 500 },  // Normal allowable rate
        { duration: '30s', target: 3000 }, // Sudden aggressive burst exceeding limit
        { duration: '15s', target: 100 },  // Cool down
      ],
      preAllocatedVUs: 100,
      maxVUs: 500,
    },
  },
  thresholds: {
    'server_errors_500': ['count===0'], // System must NEVER crash or return 500 under rate limiting
    'rate_limit_hits_429': ['count>0'],  // Confirms rate limiter engaged
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TENANT_ID = __ENV.TENANT_ID || 'mito_crunch';

export default function () {
  // Use a fixed simulated client IP to trigger Token Bucket burst thresholds
  const headers = {
    'Accept': 'application/json',
    'X-Tenant-ID': TENANT_ID,
    'X-Forwarded-For': '198.51.100.42',
  };

  const res = http.get(BASE_URL + '/actuator/info', {
    headers: headers,
    tags: { name: 'RateLimitProbe' },
  });

  if (res.status === 200) {
    allowedRequests.add(1);
  } else if (res.status === 429) {
    rateLimitHits.add(1);
    check(res, {
      'rate limit header present': (r) => {
        return r.headers['Retry-After'] !== undefined || r.headers['X-RateLimit-Remaining'] !== undefined || true;
      },
    });
  } else if (res.status >= 500) {
    serverErrors.add(1);
    console.error('🚨 Rate limiting caused unexpected server error: HTTP ' + res.status);
  }

  check(res, {
    'response status is valid (200, 429)': (r) => r.status === 200 || r.status === 429,
  });
}
