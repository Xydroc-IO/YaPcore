#!/usr/bin/env bash
# Regenerate facade event stubs from Paper API sources (non-product path).
# Product plugins use real Paper — see docs/PAPER_API_COVERAGE.md.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORKDIR="${TMPDIR:-/tmp}/yap-paper-stubs"
# Match vendor/paper.pin / gradle.properties paperApiVersion
VER="${PAPER_API_VERSION:-26.2.build.112-stable}"
URL="https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/${VER}/paper-api-${VER}-sources.jar"
mkdir -p "$WORKDIR" && cd "$WORKDIR"
echo "Fetching paper-api sources $VER …"
curl -fsSL -o paper-api-sources.jar "$URL"
rm -rf paper-src && mkdir paper-src && unzip -q -o paper-api-sources.jar -d paper-src
python3 "$ROOT/scripts/generate_paper_event_stubs.py" "$WORKDIR/paper-src" "$ROOT/src/main/java"
echo "Done (facade stubs only). Product coverage is embedded Paper — ./scripts/verify-paper-api-coverage.sh"
echo "Compile: gradle compileJava"
