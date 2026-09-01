#!/usr/bin/env bash
# Quick resource-pack health check (local files + optional HTTP probes).
set -euo pipefail
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"
# shellcheck disable=SC1091
. "$SCRIPT_DIR/lib.sh"

yap_load_config
CFG="$ROOT/config/server.properties"

enabled="$(grep -E '^resource-pack-enabled=' "$CFG" 2>/dev/null | cut -d= -f2- || echo true)"
pack_dir="$ROOT/resourcepacks"
pack_file="$(grep -E '^resource-pack-files=' "$CFG" 2>/dev/null | cut -d= -f2- | cut -d, -f1 || true)"
[ -n "$pack_file" ] || pack_file="$(grep -E '^resource-pack-file=' "$CFG" 2>/dev/null | cut -d= -f2- || true)"
[ -n "$pack_file" ] || pack_file="yapcore-default.zip"
http_port="$(grep -E '^resource-pack-http-port=' "$CFG" 2>/dev/null | cut -d= -f2- || echo 8081)"
pack_url="$(grep -E '^resource-pack-url=' "$CFG" 2>/dev/null | cut -d= -f2- || true)"

echo "=== YaPcore pack verify ==="
echo "  enabled=$enabled"
echo "  active=$pack_file"
echo "  dir=$pack_dir"

if [ "$enabled" != "true" ] && [ "$enabled" != "1" ]; then
  echo "FAIL: resource-pack-enabled is not true in config/server.properties" >&2
  exit 1
fi

pack_path="$pack_dir/$pack_file"
if [ ! -f "$pack_path" ]; then
  echo "FAIL: pack missing on disk: $pack_path" >&2
  exit 1
fi

sha1="$(sha1sum "$pack_path" | awk '{print $1}')"
size="$(wc -c <"$pack_path")"
echo "  sha1=$sha1"
echo "  size=$size bytes"

local_url="http://127.0.0.1:${http_port}/pack/${pack_file}"
code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$local_url" 2>/dev/null || true)"
code="${code:-000}"
if [ "$code" = "200" ]; then
  echo "OK  local HTTP $local_url ($code)"
else
  echo "WARN local HTTP not reachable ($code) — start YaPcore or check resource-pack-http-port"
fi

if [ -n "$pack_url" ]; then
  pub="${pack_url//\{file\}/$pack_file}"
  pub_code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 8 "$pub" 2>/dev/null || echo 000)"
  if [ "$pub_code" = "200" ]; then
    echo "OK  public URL $pub ($pub_code)"
  else
    echo "WARN public URL probe $pub_code — publish the pack via nginx/CF if needed"
  fi
fi

manifest="$ROOT/plugins/YaPPacks/active.json"
if [ -f "$manifest" ]; then
  if grep -q "\"sha1\": \"$sha1\"" "$manifest" 2>/dev/null; then
    echo "OK  YaPPacks active.json sha1 matches"
  else
    echo "WARN YaPPacks active.json sha1 mismatch — restart YaPcore to rewrite manifest"
  fi
else
  echo "WARN no plugins/YaPPacks/active.json — restart YaPcore to generate"
fi

folia_props="$ROOT/folia-kernel/server.properties"
if [ -f "$folia_props" ]; then
  if grep -q "^resource-pack=." "$folia_props" 2>/dev/null; then
    echo "OK  folia-kernel/server.properties has resource-pack URL"
  else
    echo "WARN folia-kernel/server.properties has no resource-pack — restart YaPcore (Folia rewrites on boot)"
  fi
fi

echo "Done."
