#!/usr/bin/env bash
# scripts/check-status.sh -- convenient wrapper around status_verifier.py
# Run from project-source/:   ./check-status.sh [--auto|--verbose]

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if command -v python3 >/dev/null 2>&1; then
    python3 "$DIR/status_verifier.py" "$@"
elif command -v python >/dev/null 2>&1; then
    python "$DIR/status_verifier.py" "$@"
else
    echo "error: python3 or python is required but not found on PATH" >&2
    exit 1
fi
