#!/usr/bin/env bash
# Maito Backend Monolith - Load & Concurrency Test Runner (POSIX Bash)
set -eo pipefail

SCENARIO="${1:-all}"
BASE_URL="${BASE_URL:-http://localhost:8080}"
TENANT="${TENANT_ID:-mito_crunch}"

echo "========================================================================"
echo "🔥 MAITO BACKEND LOAD & CONCURRENCY TEST RUNNER"
echo "========================================================================"
echo "Target Base URL: ${BASE_URL}"
echo "Target Tenant:   ${TENANT}"
echo "Scenario:        ${SCENARIO}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCENARIOS_DIR="${SCRIPT_DIR}/scenarios"

if ! command -v k6 &> /dev/null; then
    echo ""
    echo "⚠️  k6 CLI is not detected in your PATH!"
    echo "ℹ️  Install via: sudo apt-get install k6 or brew install k6"
    echo "--> Running offline specification and syntax verification..."
    node "${SCRIPT_DIR}/validate-load-and-chaos.js"
    exit $?
fi

run_k6() {
    local name="$1"
    local file="$2"
    echo ""
    echo "--> Executing k6 Scenario: ${name} (${file})"
    k6 run --env BASE_URL="${BASE_URL}" --env TENANT_ID="${TENANT}" "${SCENARIOS_DIR}/${file}"
    echo "✅ Scenario ${name} passed all thresholds!"
}

case "${SCENARIO}" in
    flash-sale)
        run_k6 "Flash Sale 10,000 VUs Checkout" "flash-sale-checkout.js"
        ;;
    browse-search)
        run_k6 "5,000 RPS Browse and Search" "browse-and-search.js"
        ;;
    rate-limiter)
        run_k6 "Rate Limiter Token Bucket Stress" "rate-limiter-stress.js"
        ;;
    all)
        run_k6 "Flash Sale 10,000 VUs Checkout" "flash-sale-checkout.js"
        run_k6 "5,000 RPS Browse and Search" "browse-and-search.js"
        run_k6 "Rate Limiter Token Bucket Stress" "rate-limiter-stress.js"
        ;;
    *)
        echo "Unknown scenario '${SCENARIO}'. Options: flash-sale, browse-search, rate-limiter, all"
        exit 1
        ;;
esac

echo ""
echo "🎉 ALL LOAD TEST SCENARIOS COMPLETED SUCCESSFULLY!"
echo "========================================================================"
