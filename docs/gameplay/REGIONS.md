# Regions & claim flags (Phase 11)

YaPcore ships **WorldGuard-class flags** on player claims (`yap-playerdata`) plus
**staff admin regions** (`yap-regions.jar`).

## Player claims — flags

Per-claim overrides persist in `yap_claim_flags` (MariaDB via YaPDB).

| Flag | Default | Behavior |
|------|---------|----------|
| `pvp` | deny | Player vs player damage |
| `mob-damage` | allow | Mob damage to players |
| `build` | trust | Block place/break (explicit deny blocks all) |
| `interact` | trust | Doors, buttons, levers |
| `entry` | allow | Deny entry with move cancel |
| `chest-access` | trust | Container open |
| `fire-spread` | deny | Cancels fire spread into/from claim |
| `mob-spawning` | allow | Blocks natural mob spawns (spawners/eggs exempt) |

```bash
/claim flag set pvp deny
/claim flag set entry allow
```

Defaults: `plugins/YaPPlayerdata/config.yml` → `claims.default-flags`.

Trust levels (`access`, `build`, `manage`) still apply when a flag is not explicitly set.

## Admin regions — YaPRegions

Staff cuboids using YaPWorld selection (`/yapworld wand`, pos1/pos2):

```bash
/region define spawn
/region flag set spawn pvp deny
/region list
```

Tables: `yap_admin_regions`, `yap_admin_region_flags`.

API: `com.yapcore.regions.RegionService` on `ServicesManager` — lookup via
`RegionServices.find()`.

## Related

- [PLAYERDATA.md](../data/PLAYERDATA.md) — claims, trust, tax
- [PERMISSIONS.md](../ops/PERMISSIONS.md) — `yapdata.claims.*`, `yapregions.admin`
