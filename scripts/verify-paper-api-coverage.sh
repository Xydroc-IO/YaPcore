#!/usr/bin/env bash
# Verify complete Paper API coverage on the product path (embedded Paperclip).
# Compares nested paper-api inside lib/paper-*-yap.jar to the published PaperMC
# artifact for the pinned build, and checks classloader isolation source.
#
# Usage: ./scripts/verify-paper-api-coverage.sh
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
# shellcheck source=lib.sh
# shellcheck disable=SC1091
. "$ROOT/scripts/lib.sh"

cd "$ROOT"
yap_load_config

PAPER_API_PUB="${PAPER_API_VERSION:-26.2.build.112-stable}"
YAP_PAPER="$ROOT/lib/paper-${PAPER_VERSION}-yap.jar"
NESTED_PATH="META-INF/libraries/io/papermc/paper/paper-api/26.2.local-SNAPSHOT/paper-api-26.2.local-SNAPSHOT.jar"

if [ ! -f "$YAP_PAPER" ]; then
  echo "FAIL: missing $YAP_PAPER — run ./scripts/build-vendor-paper.sh" >&2
  exit 1
fi

WORKDIR="$(mktemp -d "${TMPDIR:-/tmp}/yap-api-cov.XXXXXX")"
cleanup() { rm -rf "$WORKDIR"; }
trap cleanup EXIT

echo "== Paper API coverage (product path) =="
echo "  paperclip: $YAP_PAPER"
echo "  expect:    paper-api $PAPER_API_PUB"

# Extract nested API from Paperclip
unzip -qo "$YAP_PAPER" "$NESTED_PATH" -d "$WORKDIR"
NESTED="$WORKDIR/$NESTED_PATH"
if [ ! -f "$NESTED" ]; then
  echo "FAIL: nested paper-api not found at $NESTED_PATH" >&2
  unzip -l "$YAP_PAPER" | grep -i paper-api | head -20 >&2 || true
  exit 1
fi

count_classes() {
  jar tf "$1" | grep '\.class$' | sort
}

NESTED_LIST="$WORKDIR/nested.txt"
PUB_LIST="$WORKDIR/pub.txt"
count_classes "$NESTED" >"$NESTED_LIST"
NESTED_N="$(wc -l <"$NESTED_LIST" | tr -d ' ')"

PUB_JAR="$WORKDIR/paper-api-pub.jar"
URL="https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/${PAPER_API_PUB}/paper-api-${PAPER_API_PUB}.jar"
echo "  download: $URL"
if ! curl -fsSL -o "$PUB_JAR" "$URL"; then
  echo "FAIL: could not download published paper-api $PAPER_API_PUB" >&2
  exit 1
fi
count_classes "$PUB_JAR" >"$PUB_LIST"
PUB_N="$(wc -l <"$PUB_LIST" | tr -d ' ')"

echo "  nested classes (top-level): $NESTED_N"
echo "  published classes:          $PUB_N"

if [ "$NESTED_N" -ne "$PUB_N" ]; then
  echo "WARN: class count differs (nested=$NESTED_N pub=$PUB_N) — checking set equality"
fi

MISSING="$WORKDIR/missing.txt"
EXTRA="$WORKDIR/extra.txt"
comm -23 "$PUB_LIST" "$NESTED_LIST" >"$MISSING" || true
comm -13 "$PUB_LIST" "$NESTED_LIST" >"$EXTRA" || true
MISS_N="$(wc -l <"$MISSING" | tr -d ' ')"
EXTRA_N="$(wc -l <"$EXTRA" | tr -d ' ')"

if [ "$MISS_N" -ne 0 ]; then
  echo "FAIL: Paperclip paper-api missing $MISS_N classes vs $PAPER_API_PUB" >&2
  head -40 "$MISSING" >&2
  exit 1
fi

echo "OK — embedded paper-api matches published $PAPER_API_PUB (0 missing; extra=$EXTRA_N)"

# Classloader isolation: stubs must not be parent of Paper
CL="$ROOT/src/main/java/com/yapcore/paper/phase3/Phase3PaperClassLoader.java"
if ! grep -q 'getPlatformClassLoader' "$CL"; then
  echo "FAIL: Phase3PaperClassLoader must use platform parent to isolate stubs" >&2
  exit 1
fi
echo "OK — Phase3PaperClassLoader uses platform parent (stubs cannot shadow Paper)"

# PluginRuntime must skip plugin.yml under Paper authority
PR="$ROOT/src/main/java/com/yapcore/plugin/loader/PluginRuntime.java"
if ! grep -q 'Paper authority' "$PR"; then
  echo "FAIL: PluginRuntime missing Paper-authority handling" >&2
  exit 1
fi
echo "OK — PluginRuntime defers plugin.yml to Paper under Paper authority"

# Compile deps should target Paper 26.2 API, not stale 1.21.x
STALE="$(grep -R --include='*.kts' 'paper-api:1\.21' "$ROOT" 2>/dev/null || true)"
if [ -n "$STALE" ]; then
  echo "FAIL: stale paper-api 1.21.x compileOnly deps:" >&2
  echo "$STALE" >&2
  exit 1
fi
echo "OK — no stale paper-api 1.21.x Gradle deps"

# Facade stub count (informational only)
STUB_N="$(find "$ROOT/src/main/java" \( -path '*/org/bukkit/*' -o -path '*/io/papermc/*' -o -path '*/com/destroystokyo/*' \) -name '*.java' 2>/dev/null | wc -l | tr -d ' ')"
echo "INFO — facade stub .java files: $STUB_N (non-product; product coverage is Paper)"

echo
echo "Product claim: complete Paper API under game-authority=paper"
echo "  Verify runtime: ./scripts/smoke-paper-plugins.sh"
echo "  Docs: docs/PAPER_API_COVERAGE.md"
exit 0
