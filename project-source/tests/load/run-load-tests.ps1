# Maito Backend Monolith - Load and Concurrency Test Runner (PowerShell)
param (
    [string]$Scenario = "all",
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Tenant = "mito_crunch"
)

Write-Host "========================================================================" -ForegroundColor Cyan
Write-Host "MAITO BACKEND LOAD AND CONCURRENCY TEST RUNNER" -ForegroundColor Cyan
Write-Host "========================================================================" -ForegroundColor Cyan
Write-Host ("Target Base URL: " + $BaseUrl) -ForegroundColor Yellow
Write-Host ("Target Tenant:   " + $Tenant) -ForegroundColor Yellow
Write-Host ("Scenario:        " + $Scenario) -ForegroundColor Yellow

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ScenariosDir = Join-Path $ScriptDir "scenarios"

# Verify k6 CLI presence
$K6Installed = Get-Command k6 -ErrorAction SilentlyContinue

if (-not $K6Installed) {
    Write-Host ""
    Write-Host "k6 CLI is not detected in your PATH." -ForegroundColor Yellow
    Write-Host "To install k6:" -ForegroundColor Cyan
    Write-Host "  - Windows: choco install k6  or  winget install k6"
    Write-Host "  - macOS:   brew install k6"
    Write-Host "  - Linux:   sudo apt-get install k6"
    Write-Host ""
    Write-Host "--> Running offline specification and syntax verification..." -ForegroundColor Green
    node (Join-Path $ScriptDir "validate-load-and-chaos.js")
    exit $LASTEXITCODE
}

function Run-K6Scenario($name, $file) {
    Write-Host ""
    Write-Host ("--> Executing k6 Scenario: " + $name + " (" + $file + ")") -ForegroundColor Cyan
    $FilePath = Join-Path $ScenariosDir $file
    k6 run --env BASE_URL=$BaseUrl --env TENANT_ID=$Tenant $FilePath
    if ($LASTEXITCODE -ne 0) {
        Write-Host ("❌ Scenario " + $name + " failed SLAs / Thresholds!") -ForegroundColor Red
        exit 1
    }
    Write-Host ("✅ Scenario " + $name + " passed all thresholds!") -ForegroundColor Green
}

switch ($Scenario.ToLower()) {
    "flash-sale" {
        Run-K6Scenario "Flash Sale 10,000 VUs Checkout" "flash-sale-checkout.js"
    }
    "browse-search" {
        Run-K6Scenario "5,000 RPS Browse and Search" "browse-and-search.js"
    }
    "rate-limiter" {
        Run-K6Scenario "Rate Limiter Token Bucket Stress" "rate-limiter-stress.js"
    }
    "all" {
        Run-K6Scenario "Flash Sale 10,000 VUs Checkout" "flash-sale-checkout.js"
        Run-K6Scenario "5,000 RPS Browse and Search" "browse-and-search.js"
        Run-K6Scenario "Rate Limiter Token Bucket Stress" "rate-limiter-stress.js"
    }
    default {
        Write-Host ("Unknown scenario " + $Scenario + ". Options: flash-sale, browse-search, rate-limiter, all") -ForegroundColor Red
        exit 1
    }
}

Write-Host ""
Write-Host "ALL LOAD TEST SCENARIOS COMPLETED SUCCESSFULLY!" -ForegroundColor Green
Write-Host "========================================================================" -ForegroundColor Cyan
