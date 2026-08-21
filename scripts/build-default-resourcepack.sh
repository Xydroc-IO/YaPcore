#!/usr/bin/env bash
# Build resourcepacks/yapcore-default.zip
# Default (CORE): Faithful 64x only (or empty stub).
# With YAP_INCLUDE_VEHICLES=1|true: overlay YaP Vehicles (GAMEPLAY tier).
set -eu
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
PACKS="$ROOT/resourcepacks"
OUT="$PACKS/yapcore-default.zip"
VEH_DIR="$PACKS/yap-vehicles"
VEH_ZIP="$PACKS/yap-vehicles.zip"
FAITHFUL="$PACKS/faithful-64x.zip"
INCLUDE_VEHICLES="${YAP_INCLUDE_VEHICLES:-0}"
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT

want_vehicles=0
case "$INCLUDE_VEHICLES" in
  1|true|TRUE|yes|YES) want_vehicles=1 ;;
esac

# Base: Faithful if available, else empty pack
if [ -f "$FAITHFUL" ]; then
  unzip -q -o "$FAITHFUL" -d "$STAGE"
else
  mkdir -p "$STAGE"
  printf '%s\n' '{"pack":{"pack_format":34,"description":"YaPcore default client pack"}}' \
    >"$STAGE/pack.mcmeta"
fi

DESC="YaPcore default — Faithful 64x (CORE)"
if [ "$want_vehicles" -eq 1 ]; then
  if [ -d "$VEH_DIR" ]; then
    (cd "$VEH_DIR" && zip -qr "$VEH_ZIP" .)
  fi
  if [ ! -f "$VEH_ZIP" ]; then
    echo "ERROR: YAP_INCLUDE_VEHICLES set but missing $VEH_ZIP (and no yap-vehicles/ folder)" >&2
    exit 1
  fi
  unzip -q -o "$VEH_ZIP" -d "$STAGE"
  DESC="YaPcore default — Faithful 64x + YaP Vehicles (GAMEPLAY)"
fi

python3 - <<PY "$STAGE" "$DESC" "$want_vehicles"
import json, sys
from pathlib import Path
stage = Path(sys.argv[1])
desc = sys.argv[2]
want_vehicles = sys.argv[3] == "1"

if want_vehicles:
    paper = stage / "assets/minecraft/models/item/paper.json"
    if paper.exists():
        data = json.loads(paper.read_text())
        overs = data.get("overrides") or []
        overs.sort(key=lambda o: o.get("predicate", {}).get("custom_model_data", 0))
        data["overrides"] = overs
        if "textures" not in data:
            data["textures"] = {"layer0": "item/paper"}
        if "parent" not in data:
            data["parent"] = "item/generated"
        paper.write_text(json.dumps(data, indent=2) + "\n")

meta = stage / "pack.mcmeta"
meta.write_text(json.dumps({
    "pack": {
        "pack_format": 34,
        "supported_formats": {"min_inclusive": 22, "max_inclusive": 99},
        "description": desc
    }
}, indent=2) + "\n")
PY

rm -f "$OUT"
(cd "$STAGE" && zip -qr "$OUT" .)
echo "Wrote $OUT ($(du -h "$OUT" | awk '{print $1}')) vehicles=$want_vehicles"

# Mirror into nginx docroot for Cloudflare :80 /pack/ (when available)
if [ -x "$ROOT/scripts/publish-resourcepack-www.sh" ]; then
  "$ROOT/scripts/publish-resourcepack-www.sh" "$OUT" || true
fi
