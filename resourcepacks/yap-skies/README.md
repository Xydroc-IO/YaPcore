# YaP Skies (+ water / weather)

First-party overlay merged into `yapcore-default.zip`.

**Vanilla (every Java client):**
- High-res circular sun and moon, multi-scale cloud banks + wisps
- Richer overworld atmosphere (`sky` core shader), nebula End sky
- **YaP Water** — animated still/flow (biome-tint grayscale), underwater overlay,
  rain/snow, drip particles

**Skybox loaders (optional client mod):** OptiFine-format day / sunrise / sunset /
night / storm / End layers under `assets/minecraft/optifine/sky/`. Works with
OptiFine, Skyboxify, Celestial, or Nuit + Interop.

**Shader water (SSR / refraction):** optional Fabric `yap-visuals` — not in this pack.

Regenerate:

```bash
python3 scripts/generate-yap-skies.py
python3 scripts/generate-yap-water.py
./scripts/build-default-resourcepack.sh
```
