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
| **GAMEPLAY** | `gradle installGameplayDefaults` | Vehicles, stacker, knobs, MMO |
| **Full release box** | `gradle assembleRelease` | CORE + NETWORK + GAMEPLAY |
| **Slim box** | `gradle assembleRelease -PyapGameplay=false` | CORE + NETWORK only |
| **Fine-tune modules** | `gradle installFineTuneModules` | All packaging jars → `modules/` |
| `Dist folder (all jars)` | `gradle assemblePluginDist` | `build/dist/yap-plugins/{core-network,gameplay,api,modules/…}/` |

### CORE + NETWORK (every release)

| Jar | Role |
|-----|------|
| `yap-folia-bridge.jar` | Folia surface / GlobalRegionScheduler smoke (`/yapbridge`) |
| `yap-placeholderapi.jar` | Clip-compatible PlaceholderAPI (plugin name `PlaceholderAPI`) |
| `yap-plugin-compat.jar` | 1.20–1.21 → 26.2 back-compat (`/yapcompat`) |
| `yap-pregen.jar` | Chunk pre-generator (`/yappregen`) |
| `yap-db.jar` | Shared MariaDB Hikari pool (`YaPDB`) — `docs/data/YAPDB.md` / `docs/data/MARIADB.md` |
| `yap-perms.jar` | Native permissions — groups, tracks, prefixes (`/yapperm`, `/promote`) |
| `yap-playerdata.jar` | Cross-server data + offline `/login` + session lock + modular features |
| `yap-moderation.jar` | Ban/mute/warn/kick + history (`/ban`, `/modhistory`) |
| `yap-essentials.jar` | Essentials-class QoL (`/spawn`, `/tpa`, `/fly`, `/vanish`, …) |
| `yap-admin.jar` | In-game staff super menu (`/yapadmin`, `/staff`) — [ADMIN_MENU.md](../docs/ops/ADMIN_MENU.md) |
| `yap-packs.jar` | Multi-active resource packs (`/yappacks`) |
| `yap-chat.jar` | Full chat suite + unsigned system chat fix |
| `yap-floodgate.jar` | Velocity Bedrock identity without Floodgate jar |

### GAMEPLAY (opt-in)

| Jar | Role |
|-----|------|
| `yap-vehicles.jar` | YaP Vehicles (fleet, fuel, upgrades, shop) |
| `yap-gameplay-knobs.jar` | Purpur-class mob encyclopedia |
| `yap-stacker.jar` | PDC mob/item/spawner stacker (`/yapstacker`) |

Plus GAMEPLAY fine-tune modules (`yap-vehicles-module`, `yap-stacker-module`,
`yap-gameplay-knobs-module`) and vehicles overlay in `yapcore-default.zip` when
`-PyapGameplay=true`. CORE fine-tune modules install with `installProductDefaults`
(see [modules/README.md](../modules/README.md)).

### Optional third-party (not in git)

| Jar | How | Notes |
|-----|-----|-------|
| `tebex.jar` | `./scripts/fetch-tebex.sh` or `gradle fetchTebex` | Official **GPLv3** Folia store plugin — Hub only · [TEBEX.md](../docs/ops/TEBEX.md) |
| `grim.jar` | `./scripts/fetch-grim.sh` or `gradle fetchGrim` | Official **GPLv3** Grim AC — auto-downloaded **disabled** on `seed-defaults.sh`; enable with `./scripts/grim-ac.sh enable` · [GRIM.md](../docs/ops/GRIM.md) |

See [docs/plugins/VEHICLES.md](../docs/plugins/VEHICLES.md) · [docs/plugins/MODULES_AND_API.md](../docs/plugins/MODULES_AND_API.md) ·
[docs/plugins/PLACEHOLDERAPI.md](../docs/plugins/PLACEHOLDERAPI.md) ·
[docs/plugins/PLUGIN_BACKCOMPAT.md](../docs/plugins/PLUGIN_BACKCOMPAT.md) · [docs/plugins/PREGEN.md](../docs/plugins/PREGEN.md) ·
[docs/plugins/STACKER.md](../docs/plugins/STACKER.md) · [docs/data/YAPDB.md](../docs/data/YAPDB.md) ·
[docs/data/PLAYERDATA.md](../docs/data/PLAYERDATA.md) · [docs/data/MARIADB.md](../docs/data/MARIADB.md) ·
[docs/ops/PERMISSIONS.md](../docs/ops/PERMISSIONS.md) ·
[docs/network/CLIENTS_AND_PACKS.md](../docs/network/CLIENTS_AND_PACKS.md) · [docs/network/VELOCITY.md](../docs/network/VELOCITY.md).
