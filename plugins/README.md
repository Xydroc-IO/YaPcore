# plugins/

**One folder for everything:** Paper/Spigot jars (`plugin.yml`) and YaP jars (`yap.yml`).

`paper-kernel/plugins` is a symlink here so real Paper loads the same jars.

See [docs/PLUGINS.md](../docs/PLUGINS.md) and [docs/PLUGIN_COMPAT.md](../docs/PLUGIN_COMPAT.md).

## Shipped by default

`gradle shadowJar` / `assembleRelease` installs:

| Jar | Role |
|-----|------|
| `yap-vehicles.jar` | YaP Vehicles (fleet, fuel, upgrades, shop) |
| `yap-gameplay-knobs.jar` | Purpur-class mob encyclopedia |
| `yap-placeholderapi.jar` | Clip-compatible PlaceholderAPI (plugin name `PlaceholderAPI`) |
| `yap-plugin-compat.jar` | 1.20–1.21 → 26.2 back-compat (`/yapcompat`) |
| `yap-pregen.jar` | Chunk pre-generator (`/yappregen`) |
| `yap-stacker.jar` | Full PDC mob/item/spawner stacker (`/yapstacker`) |
| `yap-db.jar` | Shared MariaDB Hikari pool (`YaPDB`) — `docs/YAPDB.md` / `docs/MARIADB.md` |
| `yap-playerdata.jar` | Cross-server data + offline `/login` + session lock + claims + GUIs (uses YaPDB) |
| `yap-packs.jar` | Multi-active resource packs (`Player.addResourcePack` — `/yappacks`) |
| `yap-chat.jar` | Unsigned system chat — clears “Chat messages cannot be verified” (offline/Via) |
| `yap-floodgate.jar` | Velocity+Geyser Bedrock identity without Floodgate jar (`/yapfloodgate`) |

See [docs/VEHICLES.md](../docs/VEHICLES.md) · [docs/PLACEHOLDERAPI.md](../docs/PLACEHOLDERAPI.md) ·
[docs/PLUGIN_BACKCOMPAT.md](../docs/PLUGIN_BACKCOMPAT.md) · [docs/PREGEN.md](../docs/PREGEN.md) ·
[docs/STACKER.md](../docs/STACKER.md) · [docs/YAPDB.md](../docs/YAPDB.md) ·
[docs/PLAYERDATA.md](../docs/PLAYERDATA.md) · [docs/MARIADB.md](../docs/MARIADB.md) ·
[docs/PERMISSIONS.md](../docs/PERMISSIONS.md) ·
[docs/CLIENTS_AND_PACKS.md](../docs/CLIENTS_AND_PACKS.md) · [docs/VELOCITY.md](../docs/VELOCITY.md).
