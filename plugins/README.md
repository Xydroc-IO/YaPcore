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
| `yap-playerdata.jar` | Cross-server inv/money + homes/warps/kits/mail/shops/jobs/ah |

See [docs/VEHICLES.md](../docs/VEHICLES.md) · [docs/PLACEHOLDERAPI.md](../docs/PLACEHOLDERAPI.md) ·
[docs/PLUGIN_BACKCOMPAT.md](../docs/PLUGIN_BACKCOMPAT.md) · [docs/PREGEN.md](../docs/PREGEN.md) ·
[docs/STACKER.md](../docs/STACKER.md) · [docs/PLAYERDATA.md](../docs/PLAYERDATA.md).
