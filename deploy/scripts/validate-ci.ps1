#!/usr/bin/env pwsh
# Maito Backend Monolith - CI/CD Workflow & GitHub Actions Validation Script (PowerShell)

Write-Host "========================================================================" -ForegroundColor Cyan
Write-Host "🚀 RUNNING CI/CD WORKFLOW VALIDATION SUITE" -ForegroundColor Cyan
Write-Host "========================================================================" -ForegroundColor Cyan

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ValidatorScript = Join-Path $ScriptDir "validate-ci-yaml.js"

if (Test-Path $ValidatorScript) {
    node $ValidatorScript
    if ($LASTEXITCODE -ne 0) {
        Write-Host "❌ CI/CD validation failed with errors!" -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "❌ Validator script $ValidatorScript not found!" -ForegroundColor Red
    exit 1
}

Write-Host "✨ CI/CD VALIDATION PIPELINE FINISHED SUCCESSFULLY!" -ForegroundColor Green
Write-Host "========================================================================" -ForegroundColor Cyan
