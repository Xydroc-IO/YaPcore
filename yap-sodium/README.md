# YaP Sodium (upstream pin — not a fork)

Minecraft **26.2** client performance layer for the optional YaP render stack.

## Why this is not a source fork

Current **Sodium** (CaffeineMC) is licensed under **PolyForm Shield 1.0.0**, which
forbids providing a **competing product**. A rebranded YaP fork of Sodium would
violate that license. Older Sodium releases used LGPL; those do not target MC 26.2.

YaP therefore **pins and redistributes the official Sodium Fabric jar** (unmodified)
alongside YaP Iris + YaP Shaders. Players may also install Sodium from
[Modrinth](https://modrinth.com/mod/sodium) themselves.

| | |
|--|--|
| Upstream | https://github.com/CaffeineMC/sodium |
| License | [PolyForm Shield 1.0.0](LICENSE-PolyForm-Shield.txt) |
| Pin | `sodium-fabric-0.9.1+mc26.2` (Fabric) |
| Fetch | `./scripts/fetch-sodium.sh` → `dist/client-mods/` |

Required Notice (upstream): see [NOTICE.txt](NOTICE.txt).

## Stack

| Piece | Role |
|-------|------|
| **Sodium (this pin)** | Chunk meshing / render performance |
| [yap-iris](../yap-iris/) | LGPL Iris fork — shader loader |
| [yap-shaders](../yap-shaders/) | YaP water + skies shader pack |

Build everything: `./scripts/build-yap-client-render.sh`

Vanilla / Bedrock clients do **not** need these jars.
