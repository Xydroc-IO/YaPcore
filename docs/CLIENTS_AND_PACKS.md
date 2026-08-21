# Dual-stack clients & resource packs

## Game authority (Paper → YapEngine)

**Product path:** Paper game + Phase 3 tick on YapEngine cores 3–6 (**done**).  
Phase 4: polish dual-stack + YaP plugins on that world.  
See [PAPER_YAPENGINE_PORT.md](PAPER_YAPENGINE_PORT.md).

```properties
game-authority=paper
paper-embed=true
paper-phase3-tick-bridge=true
paper-phase3-nms-tick=true
paper-version=26.2
paper-dir=paper-kernel
```

| Value | Meaning |
|-------|---------|
| `paper` + `paper-embed=true` | Default — Paper owns JE; Phase 3 same-JVM when bridge on |
| `paper-phase3-nms-tick=true` | Interior NMS entity tick on cores 3–6 (**requires** `lib/paper-*-yap.jar`; boot fails if missing) |
| `native` | Experimental YapEngine flat world |
| `mojang` | Legacy Mojang wrap |

**Java 25+** for Paper 26.2. Prefer YaP Paperclip:

```bash
./scripts/vendor-paper.sh
./scripts/build-vendor-paper.sh   # → lib/paper-26.2-yap.jar
./scripts/start.sh --fg           # cds into paper-kernel
```

## Built-in multi-version (Via-class — no Via* plugins)

**Supported JE (product DoD):** **1.20.2 → current** onto Paper 26.2
(first-party ViaBackwards-class remaps). **1.19.4** is an optional canary.

**Best-effort:** pre-1.19 (incl. 1.8 Rewind) may still join for status/spawn
checks (`MATRIX_FULL=1`); deep play remaps are **not** a Phase 4 blocker.

**Phase 4 DoD:** first-party ViaVersion + ViaBackwards equivalents on the Paper
JE path. ViaRewind-depth is best-effort only. See [PHASE4_PROTOCOL.md](PHASE4_PROTOCOL.md)
and the full checklist [VIA_GEYSER_PARITY.md](VIA_GEYSER_PARITY.md).

Scaffold: `ProtocolBand` / `ProtocolCompat` / `ViaStyleRemapper`. Bedrock uses
first-party Geyser parity (`GeyserStyleTranslator`), not the Geyser jar.

| Path | Who remaps |
|------|------------|
| Native JE gateway | `ProtocolBand` + expanding `ViaStyleRemapper` |
| Paper JE (product) | Phase 4: full Via\* parity onto Paper 26.2 |
| Bedrock | Phase 4: full Geyser parity via CrossplayHub |

```properties
backwards-compatible=true
```

Do not copy `ViaVersion.jar` / Geyser jars into `plugins/` for YaPcore.

## Java + Bedrock

| Edition | Who binds | Notes |
|---------|-----------|--------|
| Java | Paper (when embed / Phase 3) | Public `port` |
| Bedrock | YaPcore gateway | UDP shared or separate |

Shared listen port (`shared-listen-port=true`) uses the same number for JE TCP and BE UDP. See [CROSSPLAY.md](CROSSPLAY.md).

## Resource packs (auto-download on join)

**Login prompt:** Active packs become **one** Paper download (Yes/No). Several actives are
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

**Yes — textures auto-download.** With Paper as game authority, YaPcore writes
the active pack URL + SHA-1 into Paper `server.properties`. On join, the client
shows Minecraft’s resource-pack prompt and downloads from that URL.
If `resource-pack-forced=true`, declining kicks the player.

Refresh the zip: `./scripts/fetch-faithful-64x.sh` then
`./scripts/build-default-resourcepack.sh`.

| Client location | Pack URL offered |
|-----------------|------------------|
| All clients (Paper) | `resource-pack-url` / `public-pack-port` (one URL for everyone) |
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
