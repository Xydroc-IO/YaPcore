# plugins/

**One folder for everything:** Paper/Spigot jars (`plugin.yml`) and YaP jars (`yap.yml`).

`paper-kernel/plugins` is a symlink here so real Paper loads the same jars.

See [docs/PLUGINS.md](../docs/PLUGINS.md) and [docs/PLUGIN_COMPAT.md](../docs/PLUGIN_COMPAT.md).

## Install tiers

| Tier | Gradle | What’s installed |
|------|--------|------------------|
| **CORE + NETWORK** (default) | `gradle installProductDefaults` / `assembleRelease` | Network + ops jars below |
| **GAMEPLAY** (opt-in) | `gradle installGameplayDefaults` or `assembleRelease -PyapGameplay=true` | Vehicles, stacker, knobs + fat pack |
| **Both** | `gradle installAllProductDefaults` | CORE + GAMEPLAY |
| **Fine-tune modules** | `gradle installFineTuneModules` | All packaging jars → `modules/` |
| **Dist folder (all jars)** | `gradle assemblePluginDist` | `build/dist/yap-plugins/{core-network,gameplay,api,engine,modules/…}/` |

### CORE + NETWORK (every release)

| Jar | Role |
|-----|------|
| `yap-placeholderapi.jar` | Clip-compatible PlaceholderAPI (plugin name `PlaceholderAPI`) |
| `yap-plugin-compat.jar` | 1.20–1.21 → 26.2 back-compat (`/yapcompat`) |
| `yap-pregen.jar` | Chunk pre-generator (`/yappregen`) |
| `yap-db.jar` | Shared MariaDB Hikari pool (`YaPDB`) — `docs/YAPDB.md` / `docs/MARIADB.md` |
| `yap-playerdata.jar` | Cross-server data + offline `/login` + session lock + modular features |
| `yap-packs.jar` | Multi-active resource packs (`/yappacks`) |
| `yap-chat.jar` | Unsigned system chat — clears “Chat messages cannot be verified” |
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

See [docs/VEHICLES.md](../docs/VEHICLES.md) · [docs/MODULES_AND_API.md](../docs/MODULES_AND_API.md) ·
[docs/PLACEHOLDERAPI.md](../docs/PLACEHOLDERAPI.md) ·
[docs/PLUGIN_BACKCOMPAT.md](../docs/PLUGIN_BACKCOMPAT.md) · [docs/PREGEN.md](../docs/PREGEN.md) ·
[docs/STACKER.md](../docs/STACKER.md) · [docs/YAPDB.md](../docs/YAPDB.md) ·
[docs/PLAYERDATA.md](../docs/PLAYERDATA.md) · [docs/MARIADB.md](../docs/MARIADB.md) ·
[docs/PERMISSIONS.md](../docs/PERMISSIONS.md) ·
[docs/CLIENTS_AND_PACKS.md](../docs/CLIENTS_AND_PACKS.md) · [docs/VELOCITY.md](../docs/VELOCITY.md).
