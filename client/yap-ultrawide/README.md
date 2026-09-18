# yap-ultrawide

Fabric **client** mod for Minecraft **26.2**. It does not go on YaPcore or Folia.

On 21:9 and 32:9, vanilla’s vertical FOV slider becomes a fish-eye horizontal view.
This mod applies **Hor+** with **separate profiles** for each panel class. 16:9 is
unchanged. Spyglass / zoom FOVs pass through.

Hands share the **world** frustum (so blocks place on the crosshair) and the
viewmodel is scaled so held items stay on screen. View-bob is scaled with Hor+
zoom so the world does not slide under the crosshair while walking.

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
  "configVersion": 5,
  "enabled": true,
  "affectHudFov": true,
  "ultrawide_21_9": {
    "mode": "match_16_9",
    "targetHorizontalFov": 100.0,
    "maxHorizontalFov": 100.0,
    "fovScale": 0.98
  },
  "superwide_32_9": {
    "mode": "match_16_9",
    "targetHorizontalFov": 103.0,
    "maxHorizontalFov": 103.0,
    "fovScale": 0.97
  }
}
```

| Band | Typical panels |
|------|----------------|
| `ultrawide_21_9` | 2560×1080, 3440×1440, 3840×1600 (aspect ≈1.90–2.80) |
| `superwide_32_9` | 3840×1080, 5120×1440, 7680×2160 / 57" (aspect ≥2.80) |

### Global keys

| Key | Meaning |
|-----|---------|
| `affectHudFov` | Hor+ on first-person hands so they share the world frustum. Keep **true**; the mod scales the viewmodel so weapons stay visible. `false` restores vanilla 70° hands (can look like you are aiming off the crosshair) |

### Per-band keys

| Key | Meaning |
|-----|---------|
| `mode` | `match_16_9` · `match_21_9` · `fixed_hfov` |
| `targetHorizontalFov` | Locked HFOV when mode is `fixed_hfov` |
| `maxHorizontalFov` | Hard HFOV cap (`0` = off) |
| `fovScale` | Extra tighten (`0.90`–`1.0`) if edges still stretch |

### 57" 32:9 tips

Use `superwide_32_9` only (your panel is detected as that band). Defaults match
16:9 horizontal FOV with a ~103° cap. If still too zoomed in: raise FOV in
vanilla video settings, or set `"maxHorizontalFov": 0` (no cap) / `"fovScale": 1.0`.
If edges fish-eye too much: drop `maxHorizontalFov` toward `95`.
