#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "========================================================================"
echo "☸️  RUNNING KUBERNETES & HELM DEPLOYMENT VALIDATION SUITE"
echo "========================================================================"

# 1. Structural & Syntax Validation via Node
echo "--> Step 1: Performing YAML syntax and specification validation..."
node "${SCRIPT_DIR}/validate-yaml.js"

# 2. Helm Lint & Template Rendering (if Helm is installed)
if command -v helm &> /dev/null; then
    echo "--> Step 2: Executing 'helm lint'..."
    helm lint "${DEPLOY_DIR}/helm/maito-monolith"

    echo "--> Step 3: Testing Helm template rendering with default values..."
    helm template maito-monolith "${DEPLOY_DIR}/helm/maito-monolith" -f "${DEPLOY_DIR}/helm/maito-monolith/values.yaml" > /dev/null
    echo "    ✅ Default values template rendered cleanly."

    echo "--> Step 4: Testing Helm template rendering with values-production.yaml..."
    helm template maito-monolith "${DEPLOY_DIR}/helm/maito-monolith" -f "${DEPLOY_DIR}/helm/maito-monolith/values-production.yaml" > /dev/null
    echo "    ✅ Production values template rendered cleanly."
else
    echo "--> Step 2-4: Helm CLI not detected in PATH. Skipping Helm client lint."
fi

# 3. Kubectl Client Dry-Run (if Kubectl is installed)
if command -v kubectl &> /dev/null; then
    echo "--> Step 5: Executing 'kubectl apply --dry-run=client' on raw manifests..."
    kubectl apply --dry-run=client -f "${DEPLOY_DIR}/k8s/"
    echo "    ✅ All manifests passed kubectl client-side schema validation."
else
    echo "--> Step 5: kubectl CLI not detected in PATH. Skipping dry-run validation."
fi

echo "========================================================================"
echo "✅ VALIDATION PIPELINE FINISHED SUCCESSFULLY!"
echo "========================================================================"
