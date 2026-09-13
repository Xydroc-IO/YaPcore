#!/usr/bin/env bash
# Extract free persona emote bone anims from an installed Bedrock game into parity fixtures.
# Auto-detects bedrock-on-linux under ~/.local/share/bedrock-on-linux when YAP_BEDROCK_PERSONA_PIECES unset.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BAND="${1:-band_26_50}"
OUT="$ROOT/src/main/resources/protocol/bedrock/parity/$BAND/fixtures/emote"
mkdir -p "$OUT"

PIECES="${YAP_BEDROCK_PERSONA_PIECES:-}"
if [[ -z "$PIECES" ]]; then
  CAND=$(find "$HOME/.local/share/bedrock-on-linux/games/release" -type d -path '*/resource_packs/persona/pieces' 2>/dev/null | sort -V | tail -1 || true)
  PIECES="$CAND"
fi
if [[ -z "$PIECES" || ! -d "$PIECES" ]]; then
  echo "No persona/pieces found. Set YAP_BEDROCK_PERSONA_PIECES=/path/to/persona/pieces" >&2
  exit 1
fi
echo "Using persona pieces: $PIECES"

python3 - "$PIECES" "$OUT" "$ROOT/src/main/resources/protocol/bedrock/parity/$BAND/catalogs/emotes.v1.json" <<'PY'
import json, sys
from pathlib import Path

pieces = Path(sys.argv[1])
out = Path(sys.argv[2])
catalog = json.loads(Path(sys.argv[3]).read_text())
want = {e["id"]: e for e in catalog["entries"]}

def slugify(name: str) -> str:
    s = "".join(ch if ch.isalnum() else "_" for ch in name.lower())
    while "__" in s:
        s = s.replace("__", "_")
    return s.strip("_")

def duration(anim_doc, anim_name):
    anims = anim_doc.get("animations") or {}
    a = anims.get(anim_name) or (next(iter(anims.values())) if anims else {})
    if not isinstance(a, dict):
        return 2.0
    if "animation_length" in a:
        return float(a["animation_length"])
    mx = 0.0
    for bone in (a.get("bones") or {}).values():
        if not isinstance(bone, dict):
            continue
        for ch in ("rotation", "position", "scale"):
            track = bone.get(ch)
            if isinstance(track, dict):
                for t in track:
                    try:
                        mx = max(mx, float(t))
                    except Exception:
                        pass
    return mx if mx > 0 else 2.0

found = {}
for d in sorted(pieces.glob("emote_*")):
    for meta_path in d.glob("*.meta.json"):
        meta = json.loads(meta_path.read_text())
        pid = meta.get("piece_id")
        if pid not in want:
            continue
        anim_file = None
        anim_name = None
        for s in meta.get("animation_sources") or []:
            anim_file = s.get("animationFile")
            anim_name = s.get("name")
        if not anim_file:
            cands = list(d.glob("*.animation.json"))
            if not cands:
                continue
            anim_file = cands[0].name
        anim_doc = json.loads((d / anim_file).read_text())
        if not anim_name:
            anim_name = next(iter((anim_doc.get("animations") or {}).keys()), "")
        fixture = {
            "format_version": "1.10.0",
            "emote_id": pid,
            "name": want[pid]["name"],
            "animation": anim_name,
            "duration_seconds": duration(anim_doc, anim_name),
            "animation_data": anim_doc,
            "_yap_source": f"persona/pieces/{d.name}/{anim_file}",
            "_yap_piece_name": meta.get("piece_name"),
        }
        found[pid] = fixture
        slug = slugify(want[pid]["name"])
        (out / f"{slug}.emote.json").write_text(json.dumps(fixture, indent=2) + "\n")
        print(f"wrote {slug}.emote.json <- {d.name}/{anim_file}")

missing = [want[i]["name"] for i in want if i not in found]
if missing:
    print("MISSING catalog emotes (not in persona/pieces):", ", ".join(missing), file=sys.stderr)
    sys.exit(2)
print(f"OK {len(found)}/{len(want)} emote fixtures")
PY
