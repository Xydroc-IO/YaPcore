# YaP Shaders

First-party **Iris / OptiFine-format** shader pack — realistic **water**, **weather-driven
foliage wind**, and **skies**.

| Effect | Notes |
|--------|--------|
| Multi-direction Gerstner swell | Mesh heave + procedural normals (no shore run-up) |
| Screen-space reflections (SSR) | Composite raymarch; sky fallback |
| Refraction | Distorts terrain under the surface + absorption tint |
| Fresnel / specular | Schlick + soft sun/moon sheen |
| Shore foam | Thin water-column detection only (no land wash paint) |
| Caustics | Solid beds / underwater (not cutout grass) |
| **Foliage wind** | Shared wind direction; speed from rain/thunder (clear ≈ still); species stiffness (birch soft, spruce stiff); height bend trunk+canopy |
| Atmosphere skies | Dawn/dusk without muddy vanilla fogColor |
| Distance fog | Soft sky-tinted haze (not a white mid-range wall) |
| Volumetric clouds | Raymarched cloud slab (toggle; profiles set step count) |

| | |
|--|--|
| License | **GPLv3** |
| Loader | [YaP Visuals](../yap-visuals/) / YaP Iris / upstream Iris |
| Profiles | LOW · MEDIUM · HIGH |

## Install

Prefer **yap-visuals** (one jar; also ships in `client_mods.zip`). Or drop
`yap-shaders.zip` into `.minecraft/shaderpacks/` and enable it.

```bash
./scripts/build-yap-client-render.sh
```

Vanilla / Bedrock players still get improved **pack** water + leaf textures from
`yapcore-default.zip` (no wind without shaders).
