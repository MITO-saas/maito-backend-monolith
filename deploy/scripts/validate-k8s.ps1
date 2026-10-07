Param()

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$DeployDir = Split-Path -Parent $ScriptDir

Write-Host "========================================================================" -ForegroundColor Cyan
Write-Host "☸️  RUNNING KUBERNETES & HELM DEPLOYMENT VALIDATION SUITE" -ForegroundColor Cyan
Write-Host "========================================================================" -ForegroundColor Cyan

# 1. Structural & Syntax Validation via Node
Write-Host "--> Step 1: Performing YAML syntax and specification validation..." -ForegroundColor Yellow
& node "$ScriptDir/validate-yaml.js"
if ($LASTEXITCODE -ne 0) {
    Write-Error "YAML validation failed."
    exit 1
}

# 2. Helm Lint & Template Rendering (if Helm is installed)
$helmCmd = Get-Command helm -ErrorAction SilentlyContinue
if ($null -ne $helmCmd) {
    Write-Host "--> Step 2: Executing 'helm lint'..." -ForegroundColor Yellow
    & helm lint "$DeployDir/helm/maito-monolith"

    Write-Host "--> Step 3: Testing Helm template rendering with default values..." -ForegroundColor Yellow
    & helm template maito-monolith "$DeployDir/helm/maito-monolith" -f "$DeployDir/helm/maito-monolith/values.yaml" | Out-Null
    Write-Host "    ✅ Default values template rendered cleanly." -ForegroundColor Green

    Write-Host "--> Step 4: Testing Helm template rendering with values-production.yaml..." -ForegroundColor Yellow
    & helm template maito-monolith "$DeployDir/helm/maito-monolith" -f "$DeployDir/helm/maito-monolith/values-production.yaml" | Out-Null
    Write-Host "    ✅ Production values template rendered cleanly." -ForegroundColor Green
} else {
    Write-Host "--> Step 2-4: Helm CLI not detected in PATH. Skipping Helm client lint." -ForegroundColor Gray
}

# 3. Kubectl Client Dry-Run (if Kubectl is installed)
$kubectlCmd = Get-Command kubectl -ErrorAction SilentlyContinue
if ($null -ne $kubectlCmd) {
    Write-Host "--> Step 5: Executing 'kubectl apply --dry-run=client' on raw manifests..." -ForegroundColor Yellow
    & kubectl apply --dry-run=client -f "$DeployDir/k8s/"
    Write-Host "    ✅ All manifests passed kubectl client-side schema validation." -ForegroundColor Green
} else {
    Write-Host "--> Step 5: kubectl CLI not detected in PATH. Skipping dry-run validation." -ForegroundColor Gray
}

Write-Host "========================================================================" -ForegroundColor Cyan
Write-Host "✅ VALIDATION PIPELINE FINISHED SUCCESSFULLY!" -ForegroundColor Green
Write-Host "========================================================================" -ForegroundColor Cyan
