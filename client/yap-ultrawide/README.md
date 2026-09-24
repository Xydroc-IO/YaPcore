# yap-ultrawide

Fabric **client** mod for Minecraft **26.2**. It does not go on YaPcore or Folia.

On 21:9 and 32:9 the world uses its own camera. The Minecraft video-settings
FOV slider does not change that view. 16:9 is unchanged. Spyglass / zoom FOVs
pass through. Hands stay on the vanilla HUD camera, so the weapon stays in
the corner.

The width is `targetHorizontalFov` in the config below. Both bands default to
110° across, in a straight perspective. Raise that number to see more to the
sides. Lower it when the sides stretch. View-bob is scaled with the camera
so the world does not slide under the crosshair while walking.

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
  "configVersion": 18,
  "enabled": true,
  "affectHudFov": false,
  "edgeCorrect": 0.0,
  "ultrawide_21_9": {
    "mode": "ultrawide",
    "targetHorizontalFov": 110.0,
    "maxHorizontalFov": 0.0,
    "fovScale": 1.0,
    "minVerticalFov": 0.0,
    "viewmodelOffsetX": 0.0,
    "viewmodelOffsetY": 0.0,
    "viewmodelExtraScale": 1.0
  },
  "superwide_32_9": {
    "mode": "ultrawide",
    "targetHorizontalFov": 110.0,
    "maxHorizontalFov": 0.0,
    "fovScale": 1.0,
    "minVerticalFov": 0.0,
    "viewmodelOffsetX": 0.0,
    "viewmodelOffsetY": 0.0,
    "viewmodelExtraScale": 1.0
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
| `affectHudFov` | Leave **false**. Hands keep the vanilla camera so the weapon stays in the corner. `true` puts Hor+ on the hand camera too |
| `edgeCorrect` | Leave **0**. The edge resample stays off |

### Per-band keys

| Key | Meaning |
|-----|---------|
| `mode` | `ultrawide` (own camera) · `match_16_9` · `match_21_9` · `fixed_hfov` |
| `targetHorizontalFov` | World width in degrees when mode is `ultrawide` or `fixed_hfov` |
| `maxHorizontalFov` | Hard HFOV cap. **`0` = off** |
| `fovScale` | Extra multiplier on vertical FOV. **`1.0`** leaves the slider alone |
| `minVerticalFov` | Floor on vertical FOV. **`0` = off** so the slider is not clamped |
| `viewmodelOffsetX` | Extra hand nudge (negative = left). Stacks on auto aspect offset |
| `viewmodelOffsetY` | Extra lift after the automatic FOV scale (positive = up). `0` is the vanilla gap above the hotbar |
| `viewmodelExtraScale` | Multiply hand size (`>1` = bigger on screen) |

### 75" / dual-4K 32:9 tips

Use `superwide_32_9` only (your panel is detected as that band). The world is
`targetHorizontalFov` degrees across (110 by default). The video-settings FOV
slider does not change it. Edit that number, then restart the client.
