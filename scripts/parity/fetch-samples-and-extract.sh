#!/usr/bin/env bash
# Fetch official Mojang bedrock-samples for the pinned parity band and extract
# geometry + player animations into fixtures. Emote bone data is NOT in samples —
# set YAP_BEDROCK_VANILLA_RP to a full game resource pack for those.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BAND="${1:-band_26_50}"
# Map band_26_50 → Mojang samples tag for 1.26.50
TAG="${YAP_BEDROCK_SAMPLES_TAG:-v1.26.50.27-preview}"
CACHE="${YAP_BEDROCK_SAMPLES_DIR:-/tmp/yap-bedrock-samples/full-unpacked/resource_pack}"

if [[ ! -d "$CACHE/animations" ]]; then
  echo "Fetching Mojang bedrock-samples $TAG …"
  WORK=/tmp/yap-bedrock-samples
  mkdir -p "$WORK"
  ZIP="$WORK/full.zip"
  URL="https://github.com/Mojang/bedrock-samples/releases/download/${TAG}/bedrock-samples-${TAG}-full.zip"
  # tag includes v prefix already in asset name pattern
  URL="https://github.com/Mojang/bedrock-samples/releases/download/${TAG}/bedrock-samples-${TAG}-full.zip"
  curl -fsL -o "$ZIP" "$URL"
  rm -rf "$WORK/full-unpacked"
  mkdir -p "$WORK/full-unpacked"
  unzip -q -o "$ZIP" -d "$WORK/full-unpacked"
  CACHE="$WORK/full-unpacked/resource_pack"
fi

export YAP_BEDROCK_SAMPLES_RP="$CACHE"
if [[ -n "${YAP_BEDROCK_VANILLA_RP:-}" ]]; then
  echo "Also using YAP_BEDROCK_VANILLA_RP=$YAP_BEDROCK_VANILLA_RP for emotes"
fi

cd "$ROOT"
if [[ -x ./gradlew ]]; then GW=./gradlew; else GW=gradle; fi
"$GW" -q parityExtract -PparityBand="$BAND" -PparityRoot="$ROOT"
"$GW" -q parityConvert -PparityBand="$BAND" -PparityRoot="$ROOT"
echo "Done. Samples RP=$CACHE"
