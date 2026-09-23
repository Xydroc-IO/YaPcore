# yap-ultrawide

Fabric **client** mod for Minecraft **26.2**. It does not go on YaPcore or Folia.

On 21:9 and 32:9, vanilla’s vertical FOV slider becomes a fish-eye horizontal view.
This mod applies **Hor+** with **separate profiles** for each panel class. 16:9 is
unchanged. Spyglass / zoom FOVs pass through.

Hands share the **world** frustum (so blocks place on the crosshair). Hor+ lowers
vertical FOV, which would drop held items onto the hotbar. The viewmodel is
**scaled on Y** so hands, blocks, and items keep the vanilla gap above the hotbar,
and **nudged on X** so they stay on screen on extreme 32:9 panels. View-bob is
scaled with Hor+ zoom so the world does not slide under the crosshair while walking.

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
  "configVersion": 9,
  "enabled": true,
  "affectHudFov": true,
  "ultrawide_21_9": {
    "mode": "match_16_9",
    "targetHorizontalFov": 90.0,
    "maxHorizontalFov": 90.0,
    "fovScale": 1.0,
    "minVerticalFov": 40.0,
    "viewmodelOffsetX": 0.0,
    "viewmodelOffsetY": 0.0,
    "viewmodelExtraScale": 1.05
  },
  "superwide_32_9": {
    "mode": "match_21_9",
    "targetHorizontalFov": 105.0,
    "maxHorizontalFov": 105.0,
    "fovScale": 1.0,
    "minVerticalFov": 38.0,
    "viewmodelOffsetX": -0.08,
    "viewmodelOffsetY": 0.0,
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
| `affectHudFov` | Hor+ on first-person hands so they share the world frustum. Keep **true**. The mod scales the viewmodel on Y so held items keep the vanilla gap above the hotbar. `false` restores vanilla 70° hands (can look like you are aiming off the crosshair) |

### Per-band keys

| Key | Meaning |
|-----|---------|
| `mode` | `match_16_9` · `match_21_9` · `fixed_hfov` |
| `targetHorizontalFov` | Locked HFOV when mode is `fixed_hfov` |
| `maxHorizontalFov` | Hard HFOV cap (`0` = off) |
| `fovScale` | Extra tighten (`0.90`–`1.0`) if edges still stretch |
| `minVerticalFov` | Floor on vertical FOV so the view never telephotos (hand/world vanishing). `0` = off |
| `viewmodelOffsetX` | Extra hand nudge (negative = left). Stacks on auto aspect offset |
| `viewmodelOffsetY` | Extra lift after the automatic FOV scale (positive = up). `0` is the vanilla gap above the hotbar |
| `viewmodelExtraScale` | Multiply hand size (`>1` = bigger on screen) |

### 75" / dual-4K 32:9 tips

Use `superwide_32_9` only (your panel is detected as that band). Defaults start
from a **21:9** horizontal match, then cap at **105°** so the edges do not stretch,
and pull the hand inward. Height above the hotbar follows
the FOV automatically. If the hand is still clipped on the right: lower
`viewmodelOffsetX` (e.g. `-0.15`). If it still sits on the hotbar: raise
`viewmodelOffsetY` (e.g. `0.06`). If edges still fish-eye: drop `maxHorizontalFov`
toward `95`. If the world feels too zoomed: raise `maxHorizontalFov` toward `115`.
