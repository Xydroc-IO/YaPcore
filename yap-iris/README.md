# YaP Iris

**LGPL-3.0** fork of [Iris](https://github.com/IrisShaders/Iris) for Minecraft **26.2**,
branded for the YaP optional client render stack.

Based on Iris by coderbot / IrisShaders and contributors. Upstream license:
[LICENSE](LICENSE). Dependency license notes: [LICENSE-DEPENDENCIES](LICENSE-DEPENDENCIES)
(**glsl-transformer** is AGPL-3.0 — distributing Iris binaries implies AGPL obligations
for that component; see [docs/start/LICENSING.md](../docs/start/LICENSING.md)).

| | |
|--|--|
| Mod id | `yap-iris` (provides `iris`) |
| Depends | Official **Sodium** `0.9.x` (Fabric) — see [yap-sodium](../yap-sodium/) |
| Shader pack | [yap-shaders](../yap-shaders/) |

## Build

```bash
cd yap-iris
./gradlew :fabric:build -Pbuild.release
# → fabric/build/libs/yap-iris-*.jar  (or iris-*.jar depending on archives name)
```

Or from repo root: `./scripts/build-yap-client-render.sh`

## Install

1. Fabric Loader **0.19+** for Minecraft **26.2**
2. Official Sodium jar (`./scripts/fetch-sodium.sh`)
3. This YaP Iris jar
4. Drop [yap-shaders](../yap-shaders/) zip into `.minecraft/shaderpacks/` and enable it

Does **not** go on Folia / YaPcore server `plugins/`.
