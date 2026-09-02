# yap-ultrawide

Fabric **client** mod for Minecraft **26.2**. It does not go on YaPcore or Folia.

On 21:9 and 32:9, vanilla’s vertical FOV slider becomes a fish-eye horizontal view.
This mod keeps the **horizontal FOV you would have on 16:9**, then derives vertical FOV
from the real window size (Hor+). 16:9 is left alone. Spyglass / zoom FOVs pass through.

## Install

1. Fabric Loader **0.19.3+** for Minecraft **26.2**
2. Drop `yap-ultrawide-1.0.0.jar` into `.minecraft/mods/`
3. Join YaPcore / Folia as usual (vanilla protocol)

Vanilla clients still connect. They just keep vanilla FOV.

## Build

```bash
cd yap-ultrawide
./gradlew build
```

Jar: `build/libs/yap-ultrawide-1.0.0.jar`

## Config

Written on first launch: `.minecraft/config/yap-ultrawide.json`

| Key | Default | Meaning |
|-----|---------|---------|
| `enabled` | `true` | Master switch |
| `mode` | `match_16_9` | Same HFOV as 16:9 at the vanilla slider. `fixed_hfov` locks `targetHorizontalFov` |
| `targetHorizontalFov` | `100` | Used only in `fixed_hfov` |
| `affectHudFov` | `true` | Also adjust first-person hand FOV |

Aspect bands: **21:9** from 1.90 (2560×1080, 3440×1440, 3840×1600), **32:9** from 2.80 (3840×1080, 5120×1440).
