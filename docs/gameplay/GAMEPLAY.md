# Gameplay — Factions, Conquest, Regions

## Factions

Factions/guilds overlay on **YaPPlayerdata claims** without altering the `claims` table schema.
Claim linkage lives in `yap_faction_claims` (claim_id → faction_id + power cost).

**Opt-in:** jar ships with CORE+NETWORK but `enabled: false` by default. Not every server wants factions.
Set `enabled: true` in `plugins/YaPFactions/config.yml`, then `/yapfactions reload` (or restart).

Hardcore conquest land (chunk grid / overclaim / warzone) is **YaPConquest** — a separate plugin, also off by default. See [GAMEPLAY.md](GAMEPLAY.md).

## Install

```bash
gradle :factions-plugin:installIntoPlugins
# or full core network:
gradle installProductDefaults
./scripts/seed-defaults.sh   # copies config/defaults/plugins/YaPFactions/ if missing
```

Requires `yap-db.jar`, `yap-playerdata.jar` (soft), and shared YaPDB.

## Branding

| Config | Default | Notes |
|--------|---------|-------|
| `labels.singular` | Faction | Shown in messages |
| `labels.plural` | Factions | |
| `labels.command` | f | Help text root |

Commands: `/f`, `/faction`, `/guild`, `/g`, `/clan` — same handler.

PAPI: `%yapfaction_*%` and `%yapguild_*%` (aliases).

## Commands

### Player (`/f` or `/guild`)

| Command | Description |
|---------|-------------|
| `/f create <name> <tag>` | Create faction |
| `/f disband` | Leader disbands |
| `/f join <faction>` | Join (respects join mode) |
| `/f leave` / `/f kick <player>` | Membership |
| `/f invite <player>` / `/f accept\|deny <faction>` | Invite flow |
| `/f promote\|demote\|leader <player>` | Role management |
| `/f desc <text>` / `/f motd [text]` | Description & MOTD |
| `/f open\|closed\|inviteonly` | Join mode (leader) |
| `/f home\|sethome\|delhome` | Faction home |
| `/f setwarp\|delwarp\|warp\|warps` | Shared warps |
| `/f chat [msg\|off]` / `/f allychat [msg\|off]` | Faction & ally chat (toggle routes public chat) |
| `/f ally\|enemy\|neutral <faction>` | Relations |
| `/f claim\|unclaim\|claimall` | Link playerdata claim overlay |
| `/f members\|claims\|top [page]\|map\|info\|upkeep` | Info views |
| `/f deposit\|withdraw\|bank` | Faction bank (YaPPlayerdata economy) |
| `/f power` | Status |

### Admin (`/yapfactions`)

| Command | Description |
|---------|-------------|
| `/yapfactions reload` | Reload config; can enable features without full restart |
| `/yapfactions snapshot json` | Dashboard live snapshot |
| `/yapfactions upkeep [faction\|all]` | Force upkeep collect (freezes immediately if bank short) |
| `/yapfactions setpower <faction> <power> [max]` | Set power |
| `/yapfactions setjoin <faction> <open\|invite\|closed>` | Set join mode |
| `/yapfactions disband <faction>` | Force disband |

## Power

- `max_power = base-max + members × per-member`
- Claim overlay cost = `ceil(claim_area / claim-blocks-per-power)`
- Available power = max − sum(overlay costs)
- Death power loss and periodic regen (configurable)
- Shield activates when power is depleted (blocks enemy build/PvP on territory)

## Roles

`LEADER` → `OFFICER` → `MEMBER` → `RECRUIT`

## Join modes

- **OPEN** — anyone may `/f join`
- **INVITE** — requires `/f invite` + `/f accept`
- **CLOSED** — no public joins

## Claim integration

When a claim has a faction overlay:

- **Build:** faction members (+ allies if configured) may build; claim owner still uses normal trust rules
- **Chests:** same membership/ally rules via `territory.members-can-open-chests` / `allies-can-open-chests`
- **PvP:** same-faction blocked; allies blocked; enemies allowed on enemy-linked territory (configurable)
- **Shield:** enemies blocked from building/PvP on shielded territory

## Warps & upkeep

- Warps stored in `yap_faction_warps`; optional `warps.require-in-territory`
- Upkeep (`upkeep.enabled`, default **false**): debits bank per linked claim each period
- On unpaid: starts grace timer (`upkeep.grace-hours`); after grace, freezes linked claims (`tax_frozen`)
- `/yapfactions upkeep [faction|all]` force-collects and freezes immediately if bank is short

## Soft perks

