const fs = require('fs');
const path = require('path');

console.log('========================================================================');
console.log('🔍 MAITO LOAD & CHAOS TESTING FRAMEWORK VALIDATOR');
console.log('========================================================================');

const baseDir = path.resolve(__dirname, '..');
const loadDir = path.join(baseDir, 'load');
const chaosDir = path.join(baseDir, 'chaos');

let totalChecks = 0;
let passedChecks = 0;
let errors = [];

function assert(condition, message) {
  totalChecks++;
  if (condition) {
    passedChecks++;
    console.log('  ✅ ' + message);
  } else {
    errors.push(message);
    console.log('  ❌ ' + message);
  }
}

// 1. Validate Load Test Config & Scenarios
console.log('\n[1/3] Validating k6 Load Test Suite in ' + loadDir);
const configPath = path.join(loadDir, 'config', 'k6-options.json');
assert(fs.existsSync(configPath), 'k6-options.json exists');
if (fs.existsSync(configPath)) {
  try {
    const config = JSON.parse(fs.readFileSync(configPath, 'utf8'));
    assert(config.baseUrl !== undefined, 'k6-options defines baseUrl');
    assert(config.thresholds !== undefined, 'k6-options defines thresholds');
    assert(config.thresholds['http_req_duration{type:read}'][0].includes('p(95)<150'), 'Read SLA enforces p95 < 150ms');
    assert(config.thresholds['http_req_duration{type:write}'][0].includes('p(99)<350'), 'Write SLA enforces p99 < 350ms');
    assert(config.scenarios.flash_sale !== undefined, 'k6-options defines flash_sale scenario');
    assert(config.scenarios.browse_and_search !== undefined, 'k6-options defines browse_and_search scenario');
    assert(config.scenarios.rate_limiter_stress !== undefined, 'k6-options defines rate_limiter_stress scenario');
  } catch (err) {
    assert(false, 'k6-options.json is valid JSON: ' + err.message);
  }
}

const flashSalePath = path.join(loadDir, 'scenarios', 'flash-sale-checkout.js');
assert(fs.existsSync(flashSalePath), 'flash-sale-checkout.js exists');
if (fs.existsSync(flashSalePath)) {
  const content = fs.readFileSync(flashSalePath, 'utf8');
  assert(content.includes('10000'), 'Flash sale ramps to 10,000 VUs');
  assert(content.includes('/api/v1/search/products'), 'User journey searches products');
  assert(content.includes('/api/v1/cart/items'), 'User journey adds limited variant to cart');
  assert(content.includes('/api/v1/checkout/create-order'), 'User journey executes checkout');
  assert(content.includes('oversell_violations'), 'Custom counter tracks oversell violations');
  assert(content.includes('409'), 'Handles HTTP 409 Conflict for out-of-stock orders');
}

const browseSearchPath = path.join(loadDir, 'scenarios', 'browse-and-search.js');
assert(fs.existsSync(browseSearchPath), 'browse-and-search.js exists');
if (fs.existsSync(browseSearchPath)) {
  const content = fs.readFileSync(browseSearchPath, 'utf8');
  assert(content.includes('5000'), 'Browse & search generates 5,000 RPS sustained throughput');
  assert(content.includes('/api/v1/search/suggest'), 'Tests typeahead suggest endpoint');
  assert(content.includes('/api/v1/search/products'), 'Tests faceted search query');
}

const rateLimiterPath = path.join(loadDir, 'scenarios', 'rate-limiter-stress.js');
assert(fs.existsSync(rateLimiterPath), 'rate-limiter-stress.js exists');
if (fs.existsSync(rateLimiterPath)) {
  const content = fs.readFileSync(rateLimiterPath, 'utf8');
  assert(content.includes('429'), 'Stress tests HTTP 429 Too Many Requests response');
  assert(content.includes('server_errors_500'), 'Monitors server errors to ensure resilience');
}

// 2. Validate Chaos Mesh Manifests
console.log('\n[2/3] Validating Chaos Mesh Experiments in ' + chaosDir);
const chaosFiles = [
  'redis-failure.yaml',
  'database-network-latency.yaml',
  'elasticsearch-pod-kill.yaml'
];

chaosFiles.forEach(file => {
  const filePath = path.join(chaosDir, 'experiments', file);
  assert(fs.existsSync(filePath), 'Chaos experiment exists: ' + file);
  if (fs.existsSync(filePath)) {
    const yaml = fs.readFileSync(filePath, 'utf8');
    assert(yaml.includes('apiVersion: chaos-mesh.org/'), file + ' uses Chaos Mesh CRD apiVersion');
    assert(yaml.includes('namespace: maito-prod'), file + ' targets namespace maito-prod');
  }
});

// Deep Check: Chaos Parameters
const redisChaos = fs.readFileSync(path.join(chaosDir, 'experiments', 'redis-failure.yaml'), 'utf8');
assert(redisChaos.includes('action: pod-failure'), 'Redis experiment tests pod-failure');
assert(redisChaos.includes('maito-redis'), 'Redis experiment selects maito-redis');

const dbChaos = fs.readFileSync(path.join(chaosDir, 'experiments', 'database-network-latency.yaml'), 'utf8');
assert(dbChaos.includes('action: delay'), 'Database experiment injects network delay');
assert(dbChaos.includes("latency: '150ms'"), 'Database experiment sets 150ms synthetic latency');

const esChaos = fs.readFileSync(path.join(chaosDir, 'experiments', 'elasticsearch-pod-kill.yaml'), 'utf8');
assert(esChaos.includes('action: pod-kill'), 'Elasticsearch experiment tests pod-kill');

// 3. Summary
console.log('\n========================================================================');
console.log(`SUMMARY: ${passedChecks}/${totalChecks} checks passed.`);
if (errors.length > 0) {
  console.log('FAILED CHECKS:');
  errors.forEach(e => console.log(' - ' + e));
  process.exit(1);
} else {
  console.log('🎉 ALL LOAD & CHAOS SPECIFICATIONS VERIFIED SUCCESSFULLY!');
  console.log('========================================================================\n');
}
