#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORKDIR="${TMPDIR:-/tmp}/yap-paper-stubs"
VER="1.21.4-R0.1-20250925.065901-231"
URL="https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/1.21.4-R0.1-SNAPSHOT/paper-api-${VER}-sources.jar"
mkdir -p "$WORKDIR" && cd "$WORKDIR"
curl -fsSL -o paper-api-sources.jar "$URL"
rm -rf paper-src && mkdir paper-src && unzip -q -o paper-api-sources.jar -d paper-src
python3 "$ROOT/scripts/generate_paper_event_stubs.py" "$WORKDIR/paper-src" "$ROOT/src/main/java"
echo "Done. Run: gradle compileJava"
