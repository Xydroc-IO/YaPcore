# YaP Conquest

Hardcore **chunk-grid** land separate from YaPFactions guild overlay.
Owns `yap_conquest_chunks` (world + chunkX/Z → faction_id + power cost).
Does **not** use YaPPlayerData AABB claims or `yap_faction_claims`.

**Opt-in:** jar ships with CORE+NETWORK but `enabled: false` by default.
Requires **YaPFactions** enabled for membership (claim refuses cleanly if missing).

Set `enabled: true` in `plugins/YaPConquest/config.yml`, then `/yapconquest reload` (or restart).

Guild social layer: [FACTIONS.md](FACTIONS.md).

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