- `perks.tab-prefix`: sets YaPPerms user meta `[TAG]` on join via Perms API (cleared on leave)
- `perks.chat-channel-toggle`: `/f chat` / `/f allychat` without args routes public chat
- `discord.role-sync` + `discord.roles`: MC→Discord role grant/revoke on join/leave **and** when the player completes Discord link / `/yapdiscord resync`

## Placeholders (PlaceholderAPI)

| Placeholder | Value |
|-------------|-------|
| `%yapfaction_name%` / `%yapguild_name%` | Faction name |
| `%yapfaction_tag%` | Faction tag |
| `%yapfaction_power%` | Available power |
| `%yapfaction_max_power%` | Max power |
| `%yapfaction_role%` | Player role |
| `%yapfaction_bank%` | Bank balance |
| `%yapfaction_shielded%` | Shield active |
| `%yapfaction_members%` | Member count |
| `%yapfaction_claims%` | Linked claim count |

## Web map tint

For faction-colored claim polygons on YaPMap, enable in `plugins/YaPMap/config.yml`:

```yaml
markers:
  claims: true
  faction-colors: true
```

## Dashboard

`GET /api/factions` — read-only JSON (counts, preview, optional live snapshot).

## Conquest

Hardcore **chunk-grid** land separate from YaPFactions guild overlay.
Owns `yap_conquest_chunks` (world + chunkX/Z → faction_id + power cost).
Does **not** use YaPPlayerData AABB claims or `yap_faction_claims`.

**Opt-in:** jar ships with CORE+NETWORK but `enabled: false` by default.
Requires **YaPFactions** enabled for membership (claim refuses cleanly if missing).

Set `enabled: true` in `plugins/YaPConquest/config.yml`, then `/yapconquest reload` (or restart).

Guild social layer: [GAMEPLAY.md](GAMEPLAY.md).

## Install

```bash
gradle :conquest-plugin:installIntoPlugins
# or:
gradle installProductDefaults
./scripts/seed-defaults.sh   # copies config/defaults/plugins/YaPConquest/ if missing
```

Requires `yap-db.jar` and (soft) `yap-factions.jar` with factions `enabled: true`.

## Feature matrix

| Phase | Feature | Default |
|-------|---------|---------|
| A | Chunk claim/unclaim, power, build/PvP, `/c map` | Plugin `enabled: false` |
| B | Warzone / safezone / wilderness | `zones.enabled: false` |
| C | Overclaim, explosions, fly, combat-tag | Each toggle `false` |

All phases are shipped. Turn on only what you need.

## Phase A — Chunk core

| Feature | Notes |
|---------|-------|
| `/c claim` / `/c unclaim` | Officer+; standing chunk |
| Power budget | `sum(power_cost) ≤ faction.max_power` |
| Build / PvP | Own + allies build; enemy PvP; shield blocks PvP |
| `/c map` | ASCII chunk map |
| Guild bridge | Must be in a YaPFactions faction |

## Phase B — Zones

| Zone | Default rules |
|------|----------------|
| **Wilderness** | Claimable; no forced build/PvP/explode |
| **Warzone** | Not claimable; no build; PvP forced on; explode ok |
| **Safezone** | Not claimable; no build; no PvP; no explode |

World lists + per-chunk overrides (`/yapconquest setzone|clearzone`).

## Phase C — Raid & combat

| Toggle | Behavior |
|--------|----------|
| `overclaim.enabled` | `/c claim` on enemy land when defender `power < land cost` (not shielded/frozen); `require-enemy` default true |
| `explosions.enabled` | `explosions.claimed: deny\|allow` for faction chunks; safezone still denies |
| `fly.enabled` | Auto flight in own territory (`only-own-territory`; if false, allies too); stripped on leave / combat-tag |
| `combat-tag.enabled` | Tag both players on PvP hit; blocks teleport + conquest fly for `duration-seconds` |

## Commands

| Command | Description |
|---------|-------------|
| `/c claim` | Claim or overclaim this chunk |
| `/c unclaim` | Unclaim this chunk |
| `/c map` | Chunk map |
| `/c info` | Claim + zone + overclaim status |
| `/c power` | Faction power vs conquest land |
| `/c help` | Help |
| `/yapconquest reload` | Reload (enable features without restart) |
| `/yapconquest setzone <warzone\|safezone\|wilderness>` | Admin chunk zone |
| `/yapconquest clearzone` | Clear chunk zone override |

Aliases: `/conquest` → `/c`, `/yapc` → `/yapconquest`.

## Permissions

| Node | Default | Grants |
|------|---------|--------|
| `yapconquest.use` | true | `/c` commands |
| `yapconquest.admin` | op | Admin + build/PvP/teleport bypass |

## Config sketch

