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

## Built-in multi-version (full Via parity — no Via* plugins)

**Phase 4 DoD:** first-party equivalents of ViaVersion + ViaBackwards + ViaRewind
on the Paper JE path. See [PHASE4_PROTOCOL.md](PHASE4_PROTOCOL.md).

Scaffold today: `ProtocolBand` / `ProtocolCompat` / `ViaStyleRemapper`. Bedrock
uses first-party Geyser parity (`GeyserStyleTranslator`), not the Geyser jar.

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

```properties
resource-pack-enabled=true
resource-pack-file=yapcore-default.zip
resource-pack-forced=true
resource-pack-http-port=8081
resource-pack-public-host=yapcoremc.yaplabs.us
public-pack-port=443
```

**Default pack:** `yapcore-default.zip` (Faithful 64x + YaP Vehicles) — built on
`gradle prepareClientPack`. Credit / license: `resourcepacks/CREDITS.md`,
`FAITHFUL_LICENSE.txt`.

**Yes — textures auto-download.** With Paper as game authority, YaPcore writes
the active pack URL + SHA-1 into Paper `server.properties`. On join, the client
shows Minecraft’s resource-pack prompt and downloads from the pack HTTP host
(`:8081` locally, or the Cloudflare/nginx edge publicly). If
`resource-pack-forced=true`, declining kicks the player.

Refresh the zip: `./scripts/fetch-faithful-64x.sh`

| Client location | Pack URL offered |
|-----------------|------------------|
| Loopback (`127.0.0.1`) | Public/edge URL still used by Paper (one URL for all players) |
| Internet / LAN | `https://yapcoremc.yaplabs.us/pack/<file>` when `public-pack-port=443` |

YaPcore also serves files on `:8081` for the edge proxy and native-path offers.
Operators can enable/list packs from the [web dashboard](WEB_DASHBOARD.md) Packs tab
(or Control Panel). Rebuild: `./scripts/build-default-resourcepack.sh` or
`gradle prepareClientPack`.

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
