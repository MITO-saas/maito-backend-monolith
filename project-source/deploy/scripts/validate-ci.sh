#!/usr/bin/env bash
# Maito Backend Monolith - CI/CD Workflow & GitHub Actions Validation Script (POSIX Bash)
set -eo pipefail

echo "========================================================================"
echo "🚀 RUNNING CI/CD WORKFLOW VALIDATION SUITE"
echo "========================================================================"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VALIDATOR_SCRIPT="${SCRIPT_DIR}/validate-ci-yaml.js"

if [ -f "${VALIDATOR_SCRIPT}" ]; then
    node "${VALIDATOR_SCRIPT}"
else
    echo "❌ Validator script ${VALIDATOR_SCRIPT} not found!"
    exit 1
fi

echo "✨ CI/CD VALIDATION PIPELINE FINISHED SUCCESSFULLY!"
echo "========================================================================"
