#!/usr/bin/env bash
# Smoke: Folia + native YaP Link (phases 0–2) — modern forwarding + ping passthrough path.
# Usage: ./scripts/smoke-yap-link-native.sh [seconds]
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
exec "$ROOT/scripts/smoke-yap-link-folia.sh" "$@"
