# yap-ultrawide

Fabric **client** mod for Minecraft **26.2**. It does not go on YaPcore or Folia.

On 21:9 and 32:9, vanilla’s vertical FOV slider becomes a fish-eye horizontal view.
This mod applies **Hor+** with **separate profiles** for each panel class. 16:9 is
unchanged. Spyglass / zoom FOVs pass through.

Hands share the **world** frustum (so blocks place on the crosshair). The viewmodel
is **scaled** and **nudged** (aspect-aware) so held items stay on screen on extreme
32:9 panels. View-bob is scaled with Hor+ zoom so the world does not slide under
the crosshair while walking.

## Install

1. Fabric Loader **0.19.3+** for Minecraft **26.2**
2. Drop `yap-ultrawide-*.jar` into `.minecraft/mods/`

## Build

```bash
cd client/yap-ultrawide && ./gradlew build
```

## Config

`.minecraft/config/yap-ultrawide.json`:

```json
{
  "configVersion": 7,
  "enabled": true,
  "affectHudFov": true,
  "ultrawide_21_9": {
    "mode": "match_16_9",
    "targetHorizontalFov": 100.0,
    "maxHorizontalFov": 100.0,
    "fovScale": 0.98,
    "minVerticalFov": 50.0,
    "viewmodelOffsetX": 0.0,
    "viewmodelOffsetY": 0.0,
    "viewmodelExtraScale": 1.05
  },
  "superwide_32_9": {
    "mode": "match_21_9",
    "targetHorizontalFov": 120.0,
    "maxHorizontalFov": 128.0,
    "fovScale": 1.0,
    "minVerticalFov": 48.0,
    "viewmodelOffsetX": -0.08,
    "viewmodelOffsetY": 0.06,
    "viewmodelExtraScale": 1.22
  }
}
```

| Band | Typical panels |
|------|----------------|
| `ultrawide_21_9` | 2560×1080, 3440×1440, 3840×1600 (aspect ≈1.90–2.80) |
| `superwide_32_9` | 3840×1080, 5120×1440, 7680×2160 / 75" dual-4K (aspect ≥2.80) |

### Global keys

| Key | Meaning |
|-----|---------|
| `affectHudFov` | Hor+ on first-person hands so they share the world frustum. Keep **true**; the mod scales + nudges the viewmodel so weapons stay visible. `false` restores vanilla 70° hands (can look like you are aiming off the crosshair) |

### Per-band keys

| Key | Meaning |
|-----|---------|
| `mode` | `match_16_9` · `match_21_9` · `fixed_hfov` |
| `targetHorizontalFov` | Locked HFOV when mode is `fixed_hfov` |
| `maxHorizontalFov` | Hard HFOV cap (`0` = off) |
| `fovScale` | Extra tighten (`0.90`–`1.0`) if edges still stretch |
| `minVerticalFov` | Floor on vertical FOV so the view never telephotos (hand/world vanishing). `0` = off |
| `viewmodelOffsetX` | Extra hand nudge (negative = left). Stacks on auto aspect offset |
| `viewmodelOffsetY` | Extra hand nudge (positive = up) |
| `viewmodelExtraScale` | Multiply hand size (`>1` = bigger on screen) |

### 75" / dual-4K 32:9 tips

Use `superwide_32_9` only (your panel is detected as that band). Defaults match
**21:9** horizontal FOV, pull the hand inward, and enlarge the viewmodel so
swords stay visible. If the hand is still clipped: lower `viewmodelOffsetX`
(e.g. `-0.15`) and raise `viewmodelExtraScale` (e.g. `1.35`). If edges still
fish-eye: drop `maxHorizontalFov` toward `115`. If still too zoomed:
`"maxHorizontalFov": 0` / raise vanilla FOV. If too wide: `"mode": "match_16_9"`
with `"minVerticalFov": 48`.
