# Dual-stack clients & resource packs

## Game authority (Folia default)

**Product path:** Folia game (`game-authority=folia`, `folia-embed=true`) + YapEngine chassis.  
Phase 3 Paper spatial is **legacy / opt-in** (defaults off); Folia path has no Phase 3 spatial tick.  
Phase 4: dual-stack join + play-depth smoke green (not full Geyser clone yet).  
See [CROSSPLAY.md](../network/CROSSPLAY.md) · [YAPCORE_WHITEPAPER.md](../whitepaper/YAPCORE_WHITEPAPER.md).

```properties
game-authority=folia
folia-embed=true
folia-version=26.2
folia-dir=folia-kernel
# Default product jar: YaP-Folia (./scripts/build-yap-folia.sh)
folia-jar-source=build
# Stock Fill: folia-jar-source=fetch + ./scripts/fetch-folia.sh
paper-phase3-tick-bridge=false
paper-phase3-nms-tick=false
```

| Value | Meaning |
|-------|---------|
| `folia` + `folia-embed=true` | **Default** — Folia owns JE |
| `folia-jar-source=build` | **Default** — `lib/yap-folia-{ver}.jar` (YaP Folia fork) |
| `folia-jar-source=fetch` | Stock Fill jar (`lib/folia-{ver}.jar`) — opt-in fallback |
| `paper` + `paper-embed=true` | Legacy — Paper owns JE; Phase 3 opt-in for benches |
| `paper-phase3-nms-tick=true` | Legacy only — interior NMS entity tick on cores 3–6 (**requires** `lib/paper-*-yap.jar`) |
| `native` | Experimental YapEngine flat world |
| `mojang` | Legacy Mojang wrap |

**Java 25+** for Folia/Paper 26.2. Recommended Folia product path:

```bash
./scripts/build-yap-folia.sh          # → lib/yap-folia-26.2.jar
./scripts/start.sh --fg
```

Stock Fill fallback:

```bash
./scripts/fetch-folia.sh              # → lib/folia-26.2.jar
# folia-jar-source=fetch
```

See [QUICK_START.md](../start/QUICK_START.md).

Legacy Paperclip (Phase 3 benches only) is retired — use the Folia product path above.

## Built-in multi-version (Via-class — no Via* plugins)

**Supported JE (product DoD):** **1.20.2 → current** onto Folia/Paper 26.2
(first-party ViaBackwards-class remaps). **1.19.4** is an optional canary.

**Best-effort:** pre-1.19 (incl. 1.8 Rewind) may still join for status/spawn
checks (`MATRIX_FULL=1`); deep play remaps are **not** a Phase 4 blocker.

**Phase 4 DoD:** first-party ViaVersion + ViaBackwards equivalents on the Folia
JE path. ViaRewind-depth is best-effort only. See [CROSSPLAY.md](../network/CROSSPLAY.md)
and the full checklist [CROSSPLAY.md](../network/CROSSPLAY.md).
Join/spawn is green; **do not** claim full Geyser play parity yet.

Scaffold: `ProtocolBand` / `ProtocolCompat` / `ViaStyleRemapper`. Bedrock uses
first-party Geyser parity (`GeyserStyleTranslator`), not the Geyser jar.

| Path | Who remaps |
|------|------------|
| Native JE gateway | `ProtocolBand` + expanding `ViaStyleRemapper` |
| Folia JE (product) | Phase 4: Via\* parity onto Folia 26.2 |
| Paper JE (legacy) | Same remapper wiring on Paper authority |
| Bedrock | Phase 4: Geyser parity via CrossplayHub (join + play-depth smoke green) |

```properties
backwards-compatible=true
```

Do not copy `ViaVersion.jar` / Geyser jars into `plugins/` for YaPcore.

## Java + Bedrock

| Edition | Who binds | Notes |
|---------|-----------|--------|
| Java | Folia (default embed) or Paper (legacy) | Public `port` |
| Bedrock | YaPcore gateway | UDP shared or separate |

Shared listen port (`shared-listen-port=true`) uses the same number for JE TCP and BE UDP. See [CROSSPLAY.md](CROSSPLAY.md).

## Resource packs (auto-download on join)

**Login prompt:** Active packs become **one** game download (Yes/No). Several actives are
**merged** into `yap-active-bundle-<hash>.zip` (later packs win on path conflicts) so every
active pack applies without play-phase `addResourcePack`.  
`resource-pack-forced=false` (default) lets players decline without being kicked.
Modern JE clients on the Via edge still get the Yes/No prompt; only mid-band /
legacy clients are auto-acked so they are not stuck on join.

