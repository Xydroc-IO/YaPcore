# Dual-stack clients & resource packs

## Game authority (Folia default)

**Product path:** Folia game (`game-authority=folia`, `folia-embed=true`) + YapEngine chassis.  
Phase 3 Paper spatial is **legacy / opt-in** (defaults off); Folia path has no Phase 3 spatial tick.  
Phase 4: dual-stack join green; play depth deepening (not full Geyser play parity yet).  
See [PAPER_YAPENGINE_PORT.md](PAPER_YAPENGINE_PORT.md) · [WHAT_WE_ARE.md](WHAT_WE_ARE.md).

```properties
game-authority=folia
folia-embed=true
folia-version=26.2
folia-dir=folia-kernel
paper-phase3-tick-bridge=false
paper-phase3-nms-tick=false
```

| Value | Meaning |
|-------|---------|
| `folia` + `folia-embed=true` | **Default** — Folia owns JE |
| `paper` + `paper-embed=true` | Legacy — Paper owns JE; Phase 3 opt-in for benches |
| `paper-phase3-nms-tick=true` | Legacy only — interior NMS entity tick on cores 3–6 (**requires** `lib/paper-*-yap.jar`) |
| `native` | Experimental YapEngine flat world |
| `mojang` | Legacy Mojang wrap |

**Java 25+** for Folia/Paper 26.2. Folia product path:

```bash
./scripts/fetch-folia.sh
./scripts/start.sh --fg           # cds into folia-kernel
```

Legacy Paperclip (Phase 3 benches only):

```bash
./scripts/vendor-paper.sh
./scripts/build-vendor-paper.sh   # → lib/paper-26.2-yap.jar
```

## Built-in multi-version (Via-class — no Via* plugins)

**Supported JE (product DoD):** **1.20.2 → current** onto Folia/Paper 26.2
(first-party ViaBackwards-class remaps). **1.19.4** is an optional canary.

**Best-effort:** pre-1.19 (incl. 1.8 Rewind) may still join for status/spawn
checks (`MATRIX_FULL=1`); deep play remaps are **not** a Phase 4 blocker.

**Phase 4 DoD:** first-party ViaVersion + ViaBackwards equivalents on the Folia
JE path. ViaRewind-depth is best-effort only. See [PHASE4_PROTOCOL.md](PHASE4_PROTOCOL.md)
and the full checklist [VIA_GEYSER_PARITY.md](VIA_GEYSER_PARITY.md).
Join/spawn is green; **do not** claim full Geyser play parity yet.

Scaffold: `ProtocolBand` / `ProtocolCompat` / `ViaStyleRemapper`. Bedrock uses
first-party Geyser parity (`GeyserStyleTranslator`), not the Geyser jar.

| Path | Who remaps |
|------|------------|
| Native JE gateway | `ProtocolBand` + expanding `ViaStyleRemapper` |
| Folia JE (product) | Phase 4: Via\* parity onto Folia 26.2 |
| Paper JE (legacy) | Same remapper wiring on Paper authority |
| Bedrock | Phase 4: Geyser parity via CrossplayHub (play depth deepening) |

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

```properties
resource-pack-enabled=true
resource-pack-files=yapcore-default.zip,my-overlay.zip
resource-pack-forced=false
resource-pack-prompt=This server offers a resource pack. Click Yes to download, or No to play without it.
resource-pack-http-port=8081
resource-pack-public-host=yapcoremc.yaplabs.us
public-pack-port=80
resource-pack-url=http://yapcoremc.yaplabs.us/pack/{file}
```

Publish the **offer** zip (single file or `yap-active-bundle-*.zip`) to the edge docroot after changing actives.

**Default pack:** `yapcore-default.zip` (Faithful 64x + YaP Vehicles) — built on
`gradle prepareClientPack`. Credit / license: `resourcepacks/CREDITS.md`,
`FAITHFUL_LICENSE.txt`.

**Publish for Cloudflare:** after rebuilding the zip, mirror it into nginx’s docroot.
The publish script also writes a **hash-suffixed** name (`yapcore-default-<sha8>.zip`) —
use that as `resource-pack-files` so Cloudflare cannot serve a stale zip. Minecraft
reports “failed to download” when the SHA-1 in `server.properties` does not match
the bytes it fetched.

```bash
./scripts/build-default-resourcepack.sh          # also calls publish when possible
./scripts/publish-resourcepack-www.sh            # → …/yapcore-default.zip + …-<sha8>.zip
curl -sL http://yapcoremc.yaplabs.us/pack/yapcore-default-<sha8>.zip | sha1sum
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
enable/list packs from the [web dashboard](WEB_DASHBOARD.md) Packs tab
(or Control Panel).

See [NETWORKING.md](NETWORKING.md) and [CLOUDFLARE_AND_NGINX.md](CLOUDFLARE_AND_NGINX.md).

## Realistic skies (shaders) — not auto-downloaded

Minecraft **cannot** push Iris/OptiFine shaders from the server. For volumetric
clouds / realistic skies, players install a **client** profile once:

| Mod | Role |
|-----|------|
| [Sodium](https://modrinth.com/mod/sodium) | Performance |
| [Iris](https://modrinth.com/mod/iris) | Shader loader |
| Complementary / BSL / etc. | Shader pack |

Recommend that stack in MOTD / Discord / website. Server modpacks are not required
and conflict with the Paper product path.
