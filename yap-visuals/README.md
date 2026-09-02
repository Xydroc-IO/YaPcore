# YaP Visuals

**One Fabric jar** for the YaP client render stack:

| Nested / embedded | Role |
|-------------------|------|
| Official **Sodium** (jar-in-jar) | Performance (PolyForm Shield — unmodified) |
| **YaP Iris** (jar-in-jar) | Shader loader (LGPL + AGPL glsl-transformer) |
| **YaP Shaders** (extracted on launch) | Water + skies pack → `.minecraft/shaderpacks/` |

## Install

1. Fabric Loader **0.19+** · Minecraft **26.2**
2. Drop **only** `yap-visuals-1.0.0.jar` into `.minecraft/mods/`
3. Launch once — shaders install; Iris is pointed at `yap-shaders.zip` if you had no pack selected
4. Join YaPcore as usual

Do **not** also install separate Sodium / Iris / `yap-shaders.zip` (duplicate mods).

Vanilla / Bedrock / no-mods clients still join without this jar.

## Build

```bash
./scripts/build-yap-client-render.sh
# → dist/client-mods/yap-visuals-1.0.0.jar
```

Components still live as source under `yap-sodium/`, `yap-iris/`, `yap-shaders/` for licenses and development.