```properties
resource-pack-enabled=true
resource-pack-files=yapcore-default.zip,my-overlay.zip
resource-pack-forced=false
resource-pack-prompt=This server offers a resource pack. Click Yes to download, or No to play without it.
# Prefer self-hosted nginx so SHA-1 always matches the local zip after rebuild:
# (leave resource-pack-url empty → PublicEndpoint uses public host + pack port)
# resource-pack-url=
# Or GitHub Releases (must re-upload zip after every rebuild or SHA fails):
# resource-pack-url=https://github.com/Xydroc-IO/YaPcore/releases/latest/download/{file}
resource-pack-http-port=8081
resource-pack-prompt=This server offers a resource pack. Click Yes to download, or No to play without it.
# resource-pack-public-host=yapcoremc.yaplabs.us
# public-pack-port=80
# resource-pack-url=http://yapcoremc.yaplabs.us/pack/{file}
```

Attach **`yapcore-default.zip`** (same bytes you built) to nginx (`./scripts/sync-pack-to-nginx.sh`)
and/or as a GitHub release asset. Tag **`1.0.0.0`** publishes it — `/releases/latest/download/{file}`
follows the newest release. YaP hashes the **download URL** at boot so Paper’s SHA-1 matches
what clients fetch. If the advertised SHA is from a newer local rebuild than GitHub/nginx,
Minecraft shows **“1 of 1 pack failed to download.”**

**Default pack:** `yapcore-default.zip` (Faithful 64x + YaP Skies + YaP Water) — built on
`gradle prepareClientPack`. Credit / license:
`resourcepacks/CREDITS.md`, `FAITHFUL_LICENSE.txt`.

**Publish for GitHub (recommended):**

```bash
./scripts/build-default-resourcepack.sh
gh release upload 1.0.0.0 resourcepacks/yapcore-default.zip --clobber -R Xydroc-IO/YaPcore
# or create a new release and attach the zip — latest/download then picks it up
```

**Publish for Cloudflare / nginx (optional):** after rebuilding the zip, copy it into nginx’s docroot
(and optionally a hash-suffixed name so CF cannot serve a stale zip). Minecraft
reports “failed to download” when the SHA-1 in `server.properties` does not match
the bytes it fetched.

```bash
./scripts/build-default-resourcepack.sh
# then copy resourcepacks/yapcore-default.zip into your pack www root
curl -sL http://127.0.0.1:8081/pack/yapcore-default.zip | sha1sum
```

**Yes — textures auto-download.** With Folia (default) or Paper (legacy) as game
authority, YaPcore writes the active pack URL + SHA-1 into the game’s
`server.properties`. On join, the client shows Minecraft’s resource-pack prompt
and downloads from that URL.
If `resource-pack-forced=true`, declining kicks the player.

Refresh the zip: `./scripts/fetch-faithful-64x.sh` then
`./scripts/build-default-resourcepack.sh`.

| Client location | Pack URL offered |
|-----------------|------------------|
| All clients (Folia/Paper) | `resource-pack-url` / `public-pack-port` (one URL for everyone) |
| Recommended public | `http://yapcoremc.yaplabs.us/pack/<file>` (`public-pack-port=80`) |

YaPcore also serves files on `:8081` for local/edge proxy use. Operators can
enable/list packs from the [web dashboard](../ops/WEB_DASHBOARD.md) Packs tab
(or Control Panel).

See [NETWORKING.md](NETWORKING.md) and [CLOUDFLARE_AND_NGINX.md](CLOUDFLARE_AND_NGINX.md).

## Ultrawide 21:9 / 32:9 — optional client mod

Vanilla FOV is **vertical**. On 21:9 and 32:9 that becomes a fish-eye horizontal view.
The server cannot fix projection. **yap-ultrawide** is a Fabric **client** mod (not a
Folia plugin) that applies Hor+ with **separate profiles** for each panel class.

| Piece | Where |
|-------|--------|
| YaP-Folia / YaPcore | Server — unchanged |
| `yap-ultrawide-*.jar` (also in `client_mods.zip`) | Player `.minecraft/mods/` with Fabric Loader 0.19+ / MC 26.2 |

```bash
cd client/yap-ultrawide && ./gradlew build
# → client/yap-ultrawide/build/libs/yap-ultrawide-1.0.0.jar
# or: ./scripts/build-yap-client-render.sh → dist/client-mods/client_mods.zip
```

Config: `.minecraft/config/yap-ultrawide.json`

| Band | Typical panels | Default mode |
|------|----------------|--------------|
| `ultrawide_21_9` | 2560×1080, 3440×1440 (aspect ≈1.90–2.80) | `match_16_9` |
| `superwide_32_9` | 3840×1080, 5120×1440, 7680×2160 / 57" (≥2.80) | `match_21_9` + HFOV cap |

`match_21_9` on 32:9 means “use the horizontal FOV a 21:9 panel would have” — it does
**not** letterbox your 32:9 screen. For a locked cinematic feel use `fixed_hfov`.

