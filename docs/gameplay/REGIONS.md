# Regions & claim flags (Phase 11)

YaPcore ships **WorldGuard-class flags** on player claims (`yap-playerdata`) plus
**staff admin regions** (`yap-regions.jar`).

## Overlap rule (admin regions)

When multiple admin regions contain a block, the **smallest volume** wins
(same rule as WorldGuard priority-by-size for nested cuboids without explicit priority).

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

**Members / owners** stay on PlayerData claims (intentional product split — admin
regions do not duplicate claim membership).

## Admin regions — YaPRegions

Staff cuboids using YaPWorld selection (`/yapworld wand`, pos1/pos2) or console coords:

```bash
/region define spawn
/region redefine spawn
/region remove spawn
/region flag set spawn pvp deny
/region flag set spawn tnt deny
/region list
/region list json
```

### Admin region flags

| Flag | Behavior when **deny** |
|------|------------------------|
| `pvp` | Cancel player damage |
| `mob-damage` | Cancel mob damage to players |
| `build` | Cancel break/place/buckets |
| `interact` | Cancel doors/gates/buttons/levers |
| `entry` | Cancel move into region |
| `chest-access` | Cancel container open |
| `fire-spread` | Cancel fire spread |
| `mob-spawning` | Cancel natural spawns |
| `item-drop` | Cancel player item drop |
| `item-pickup` | Cancel player item pickup |
| `tnt` | Cancel TNT explosion damage to blocks |
| `creeper-explosion` | Cancel creeper block damage |

Unset flags default to **allow**. Dashboard Regions tab shows the flag map from
`region list json`.

Tables: `yap_admin_regions`, `yap_admin_region_flags`.

API: `com.yapcore.regions.RegionService` on `ServicesManager` — lookup via
`RegionServices.find()`.

## Related

- [PLAYERDATA.md](../data/PLAYERDATA.md) — claims, trust, tax
- [PROTECT.md](../plugins/PROTECT.md) — grief audit / restore (not land claims)
- [PERMISSIONS.md](../ops/PERMISSIONS.md) — `yapdata.claims.*`, `yapregions.admin`
