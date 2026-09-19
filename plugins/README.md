# plugins/

**Runtime folder** — drop built Folia-native jars (`plugin.yml` with
`folia-supported: true`) and YaP jars (`yap.yml`).

**Sources:** [`yap-first-party/`](../yap-first-party/README.md) (Gradle builds install here).

`folia-kernel/plugins` (and legacy `paper-kernel/plugins`) symlink here.

Product path: `game-authority=folia`. Schedule with `com.yapcore.sched.YapSched`.
Stock Paper jars are unsupported on the Folia product path.

See [docs/plugins/PLUGINS.md](../docs/plugins/PLUGINS.md) and [docs/plugins/PLUGIN_COMPAT.md](../docs/plugins/PLUGIN_COMPAT.md).

## Install tiers

| Tier | Gradle | What’s installed |
|------|--------|------------------|
| **CORE + NETWORK** | `gradle installProductDefaults` | Network + ops jars below |
| **GAMEPLAY** | `gradle installGameplayDefaults` | Skills, dungeons, stacker, qol, items, knobs, disasters, leveled-mobs |
| **Full release box** | `gradle assembleRelease` | CORE + NETWORK + GAMEPLAY |
| **Slim box** | `gradle assembleRelease -PyapGameplay=false` | CORE + NETWORK only |
| **Fine-tune modules** | `gradle installFineTuneModules` | All packaging jars → `modules/` |
| Dist folder (all jars) | `gradle assemblePluginDist` | `build/dist/yap-plugins/{core-network,gameplay,api,modules/…}/` |

### CORE + NETWORK (every release)

| Jar | Role |
|-----|------|
| `yap-folia-bridge.jar` | Folia surface / GlobalRegionScheduler smoke (`/yapbridge`) |
| `yap-placeholderapi.jar` | Clip-compatible PlaceholderAPI (plugin name `PlaceholderAPI`) |
| `yap-plugin-compat.jar` | 1.20–1.21 → 26.2 back-compat (`/yapcompat`) |
| `yap-pregen.jar` | Chunk pre-generator (`/yappregen`) |
| `yap-db.jar` | Shared SQL Hikari pool (`YaPDB`) — MariaDB/MySQL · PostgreSQL · SQLite — [`YAPDB.md`](../docs/data/YAPDB.md) |
| `yap-perms.jar` | Native permissions — groups, tracks, prefixes (`/yapperm`, `/promote`) |
| `yap-playerdata.jar` | Cross-server sync, auth, session lock, schema — data plane ([PLAYERDATA.md](../docs/data/PLAYERDATA.md)) |
| `yap-moderation.jar` | Ban/mute/warn/kick + history (`/ban`, `/modhistory`) |
| `yap-essentials.jar` | Essentials QoL + data-backed cmds (`/bag`, `/home`, `/kit`, `/bal`, `/shop`, …) |
| `yap-admin.jar` | In-game staff super menu (`/yapadmin`, `/staff`) — [WEB_DASHBOARD.md](../docs/ops/WEB_DASHBOARD.md) |
| `yap-lib.jar` | Folia-safe packet intercept (`/yaplib`, ProtocolLib-class API) — [YAPLIB.md](../docs/plugins/YAPLIB.md) |
| `yap-holo.jar` | Packet holograms — attach, PAPI, clicks, items, pages (`/yapholo`) — [YAPHOLO.md](../docs/plugins/YAPHOLO.md) |
| `yap-protect.jar` | Block logging / rollback |
| `yap-world.jar` | Multi-world + WorldEdit-class tools (`/yapworld`) |
| `WorldEdit.jar` | WorldEdit API shim (`yap-worldedit-shim`) |
| `yap-regions.jar` | WorldGuard-class regions (`/region`) |
| `yap-portals.jar` | Fleet walk-through portals (`/portal`) — **on** by default · [PORTALS.md](../docs/network/PORTALS.md) |
| `yap-npcs.jar` | NPCs + quests + hub actions (`/npc`, `/quests`) |
| `yap-guard.jar` | Lightweight movement heuristics (not Grim) — PvP: `./scripts/grim-ac.sh enable` |
| `yap-lagguard.jar` | Lag / entity budgets |
| `yap-map.jar` | Flat web map (Leaflet tiles + markers; no 3D) |
| `yap-factions.jar` | Factions/guilds overlay on playerdata claims (off by default) |
| `yap-conquest.jar` | Hardcore chunk conquest land (off by default; needs YaPFactions) |
| `yap-packs.jar` | Multi-active resource packs (`/yappacks`) |
| `yap-commands.jar` | YAML custom `/commands` + dashboard CRUD |
| `yap-chat.jar` | Full chat suite + unsigned system chat fix |
| `yap-tab.jar` | Tab list / nametags |
| `yap-discord.jar` | Discord bridge |
| `yap-floodgate.jar` | Velocity Bedrock identity without Floodgate jar |
| `yap-bedrock-ui.jar` | Bedrock form UI bridge |
| `yap-tailor.jar` | Skins / wardrobe / emotes / `yap:presence` channel — [BEDROCK_FEEL_PARITY.md](../docs/product/BEDROCK_FEEL_PARITY.md) |
| `yap-bedrock-blocks.jar` | Catalog Bedrock port-blocks for JE (`yap:blocks`) |
| `yap-items.jar` | Custom items — abilities, furniture, recipes (`/yapitems`) — [YAPITEMS.md](../docs/plugins/YAPITEMS.md) |
| `yap-qol.jar` | Timber axe + area excavator (`/yapqol`) — [PLUGINS.md](../docs/plugins/PLUGINS.md) |