Vanilla, Bedrock, and players without the mod still join.
See [yap-ultrawide/README.md](../../client/yap-ultrawide/README.md).

## Extra bag tabs — optional client mod

`/bag` works for every client (vanilla Java, Bedrock, no mods). **yap-bag** is a Fabric **client** mod that adds a **B** keybind, a Bag button on the survival inventory, and page tabs on the bag chest. It sends `/bag` (YaPEssentials command, YaPPlayerData storage). It is not a Folia plugin.

| Piece | Where |
|-------|--------|
| YaPEssentials `/bag` + YaPPlayerData storage | Server — required |
| `yap-bag-1.0.0.jar` | Player `.minecraft/mods/` with Fabric Loader 0.19+ / MC 26.2 |

```bash
cd client/yap-bag && ./gradlew build
# → client/yap-bag/build/libs/yap-bag-1.0.0.jar
```

Vanilla and Bedrock players keep `/bag` and the `/menu` Bag icon. Config: `.minecraft/config/yap-bag.json`.
See [yap-bag/README.md](../../client/yap-bag/README.md).

## Staff menu on Esc — optional client mod

`/yapadmin` and `/menu` → Staff work for every client that has permission. **yap-staff** is a Fabric **client** mod that adds a **Staff menu** (Esc pause + keybind **R**): full native Screens for players, give, trolls, moderation, economy, ranks/perms, and more. Server still enforces `yapadmin.*` / `yapperm.*`. It is not a Folia plugin.

| Piece | Where |
|-------|--------|
| YaPAdmin `/yapadmin` | Server — required |
| `yap-staff-1.0.3.jar` | Player `.minecraft/mods/` with Fabric Loader 0.19+ / MC 26.2 |

```bash
cd client/yap-staff && ./gradlew build
# → client/yap-staff/build/libs/yap-staff-1.0.3.jar
```

Config: `.minecraft/config/yap-staff.json`. See [yap-staff/README.md](../../client/yap-staff/README.md) and [ADMIN_MENU.md](../ops/ADMIN_MENU.md).

The Fabric **yap-staff** mod scrolls/scales to the window, uses a searchable player picker that returns to the calling tool, and includes a full YaPPerms ranks editor UI. Update **yap-admin.jar** for troll + give + Folia-safe `/yapadmin money`.

## Realistic skies

**YaP Skies** + **YaP Water** ship in `yapcore-default.zip` and download with the pack prompt.

| Layer | Who sees it |
|-------|-------------|
| Circular sun / moon, cloud banks, End nebula, `sky` core shader | Every Java client that accepts the pack |
| Animated water still/flow, underwater overlay, rain/snow | Every Java client that accepts the pack |
| Panoramic day / sunrise / sunset / night / storm / End skyboxes | Clients with a skybox loader |
| Wavy water, fresnel, refraction tint, atmosphere + volumetric clouds | Clients with the **YaP client render stack** (below) |

Skybox loaders (install once on the **client**, not as Folia plugins):
OptiFine, [Skyboxify](https://modrinth.com/mod/skyboxify), Celestial, or
Nuit + Interop.

Minecraft **cannot** push Iris shader packs from the server resource-pack URL.
Use the optional Fabric stack (or upstream equivalents):

## YaP client render stack (optional — Fabric Java)

Realistic water (waves, specular, refraction tint) and shader skies. Vanilla /
Bedrock / no-mods players still join YaPcore without these jars.

**Recommended — one jar:** [`yap-visuals`](../../client/yap-visuals/) embeds official Sodium +
YaP Iris (jar-in-jar) and installs YaP Shaders on first launch.

| Piece | Role | License |
|-------|------|---------|
| **yap-visuals-*.jar** | All-in-one install | GPLv3 wrapper; nests Sodium + Iris |
| Official Sodium (nested) | Performance | PolyForm Shield — **not** forked |
| YaP Iris (nested) | Shader loader | LGPL-3.0 (+ AGPL glsl-transformer) |
| YaP Shaders (auto-extract) | Water + skies pack | GPLv3 |

```bash
./scripts/build-yap-client-render.sh
# → dist/client-mods/client_mods.zip         (release upload — bag + staff + ultrawide + visuals)
# → dist/client-mods/yap-visuals-*.jar       (also loose jars for local installs)
# → dist/client-mods/yap-client-visuals.zip  (visuals-only Discord / site bundle)
```

Install: Fabric Loader 0.19+ · MC 26.2 · **only** `yap-visuals-*.jar` in `.minecraft/mods/`
(do not also install separate Sodium/Iris/shaders). Upstream Iris + any Iris pack also work.
Complementary / BSL are **not** redistributed (custom licenses).

Refresh pack skies textures: `python3 scripts/generate-yap-skies.py` then
`./scripts/build-default-resourcepack.sh`.
