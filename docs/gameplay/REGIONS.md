# Regions & claim flags (Phase 11)

YaPcore ships **WorldGuard-class flags** on player claims (`yap-playerdata`) plus
**staff admin regions** (`yap-regions.jar`).

## Overlap rule (admin regions)

When multiple admin regions contain a block:

1. **Highest `priority` wins** (explicit int, default `0`)
2. If priorities tie, the **smallest volume** wins (cuboid AABB, or polygon shoelace×Y)

```bash
/region priority spawn 10
/region priority arena 5
```

Lookup scans all regions for this server (**O(n)**). That is fine for typical admin-region
counts (tens to low hundreds); there is no spatial index in this release.

## Admin vs claim precedence

When an **admin region** covers a block, **admin flags win** for enforcement at that
location (product rule). Claim flags apply only when no admin region is present.

Historically both listeners could fire and the most restrictive outcome would win;
operators should treat **admin-wins** as the documented product rule going forward.

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
| `item-drop` | allow | Player item drop (trust/bypass can override deny) |
| `item-pickup` | allow | Player item pickup (trust/bypass can override deny) |
| `tnt` | deny | TNT explosion block damage |
| `creeper-explosion` | deny | Creeper explosion block damage |

```bash
/claim flag set pvp deny
/claim flag set entry allow
/claim flag set tnt deny
/claim message set greeting Welcome to my claim!
/claim message clear farewell
```

Defaults: `plugins/YaPPlayerdata/config.yml` → `claims.default-flags`.

Trust levels (`access`, `build`, `manage`) still apply when a flag is not explicitly set.

**Members / owners** stay on PlayerData claims (intentional product split — admin
regions do not duplicate claim membership).

Greeting / farewell strings live in `yap_claim_messages` (not in the allow/deny flag table).

## Admin regions — YaPRegions

Staff **cuboids** and **2D polygons** (XZ vertices + Y range). Cuboids use YaPWorld
selection (`/yapworld wand`, pos1/pos2) or console coords. Polygons use
`/region polyadd` (stand at each corner) then `definepoly`, or console vertex lists.
Y range for player `definepoly` comes from the cuboid selection when set; otherwise
full world height.

```bash
/region define spawn
/region define spawn at world 0 64 0 50 120 50
/region redefine spawn
/region polyadd
/region polyclear
/region definepoly arena
/region definepoly arena at world 40 80 0 0 20 0 10 20
/region remove spawn
/region info spawn
/region priority spawn 10
/region flag set spawn pvp deny
/region flag set spawn tnt deny
/region message set spawn greeting Welcome to spawn!
/region message clear spawn farewell
/region template save safe-hub spawn
/region apply-template arena safe-hub
/region template list
/region list
/region list json
/region reload
```

`/region info` shows bounds, **shape**, **priority**, vertices (polygons), and flags.
New regions start at priority `0`.

### Admin vs player claim flags

| Flag | Admin regions (`YaPRegions`) | Player claims (`YaPPlayerData`) |
|------|------------------------------|----------------------------------|
| `pvp`, `mob-damage`, `build`, `interact`, `entry`, `chest-access`, `fire-spread`, `mob-spawning` | yes | yes |
| `item-drop`, `item-pickup`, `tnt`, `creeper-explosion` | yes | yes |

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

Unset flags default to **allow**. Overlap: highest priority, then smallest volume.
Dashboard Regions tab shows the flag map from `region list json` (includes `priority`, `shape`).

Greeting / farewell: `RegionMessageKind` API + `yap_admin_region_messages` table
(string text, not stuffed into allow/deny `VARCHAR(8)`).

### Templates

Save a region's flags + greeting/farewell as a named preset, then apply onto another region:

```bash
/region template save pvp-arena spawn
/region apply-template colosseum pvp-arena
```

Stored in `yap_admin_region_templates` (per `server-id`).

Tables: `yap_admin_regions` (`priority`, `shape`), `yap_admin_region_flags`,
`yap_admin_region_messages`, `yap_admin_region_vertices` (polygon XZ order),
`yap_admin_region_templates`.

### Writable API

`com.yapcore.regions.RegionService` on `ServicesManager` — lookup via
`RegionServices.find()`. Includes:

- Read: `at`, `named`, `flagAt`, `listRegions`, `message`
- Write: `define` / `definePolygon`, `redefine` / `redefinePolygon`, `remove`,
  `setFlag`, `setPriority`, `setMessage` / `clearMessage`,
  `saveTemplate` / `applyTemplate` / `listTemplates`

Pure overlap helper: `RegionLookup.at(...)`. Polygon math: `Polygons.contains(...)`.

## Related

- [PLAYERDATA.md](../data/PLAYERDATA.md) — claims, trust, tax
- [PROTECT.md](../plugins/PROTECT.md) — grief audit / restore (not land claims)
- [PERMISSIONS.md](../ops/PERMISSIONS.md) — `yapdata.claims.*`, `yapregions.admin`
