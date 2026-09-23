# yap-ultrawide

Fabric **client** mod for Minecraft **26.2**. It does not go on YaPcore or Folia.

On 21:9 and 32:9, vanilla’s vertical FOV slider becomes a fish-eye horizontal view.
This mod applies **Hor+** with **separate profiles** for each panel class. 16:9 is
unchanged. Spyglass / zoom FOVs pass through.

The FOV slider still widens and tightens the view. On 21:9 and 32:9 a normal
slider position (70) is about 102° across, so the view stays wide. The edges
are compressed after the world is drawn, which keeps the sides straight. Hands
stay on the vanilla HUD camera, so the weapon stays in the corner. View-bob is
scaled with the FOV change so the world does not slide under the crosshair
while walking.

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
  "configVersion": 14,
  "enabled": true,
  "affectHudFov": false,
  "edgeCorrect": 1.0,
  "ultrawide_21_9": {
    "mode": "match_16_9",
    "targetHorizontalFov": 100.0,
    "maxHorizontalFov": 0.0,
    "fovScale": 1.0,
    "minVerticalFov": 0.0,
    "viewmodelOffsetX": 0.0,
    "viewmodelOffsetY": 0.0,
    "viewmodelExtraScale": 1.0
  },
  "superwide_32_9": {
    "mode": "match_21_9",
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
| `edgeCorrect` | **1** compresses the edges of the wide view. **0** leaves them rectilinear |

### Per-band keys

| Key | Meaning |
|-----|---------|
| `mode` | `match_16_9` · `match_21_9` · `fixed_hfov` |
| `targetHorizontalFov` | Locked HFOV when mode is `fixed_hfov` |
| `maxHorizontalFov` | Hard HFOV cap. **`0` = off** so the Minecraft FOV slider works |
| `fovScale` | Extra multiplier on vertical FOV. **`1.0`** leaves the slider alone |
| `minVerticalFov` | Floor on vertical FOV. **`0` = off** so the slider is not clamped |
| `viewmodelOffsetX` | Extra hand nudge (negative = left). Stacks on auto aspect offset |
| `viewmodelOffsetY` | Extra lift after the automatic FOV scale (positive = up). `0` is the vanilla gap above the hotbar |
| `viewmodelExtraScale` | Multiply hand size (`>1` = bigger on screen) |

### 75" / dual-4K 32:9 tips

Use `superwide_32_9` only (your panel is detected as that band). The FOV slider
still changes the view. At 70 the picture is about 102° across, with the edges
compressed so the sides stay straight. Raising the slider widens it toward
115°. Lowering it tightens it toward 90°.
