# YaP-QoL

First-party quality-of-life tools for YaP-Folia: **timber axe** and **area excavator** (3×3 / 6×6 / 9×9).

| | |
|--|--|
| Jar | `yap-qol.jar` |
| Config | `plugins/YaP-QoL/config.yml` |
| Commands | `/yapqol` (reload / give / staff GUI) |
| Kit | VIP kit includes timber + excavator (`yapqol give excavator:3`) |
| Staff | Admin menu → QoL; Fabric **yap-staff** Links screen |

## Defaults

Fleet seed + release boxes ship `yap-qol.jar` alongside `yap-items.jar` when present in the catalog. Claims / Protect / Skills extras respect break events (nested `BlockBreakEvent` for Folia-safe multi-break).

## Rebuild

```bash
gradle :qol-plugin:installIntoPlugins
# or full box:
gradle publishReleasesFolder -PyapGameplay=true
```

See [PLUGINS.md](PLUGINS.md) · [ADMIN_MENU.md](../ops/ADMIN_MENU.md).