### GAMEPLAY (opt-in)

| Jar | Role |
|-----|------|
| `yap-skills.jar` | Thin skills — mining / woodcutting / strength / marathon / builder / herbalism / excavation / alchemy / health (`/skills`) — [PLUGINS.md](../docs/plugins/PLUGINS.md) |
| `yap-dungeons.jar` | Instanced procedural dungeons L1–50 + prestige 51–100 — [PLUGINS.md](../docs/plugins/PLUGINS.md) |
| `yap-stacker.jar` | PDC mob/item/spawner stacker (`/yapstacker`) |
| `yap-disasters.jar` | Extreme weather + disasters (`/yapdisaster`) |
| `yap-leveled-mobs.jar` | Distance-based mob levels (`/yaplevel`) |
| `yap-gameplay-knobs.jar` | Purpur-inspired encyclopedia (event-wired; crop/fluid NMS opt-in via YaP-Folia 0025) |

Plus GAMEPLAY fine-tune modules (`yap-stacker-module`, `yap-gameplay-knobs-module`)
when `-PyapGameplay=true`. CORE fine-tune modules install with `installProductDefaults`
(see [modules/README.md](../modules/README.md)).

### Optional third-party (not in git)

| Jar | How | Notes |
|-----|-----|-------|
| `tebex.jar` | `./scripts/fetch-tebex.sh` or `gradle fetchTebex` | Official **GPLv3** Folia store plugin — Hub only · [INTEGRATIONS.md](../docs/ops/INTEGRATIONS.md) |
| `grim.jar` | `./scripts/fetch-grim.sh` or `gradle fetchGrim` | Official **GPLv3** Grim AC — auto-downloaded **disabled** on `seed-defaults.sh`; enable with `./scripts/grim-ac.sh enable` · [GRIM.md](../docs/ops/GRIM.md) |

See [docs/plugins/PLUGINS.md](../docs/plugins/PLUGINS.md) · [docs/plugins/PLUGINS.md](../docs/plugins/PLUGINS.md) ·
[docs/plugins/PLUGINS.md](../docs/plugins/PLUGINS.md) ·
[docs/plugins/PLUGIN_COMPAT.md](../docs/plugins/PLUGIN_COMPAT.md) · [docs/plugins/PLUGINS.md](../docs/plugins/PLUGINS.md) ·
[docs/plugins/PLUGINS.md](../docs/plugins/PLUGINS.md) · [docs/data/YAPDB.md](../docs/data/YAPDB.md) ·
[docs/data/PLAYERDATA.md](../docs/data/PLAYERDATA.md) · [docs/data/YAPDB.md](../docs/data/YAPDB.md) ·
[docs/ops/PERMISSIONS.md](../docs/ops/PERMISSIONS.md) ·
[docs/network/CLIENTS_AND_PACKS.md](../docs/network/CLIENTS_AND_PACKS.md) · [docs/network/YAP_LINK.md](../docs/network/YAP_LINK.md).
