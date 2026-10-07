const fs = require('fs');
const path = require('path');

console.log('========================================================================');
console.log('🔍 MAITO KUBERNETES & HELM SUITE VALIDATOR');
console.log('========================================================================');

const baseDir = path.resolve(__dirname, '..');
const k8sDir = path.join(baseDir, 'k8s');
const helmDir = path.join(baseDir, 'helm', 'maito-monolith');

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

// 1. Validate Standalone K8s Manifests
console.log('\n[1/3] Validating Declarative Manifests in ' + k8sDir);
const expectedK8sFiles = [
  '00-namespace.yaml',
  '01-configmap.yaml',
  '02-secrets.yaml',
  '03-postgres-statefulset.yaml',
  '03-postgres-service.yaml',
  '04-redis-statefulset.yaml',
  '04-redis-service.yaml',
  '05-elasticsearch.yaml',
  '06-app-deployment.yaml',
  '06-app-service.yaml',
  '07-hpa.yaml',
  '08-ingress.yaml',
  '08-cert-issuer.yaml'
];

expectedK8sFiles.forEach(file => {
  const filePath = path.join(k8sDir, file);
  assert(fs.existsSync(filePath), 'File exists: ' + file);
  if (fs.existsSync(filePath)) {
    const content = fs.readFileSync(filePath, 'utf8');
    assert(content.includes('apiVersion:'), file + ' contains apiVersion');
    assert(content.includes('kind:'), file + ' contains kind');
    assert(content.includes('metadata:'), file + ' contains metadata');
  }
});

// Deep Check: App Deployment Security & Resources
const appDepPath = path.join(k8sDir, '06-app-deployment.yaml');
if (fs.existsSync(appDepPath)) {
  const dep = fs.readFileSync(appDepPath, 'utf8');
  assert(dep.includes('runAsNonRoot: true'), 'App runs as non-root user');
  assert(dep.includes('runAsUser: 10001'), 'App specifies UID 10001');
  assert(dep.includes('runAsGroup: 10001'), 'App specifies GID 10001');
  assert(dep.includes('allowPrivilegeEscalation: false'), 'App disables privilege escalation');
  assert(dep.includes('drop:') && dep.includes('- ALL'), 'App drops all Linux capabilities');
  assert(dep.includes('cpu: 500m'), 'App defines CPU request 500m');
  assert(dep.includes('memory: 512Mi'), 'App defines Memory request 512Mi');
  assert(dep.includes('cpu: 2000m'), 'App defines CPU limit 2000m');
  assert(dep.includes('memory: 2048Mi'), 'App defines Memory limit 2048Mi');
  assert(dep.includes('maxSurge: 25%'), 'Rolling update strategy has maxSurge 25%');
  assert(dep.includes('maxUnavailable: 0'), 'Rolling update strategy has maxUnavailable 0');
  assert(dep.includes('/actuator/health/liveness'), 'Liveness probe configured on /actuator/health/liveness');
  assert(dep.includes('/actuator/health/readiness'), 'Readiness probe configured on /actuator/health/readiness');
}

// Deep Check: Ingress Wildcard & Cert-Manager
const ingPath = path.join(k8sDir, '08-ingress.yaml');
if (fs.existsSync(ingPath)) {
  const ing = fs.readFileSync(ingPath, 'utf8');
  assert(ing.includes('host: "*.maito.io"'), 'Ingress supports wildcard subdomains *.maito.io');
  assert(ing.includes('cert-manager.io/cluster-issuer: letsencrypt-prod'), 'Ingress specifies ClusterIssuer letsencrypt-prod');
  assert(ing.includes('nginx.ingress.kubernetes.io/proxy-body-size: "25m"'), 'Ingress sets proxy body size to 25m');
}

// 2. Validate Helm Chart Package
console.log('\n[2/3] Validating Production Helm Chart in ' + helmDir);
assert(fs.existsSync(path.join(helmDir, 'Chart.yaml')), 'Chart.yaml exists');
assert(fs.existsSync(path.join(helmDir, 'values.yaml')), 'values.yaml exists');
assert(fs.existsSync(path.join(helmDir, 'values-production.yaml')), 'values-production.yaml exists');
assert(fs.existsSync(path.join(helmDir, 'templates', '_helpers.tpl')), 'templates/_helpers.tpl exists');

const expectedTemplates = [
  'namespace.yaml',
  'configmap.yaml',
  'secret.yaml',
  'app-deployment.yaml',
  'app-service.yaml',
  'app-hpa.yaml',
  'app-pdb.yaml',
  'ingress.yaml',
  'cert-issuer.yaml',
  'postgres-statefulset.yaml',
  'postgres-service.yaml',
  'redis-statefulset.yaml',
  'redis-service.yaml',
  'elasticsearch-statefulset.yaml',
  'elasticsearch-service.yaml'
];

expectedTemplates.forEach(tpl => {
  const tplPath = path.join(helmDir, 'templates', tpl);
  assert(fs.existsSync(tplPath), 'Template exists: ' + tpl);
});

// 3. Summary
console.log('\n========================================================================');
console.log(`SUMMARY: ${passedChecks}/${totalChecks} checks passed.`);
if (errors.length > 0) {
  console.log('FAILED CHECKS:');
  errors.forEach(e => console.log(' - ' + e));
  process.exit(1);
} else {
  console.log('🎉 ALL KUBERNETES & HELM SPECIFICATIONS VERIFIED SUCCESSFULLY!');
  console.log('========================================================================\n');
}
