# Third-party resource pack credits

## YaPcore default pack (`yapcore-default.zip` / `.mcpack`)

Built by `scripts/packs/build-default-resourcepack.sh` (Java) and
`scripts/packs/build-default-bedrock-pack.sh` (Bedrock) on product builds:

- **Faithful 64x** (world textures) — see below
- **YaP Skies** (`yap-skies/`) — realistic sun, moon, multi-scale clouds, atmosphere, OptiFine skyboxes
- **YaP Water / weather** (same overlay) — still/flow, underwater, drips (`scripts/packs/generate-yap-water.py`)
- **YaP Foliage** (same overlay) — denser Faithful-based leaf cutouts + `strict_cutout` mcmeta
  (`scripts/packs/generate-yap-foliage.py`)
- **YaP Portals** (`yap-portals/`) — multi-color animated portal sheets on stained glass
  (`scripts/packs/generate-yap-portals.py`) for YaPPortals

`config/server.properties` → `resource-pack-file=yapcore-default.zip` (Java)
and `resource-pack-bedrock-file=yapcore-default.mcpack` (Bedrock).

## Faithful 64x (base layer)

- **Project:** Faithful Resource Pack — Faithful 64x
- **Website:** https://faithfulpack.net
- **Modrinth:** https://modrinth.com/resourcepack/faithful-64x
- **License:** Faithful License — see `FAITHFUL_LICENSE.txt` and
  https://faithfulpack.net/license
- **Shipped file (Java):** `faithful-64x.zip` (Release 14 Chaos Cubed, game version 26.2)
- **Shipped file (Bedrock):** `faithful-64x-bedrock.mcpack` (fetched via
  `scripts/packs/fetch-faithful-64x-bedrock.sh`; not committed — rebuild locally)

YaPcore redistributes this pack as a **server resource pack** under the terms of
the Faithful License (credit + license link required; no paywall).

YaP-authored overlays in this tree (skies, water, foliage, etc.) follow YaPcore’s
**[GPLv3](../LICENSE)** — [docs/start/LICENSING.md](../docs/start/LICENSING.md).

## YaP420 item art (CMD 12200–12230)

Plant sheets and several item icons are processed from **Blazin / Cannabis
Resource Pack 3.1** (“Get Cobblestoned!”) — see `THIRD_PARTY/blazin-NOTICE.txt`.
`scripts/packs/generate-yap420-plants.py` splits the dual nether-wart crop panels
into YaP420 stage / tall-crop sheets and remaps kief → gram, bud-block → pound,
grinder → press. Seeds are YaP-authored mottled ovals (not the Blazin leaf).
Fiber prefers GanjaCraft hempfiber. Indica is a color-shifted Blazin panel.
Vanilla `block/cross` / stacked-cross models. Drying-rack / paper / brownie /
blunt remain YaP-authored.

## YaP Skies + Water + Foliage + Portals

First-party. Skies: `scripts/packs/generate-yap-skies.py`. Water/weather:
`scripts/packs/generate-yap-water.py`. Foliage: `scripts/packs/generate-yap-foliage.py`
(densifies Faithful leaves, binary alpha, `strict_cutout`). Portals:
`scripts/packs/generate-yap-portals.py` — animated dye-colored portal sheets on
stained glass / panes for YaPPortals (vanilla only has one nether-portal look).
No third-party photos or Complementary/BSL assets.

Vanilla clients get the sun / moon / clouds textures plus improved
water and leaf textures. **Canopy / grass wind** needs YaP Shaders via yap-visuals.
Panoramic sky layers need a client skybox loader (OptiFine, Skyboxify, Celestial, or Nuit + Interop).
Custom core `sky` shaders are **not** shipped (they double the sun with Iris and can seam the sky).

## YaP client render stack (optional Fabric)

For wavy water, foliage wind, and shader skies, players install the Fabric client
bundle (`client_mods.zip` / **yap-visuals**):

- **yap-visuals** — nests official Sodium + YaP Iris; extracts YaP Shaders on launch
- **yap-bag** / **yap-staff** / **yap-ultrawide** — optional UI / Esc staff / Hor+ FOV (also in `client_mods.zip`)

Build: `./scripts/packs/build-yap-client-render.sh` → `dist/client-mods/client_mods.zip`.
See [docs/network/CLIENTS_AND_PACKS.md](../docs/network/CLIENTS_AND_PACKS.md).
