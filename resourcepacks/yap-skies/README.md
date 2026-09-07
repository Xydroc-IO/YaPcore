# YaP Skies (+ water / weather / foliage)

First-party overlay merged into `yapcore-default.zip`.

**Vanilla (every Java client):**
- High-res circular sun and moon, multi-scale cloud banks + wisps
- Nebula End sky (`end_sky` texture only — **no** custom core `sky` shader)
- **YaP Water** — animated still/flow (biome-tint grayscale), underwater overlay, drips
- **YaP Foliage** — denser Faithful-based leaf cutouts with `strict_cutout` (less hatch/static)

Custom core sky shaders were removed: they stacked with Iris `SUN_GLOW` (two suns)
and could seam the sky on modern clients.

**Skybox loaders (optional client mod):** OptiFine-format day / sunrise / sunset /
night / storm / End layers under `assets/minecraft/optifine/sky/`. Works with
OptiFine, Skyboxify, Celestial, or Nuit + Interop. No separate additive sun layer —
vanilla `celestial/sun` is the single sun.

**Shader water + wind (SSR / canopy sway):** optional Fabric `yap-visuals` — not in this pack.

Regenerate:

```bash
python3 scripts/generate-yap-skies.py
python3 scripts/generate-yap-water.py
python3 scripts/generate-yap-foliage.py
./scripts/build-default-resourcepack.sh
```
