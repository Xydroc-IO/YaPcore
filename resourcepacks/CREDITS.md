# Third-party resource pack credits

## YaPcore default pack (`yapcore-default.zip`)

Built by `scripts/build-default-resourcepack.sh` on every product build:

- **Faithful 64x** (world textures) — see below
- **YaP Skies** (`yap-skies/`) — realistic sun, moon, multi-scale clouds, atmosphere, OptiFine skyboxes
- **YaP Water** (same overlay) — animated water still/flow, underwater, rain/snow, drips
  (`scripts/generate-yap-water.py`)

Vehicles and abilities overlays are **not** in the current product pack.

`config/server.properties` → `resource-pack-file=yapcore-default.zip`

## Faithful 64x (base layer)

- **Project:** Faithful Resource Pack — Faithful 64x
- **Website:** https://faithfulpack.net
- **Modrinth:** https://modrinth.com/resourcepack/faithful-64x
- **License:** Faithful License — see `FAITHFUL_LICENSE.txt` and
  https://faithfulpack.net/license
- **Shipped file:** `faithful-64x.zip` (Release 14 Chaos Cubed, game version 26.2)

YaPcore redistributes this pack as a **server resource pack** under the terms of
the Faithful License (credit + license link required; no paywall).

YaP-authored overlays in this tree (skies, water, etc.) follow YaPcore’s
**[GPLv3](../LICENSE)** — [docs/start/LICENSING.md](../docs/start/LICENSING.md).

## YaP Skies + Water

First-party. Skies: `scripts/generate-yap-skies.py`. Water/weather:
`scripts/generate-yap-water.py` (grayscale still/flow for biome tint; underwater,
rain, snow, drips). No third-party photos or Complementary/BSL assets.

Vanilla clients get the sun / moon / clouds / `sky` core shader. Panoramic layers
need a client skybox loader (OptiFine, Skyboxify, Celestial, or Nuit + Interop).

## YaP client render stack (optional Fabric)

For wavy water and shader skies, players install **one** Fabric jar:

- **yap-visuals** — nests official Sodium + YaP Iris; extracts YaP Shaders on launch

Build: `./scripts/build-yap-client-render.sh` → `dist/client-mods/yap-visuals-*.jar`.
See [docs/network/CLIENTS_AND_PACKS.md](../docs/network/CLIENTS_AND_PACKS.md).
