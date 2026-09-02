#!/usr/bin/env bash
# Build resourcepacks/yapcore-default.zip
# Default (CORE): Faithful 64x + YaP Skies (or empty stub).
# With YAP_INCLUDE_VEHICLES=1|true: overlay YaP Vehicles + YaP Abilities (GAMEPLAY).
set -eu
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
PACKS="$ROOT/resourcepacks"
OUT="$PACKS/yapcore-default.zip"
VEH_DIR="$PACKS/yap-vehicles"
VEH_ZIP="$PACKS/yap-vehicles.zip"
ABIL_DIR="$PACKS/yap-abilities"
ABIL_ZIP="$PACKS/yap-abilities.zip"
SKIES_DIR="$PACKS/yap-skies"
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

# Realistic sun / moon / clouds + atmosphere + water (CORE).
if [ ! -f "$SKIES_DIR/assets/minecraft/textures/environment/celestial/sun.png" ]; then
  python3 "$ROOT/scripts/generate-yap-skies.py"
fi
# Always refresh YaP water / weather overlays (fast; biome-tint grayscale).
python3 "$ROOT/scripts/generate-yap-water.py"
if [ -d "$SKIES_DIR/assets" ]; then
  mkdir -p "$STAGE/assets"
  cp -a "$SKIES_DIR/assets/." "$STAGE/assets/"
fi

DESC="YaPcore default — Faithful 64x + YaP Skies + YaP Water (CORE)"
if [ "$want_vehicles" -eq 1 ]; then
  # Same path as vehicles: zip the overlay tree, then merge into the default pack.
  if [ -d "$ABIL_DIR/assets" ]; then
    (cd "$ABIL_DIR" && zip -qr "$ABIL_ZIP" assets pack.mcmeta)
  fi
  if [ ! -f "$ABIL_ZIP" ]; then
    echo "ERROR: YAP_INCLUDE_VEHICLES set but missing $ABIL_ZIP (and no yap-abilities/ folder)" >&2
    exit 1
  fi
  unzip -q -o "$ABIL_ZIP" -d "$STAGE"
  if [ -d "$VEH_DIR" ]; then
    (cd "$VEH_DIR" && zip -qr "$VEH_ZIP" .)
  fi
  if [ ! -f "$VEH_ZIP" ]; then
    echo "ERROR: YAP_INCLUDE_VEHICLES set but missing $VEH_ZIP (and no yap-vehicles/ folder)" >&2
    exit 1
  fi
  unzip -q -o "$VEH_ZIP" -d "$STAGE"
  DESC="YaPcore default — Faithful 64x + YaP Skies + YaP Water + Vehicles + MMO icons (GAMEPLAY)"
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

    clay = stage / "assets/minecraft/models/item/clay_ball.json"
    if clay.exists():
        data = json.loads(clay.read_text())
        overs = data.get("overrides") or []
        overs.sort(key=lambda o: o.get("predicate", {}).get("custom_model_data", 0))
        data["overrides"] = overs
        if "textures" not in data:
            data["textures"] = {"layer0": "item/clay_ball"}
        if "parent" not in data:
            data["parent"] = "item/generated"
        clay.write_text(json.dumps(data, indent=2) + "\n")

    # Strip legacy ability overrides from blaze_rod so staffs stay vanilla
    rod = stage / "assets/minecraft/models/item/blaze_rod.json"
    if rod.exists():
        data = json.loads(rod.read_text())
        overs = [o for o in (data.get("overrides") or [])
                 if not str(o.get("model", "")).startswith("yapabilities:")]
        if overs:
            data["overrides"] = sorted(
                overs, key=lambda o: o.get("predicate", {}).get("custom_model_data", 0))
            rod.write_text(json.dumps(data, indent=2) + "\n")
        else:
            rod.unlink(missing_ok=True)

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
if [[ -x "$ROOT/scripts/sync-pack-to-nginx.sh" ]]; then
  "$ROOT/scripts/sync-pack-to-nginx.sh" || true
fi
echo "NOTE: restart Folia (or YaPcore) so server.properties resource-pack-sha1 matches the new zip."
