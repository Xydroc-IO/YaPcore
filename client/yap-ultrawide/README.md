# yap-ultrawide

Fabric **client** mod for Minecraft **26.2**. It does not go on YaPcore or Folia.

On 21:9 and 32:9, vanilla’s vertical FOV slider becomes a fish-eye horizontal view.
This mod applies **Hor+** with **separate profiles** for each panel class. 16:9 is
unchanged. Spyglass / zoom FOVs pass through.

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
  "configVersion": 2,
  "enabled": true,
  "affectHudFov": false,
  "ultrawide_21_9": {
    "mode": "match_16_9",
    "targetHorizontalFov": 105.0,
    "maxHorizontalFov": 110.0,
    "fovScale": 1.0
  },
  "superwide_32_9": {
    "mode": "match_21_9",
    "targetHorizontalFov": 100.0,
    "maxHorizontalFov": 100.0,
    "fovScale": 0.95
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
| `affectHudFov` | Hor+ on first-person hands / HUD. **Keep `false`** so held items stay on screen; `true` matches world FOV and can zoom weapons out of view |

### Per-band keys

| Key | Meaning |
|-----|---------|
| `mode` | `match_16_9` · `match_21_9` · `fixed_hfov` |
| `targetHorizontalFov` | Locked HFOV when mode is `fixed_hfov` |
| `maxHorizontalFov` | Hard HFOV cap (`0` = off) |
| `fovScale` | Extra tighten (`0.90`–`1.0`) if edges still stretch |

### 57" 32:9 tips

Use `superwide_32_9` only (your panel is detected as that band). If edges still
fish-eye: lower `maxHorizontalFov` to `95`, or `"mode": "fixed_hfov"` with
`"targetHorizontalFov": 95`. If hands/weapons vanish, ensure `affectHudFov` is `false`.
