#!/usr/bin/env bash
# Build vendored Paper and install Paperclip as lib/paper-${mc}-yap.jar
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
PIN="$ROOT/vendor/paper.pin"
DEST="$ROOT/vendor/paper"
PATCH_HELPER="$ROOT/scripts/apply-yap-paper-hooks.sh"

if [[ ! -d "$DEST/.git" ]]; then
  echo "vendor/paper missing — run scripts/vendor-paper.sh first" >&2
  exit 1
fi

mc="$(grep '^mc=' "$PIN" | cut -d= -f2-)"
artifact_rel="$(grep '^artifact=' "$PIN" | cut -d= -f2-)"
ARTIFACT="$ROOT/$artifact_rel"

cd "$DEST"
export GRADLE_OPTS="${GRADLE_OPTS:--Xmx4g}"

echo "Applying Paper patches…"
./gradlew paper-server:applyPatches --no-daemon

if [[ -x "$PATCH_HELPER" ]]; then
  "$PATCH_HELPER"
fi

echo "Building Paperclip…"
./gradlew paper-server:createPaperclipJar --no-daemon

JAR=""
if [[ -d "$DEST/paper-server/build/libs" ]]; then
  # Prefer runnable paperclip over bundler/server jars
  JAR="$(ls -1t "$DEST"/paper-server/build/libs/paper-paperclip-*.jar 2>/dev/null | head -n 1 || true)"
  if [[ -z "${JAR:-}" ]]; then
    JAR="$(ls -1t "$DEST"/paper-server/build/libs/paper-*.jar 2>/dev/null | grep -v -- '-sources' | grep -v -- '-javadoc' | grep -v bundler | grep -v 'paper-server-' | head -n 1 || true)"
  fi
fi

if [[ -z "${JAR:-}" || ! -f "$JAR" ]]; then
  echo "Could not locate built Paperclip jar under paper-server/build/libs" >&2
  exit 1
fi

mkdir -p "$(dirname "$ARTIFACT")"
cp -f "$JAR" "$ARTIFACT"
# Also stage into paper-kernel for immediate use
mkdir -p "$ROOT/paper-kernel"
/bin/cp -f "$ARTIFACT" "$ROOT/paper-kernel/paper-${mc}.jar"
echo "Installed vendored Paperclip → $ARTIFACT ($(wc -c < "$ARTIFACT") bytes)"
