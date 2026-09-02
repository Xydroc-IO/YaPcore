# YaP Shaders

First-party **Iris / OptiFine-format** shader pack — realistic **water** and **skies**.

| Effect | Notes |
|--------|--------|
| Multi-scale swell + chop | Quiet vertex displacement + soft normals |
| Screen-space reflections (SSR) | Composite raymarch; sky fallback |
| Refraction | Distorts terrain under the surface + absorption tint |
| Fresnel | Schlick mix of refract vs reflect |
| Specular sun glints | Blinn-style highlight |
| Shore foam | Thin water-column detection |
| Caustics | On beds / underwater (toggle) |
| Atmosphere skies | Zenith/horizon + sun/moon glow |
| Volumetric clouds | Raymarched cloud slab (toggle; profiles set step count) |

| | |
|--|--|
| License | **GPLv3** |
| Loader | [YaP Visuals](../yap-visuals/) / YaP Iris / upstream Iris |
| Profiles | LOW (no SSR) · MEDIUM · HIGH (more SSR steps) |

## Install

Prefer **yap-visuals** (one jar). Or drop `yap-shaders.zip` into `.minecraft/shaderpacks/` and enable it.

```bash
./scripts/build-yap-client-render.sh
```
