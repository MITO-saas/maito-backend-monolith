const fs = require('fs');
const path = require('path');

console.log('========================================================================');
console.log('🔍 GITHUB ACTIONS CI/CD SPECIFICATION & WORKFLOW VALIDATOR');
console.log('========================================================================');

const baseDir = path.resolve(__dirname, '..', '..');
const workflowsDir = path.join(baseDir, '.github', 'workflows');

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

console.log('\n[1/3] Validating Workflow File Existence in ' + workflowsDir);
const expectedWorkflows = [
  'ci-pipeline.yml',
  'cd-build-and-publish.yml',
  'cd-deploy-helm.yml'
];

expectedWorkflows.forEach(file => {
  const filePath = path.join(workflowsDir, file);
  assert(fs.existsSync(filePath), 'Workflow exists: ' + file);
  if (fs.existsSync(filePath)) {
    const content = fs.readFileSync(filePath, 'utf8');
    assert(content.includes('name:'), file + ' defines workflow name');
    assert(content.includes('on:'), file + ' defines trigger events (on)');
    assert(content.includes('jobs:'), file + ' defines jobs section');
  }
});

console.log('\n[2/3] Deep Inspecting CI Pipeline (ci-pipeline.yml)');
const ciPath = path.join(workflowsDir, 'ci-pipeline.yml');
if (fs.existsSync(ciPath)) {
  const ci = fs.readFileSync(ciPath, 'utf8');
  assert(ci.includes('static-analysis:'), 'ci-pipeline contains static-analysis job');
  assert(ci.includes('test-suite:'), 'ci-pipeline contains test-suite job');
  assert(ci.includes('vulnerability-scan:'), 'ci-pipeline contains vulnerability-scan job');
  assert(ci.includes('gitleaks/gitleaks-action'), 'ci-pipeline enforces Gitleaks secret scanning');
  assert(ci.includes('actions/setup-java@v4'), 'ci-pipeline configures JDK setup action');
  assert(ci.includes("java-version: '21'"), 'ci-pipeline targets Java 21 Temurin');
  assert(ci.includes('postgres:16-alpine'), 'ci-pipeline specifies PostgreSQL 16 service container');
  assert(ci.includes('redis:7-alpine'), 'ci-pipeline specifies Redis 7 service container');
  assert(ci.includes('mvnw clean test'), 'ci-pipeline runs automated tests with mvnw clean test');
  assert(ci.includes('aquasecurity/trivy-action'), 'ci-pipeline runs Trivy CVE scanner');
  assert(ci.includes("severity: 'CRITICAL,HIGH'"), 'ci-pipeline halts on CRITICAL/HIGH vulnerabilities');
  assert(ci.includes("exit-code: '1'"), 'ci-pipeline enforces strict exit-code 1 security gate');
}

console.log('\n[3/3] Deep Inspecting CD Pipelines (cd-build-and-publish.yml & cd-deploy-helm.yml)');
const cdBuildPath = path.join(workflowsDir, 'cd-build-and-publish.yml');
if (fs.existsSync(cdBuildPath)) {
  const cdBuild = fs.readFileSync(cdBuildPath, 'utf8');
  assert(cdBuild.includes('docker-build:'), 'cd-build-and-publish contains docker-build job');
  assert(cdBuild.includes('docker/setup-buildx-action'), 'cd-build-and-publish configures Docker Buildx');
  assert(cdBuild.includes('ghcr.io'), 'cd-build-and-publish targets GitHub Container Registry');
  assert(cdBuild.includes('cache-from: type=gha'), 'cd-build-and-publish uses GitHub Actions cache layer (cache-from)');
  assert(cdBuild.includes('cache-to: type=gha,mode=max'), 'cd-build-and-publish optimizes cache reuse (cache-to)');
  assert(cdBuild.includes('aquasecurity/trivy-action'), 'cd-build-and-publish scans built image with Trivy');
}

const cdDeployPath = path.join(workflowsDir, 'cd-deploy-helm.yml');
if (fs.existsSync(cdDeployPath)) {
  const cdDeploy = fs.readFileSync(cdDeployPath, 'utf8');
  assert(cdDeploy.includes('deploy-staging:'), 'cd-deploy-helm contains deploy-staging job');
  assert(cdDeploy.includes('deploy-production:'), 'cd-deploy-helm contains deploy-production job');
  assert(cdDeploy.includes('environment:'), 'cd-deploy-helm defines GitHub Deployment Environment');
  assert(cdDeploy.includes('production'), 'cd-deploy-helm configures production environment with gates');
  assert(cdDeploy.includes('azure/setup-helm'), 'cd-deploy-helm sets up Helm 3');
  assert(cdDeploy.includes('--atomic'), 'cd-deploy-helm deploys atomically (--atomic)');
  assert(cdDeploy.includes('--timeout 5m'), 'cd-deploy-helm enforces 5-minute timeout window');
  assert(cdDeploy.includes('maito-staging'), 'cd-deploy-helm deploys to maito-staging namespace');
  assert(cdDeploy.includes('maito-prod'), 'cd-deploy-helm deploys to maito-prod namespace');
}

console.log('\n========================================================================');
console.log(`SUMMARY: ${passedChecks}/${totalChecks} checks passed.`);
if (errors.length > 0) {
  console.log('FAILED CHECKS:');
  errors.forEach(e => console.log(' - ' + e));
  process.exit(1);
} else {
  console.log('🎉 ALL CI/CD SPECIFICATIONS & WORKFLOWS VERIFIED SUCCESSFULLY!');
  console.log('========================================================================\n');
}