```yaml
enabled: false
zones:
  enabled: false
overclaim:
  enabled: false
  require-enemy: true
explosions:
  enabled: false
  claimed: deny
fly:
  enabled: false
  only-own-territory: true
combat-tag:
  enabled: false
  duration-seconds: 15
  block-teleport: true
  block-fly: true
```

## Coexistence with YaPFactions

| Plugin | Land model |
|--------|------------|
| YaPFactions | Overlay on playerdata AABB claims (`/f claim`) |
| YaPConquest | Chunk grid (`/c claim`) + optional zones/raid |

Hardcore servers: enable both — Factions for guilds, Conquest for land/war.

## Regions

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

Per-claim overrides persist in `yap_claim_flags` (shared SQL via YaPDB).

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
| `mob-entry` | allow | Hostile mobs cannot enter or remain |
| `item-drop` | allow | Player item drop (trust/bypass can override deny) |
| `item-pickup` | allow | Player item pickup (trust/bypass can override deny) |
| `tnt` | deny | TNT explosion block damage |
| `creeper-explosion` | deny | Creeper explosion block damage |
| `weather` | allow | Deny = clear skies for players in the area (client overlay) |

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
/region flag set spawn damage deny
/region flag set spawn build deny
/region flag set spawn hunger deny
/region flag set spawn item-frame deny
/region flag set spawn armor-stand deny
/region flag set spawn farmland-trample deny
/region flag set spawn leaf-decay deny
/region flag set spawn pistons deny
/region flag set spawn vehicle-place deny
/region flag set spawn vehicle-destroy deny
/region flag set spawn tnt deny
/region gamemode spawn adventure
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

**Enter / leave popups** (title + action bar) are on by default via `plugins/YaPRegions/config.yml` → `notify.*`.
Custom text still works and becomes the subtitle:

```
/region message set spawn greeting Welcome to spawn!
/region message set spawn farewell Goodbye!
```

### Admin vs player claim flags

| Flag | Admin regions (`YaPRegions`) | Player claims (`YaPPlayerData`) |
|------|------------------------------|----------------------------------|
| `pvp`, `mob-damage`, `damage`, `build`, `use`, `interact`, `entry`, `chest-access`, `fire-spread`, `mob-spawning`, `mob-entry`, `weather` | yes | yes |
| `item-drop`, `item-pickup`, `tnt`, `creeper-explosion` | yes | yes |
| `hunger`, `farmland-trample`, `item-frame`, `armor-stand`, `leaf-decay`, `pistons`, `vehicle-place`, `vehicle-destroy` | yes | yes (stored; admin regions enforce) |

### Admin region flags

| Flag | Behavior when **deny** |
|------|------------------------|
| `pvp` | Cancel player vs player damage (incl. projectiles) |
| `mob-damage` | Cancel mob damage to players |
| `damage` | Full safe-zone: players take **no** damage and deal **no** damage (aliases: `invincible`, `god`) |
| `build` | Cancel break/place/buckets |
| `use` | Cancel doors/gates/buttons/levers/pressure plates (default **allow** — parkour works) |
| `interact` | Cancel flower pots / lecterns / jukebox / note block / bell (not doors) |
| `entry` | Cancel move into region |
| `chest-access` | Cancel container open |
| `fire-spread` | Cancel fire spread |
| `mob-spawning` | Cancel natural spawns |
| `mob-entry` | Block hostile mobs from entering; remove if already inside |
| `item-drop` | Cancel player item drop |
| `item-pickup` | Cancel player item pickup |
| `tnt` | Cancel TNT explosion damage to blocks |
| `creeper-explosion` | Cancel creeper block damage |
| `hunger` | Cancel food drain |
| `farmland-trample` | Cancel farmland trampling |
| `item-frame` | Protect item frames / paintings |
| `armor-stand` | Protect armor stands |
| `leaf-decay` | Cancel natural leaf decay |
| `pistons` | Cancel piston extend/retract affecting the region |
| `vehicle-place` / `vehicle-destroy` | Cancel boat/minecart place or break |
| `weather` | Force clear client weather for players inside (world rain continues outside) |

### Region gamemode

Force a mode while players are inside (restored on leave). Staff with land bypass keep their mode.

```text
/region gamemode spawn adventure
/region gamemode spawn clear
```

Unset flags default to **allow**. Overlap: highest priority, then smallest volume.
Flag changes apply **immediately** (no restart). Gamemode applies to players already inside.
**OP / `yap.bypass` / `yapregions.admin` bypass land flags** (`build`, `use`, `interact`, chests, frames, vehicles) — test with a non-op account.
After deploying a new `yap-regions.jar`, do a full server restart (or disable+enable the plugin); `/region reload` only reloads YAML + DB, it does not load new listener code.
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
