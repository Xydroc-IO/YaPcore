# How to use YaPblock

**Jar:** `yap-block.jar` (GAMEPLAY)  
Classic **grid skyblock** on a dedicated Folia backend: void world, island paste, protection, invites, upgrades, cobble generators, and levels.

## What it does

| Who | What |
|-----|------|
| Players | `/is create` · home · invite coop · visit · settings · upgrades · level/top |
| Staff | `/yapblock tp` · disband · setlevel · reload |
| Ops | Fleet `skyblock` JVM, hub portal with `setarrival island`, generators / block-values YAML |

Islands live in world `yapblock` (config) on a **spiral grid** (default 200 blocks apart). Protection is YaPblock’s own (not YaPClaims). Inventory on this backend is **private** (`inventory-profile: server`) — lobby/survival/factions stay on `global`.

## Prerequisites

- Fleet instance **`skyblock`** running (port `25571` by default) with `yap-block.jar`
- Join through **YaP Link** (`:25565`), not the raw Folia port
- **YaPDB** (shared SQL) recommended
- Soft: **YaPWorld** (schematic paste), **YaPPlayerData** (upgrade costs), **PlaceholderAPI**
- Soft: **YaPPortals** on lobby for walk-through → island

```bash
gradle :yapblock-plugin:installIntoPlugins
# Start skyblock from Fleet GUI / POST /api/fleet action=start id=skyblock
```

## How the game works (short)

1. Player gets an **island** (portal auto-create, or `/is create`).
2. They build inside their **radius**; outsiders cannot build (unless trusted / public visit).
3. **Lava + water** cobble gens drop tiered ores from `generators.yml`.
4. **`/is level`** scans blocks using `block-values.yml`; **`/is top`** ranks islands.
5. **Invite** friends as members; **trust** guests to build; **visit** open islands.

---

## Quick start — hub portal → your island

Goal: walk a lobby pad → Connect to `skyblock` → land on **your** island (created automatically the first time).

### On skyblock (once)

```text
/setspawn
# Optional: stand at a hub dock in world "world" if you want a non-island spawn for staff
```

Confirm YaPblock is on:

```yaml
# plugins/YaPblock/config.yml
enabled: true
world:
  name: yapblock
```

### On lobby

Select a pad (wand) or use `at` coords, then:

```text
/portal create to-skyblock skyblock cyan
/portal setarrival to-skyblock island
```

Aliases for arrival: `island` · `skyblock` · `is` · `ishome`.

### Return pad (on skyblock → lobby)

```text
/portal create to-lobby lobby purple
/portal setarrival to-lobby spawn
```

### Player test

1. Join via Link → lobby  
2. Walk **to-skyblock** pad  
3. First visit: island is **created + pasted**, then you teleport home  
4. Later visits: straight to `/is home`  
5. Chat: `Arrived at your island.`

If YaPblock is missing on the destination, arrival falls back to skyblock **spawn**.

---

## Getting started as a player

### First island (no portal)

```text
/is create
/is home
/is sethome          # stand where you want the bed/home point
/is info
```

### With the hub portal

Walk the skyblock portal — you do **not** need `/is create` first. First trip creates the island for you.

### Coop / invite

Owner on their island:

```text
/is invite Steve
# or: /is coop Steve
```

Steve (online on skyblock, and **not** already owning an island):

```text
/is accept          # or click [Accept] in chat → join as MEMBER + teleport home
/is deny            # or click [Deny]
```

If Steve already has an island (portal auto-create counts), they must `/is leave` or `/is delete` first, then re-invite.

Owner tools:

```text
/is kick Steve
/is ban Steve
/is unban Steve
/is trust Alex      # build guest (TRUSTED), not a full member slot
/is untrust Alex
/is leave           # members leave (owners use /is delete)
```

Invites expire (default **5 minutes** — `invites.expire-minutes`).

### Visit someone else’s island

```text
/is visit Steve
```

Needs `yapblock.visit`. Target island must allow **PUBLIC_VISIT** (default on) and not be **LOCK**ed; banned players cannot enter.

### Settings (owner GUI)

```text
/is settings
```

| Flag | Default | Meaning |
|------|---------|---------|
| PVP | off | Player combat on the island |
| MOB_SPAWN | on | Natural animal/monster spawns |
| FIRE | on | Fire / lava spread |
| PUBLIC_VISIT | on | Strangers can `/is visit` |
| LOCK | off | Only OWNER/MEMBER can build (trusted blocked) |

### Upgrades (economy)

```text
/is upgrade size
/is upgrade members
/is upgrade generator
```

Costs come from YaPPlayerData balance (`upgrades:` in `config.yml`). Generator tier changes cobble-gen loot tables.

### Level & top

```text
/is level           # scan island AABB for block values
/is top
/is top 15
```

### Delete / reset

```text
/is delete          # then /is confirm within 30s — permanent remove
/is reset           # then /is confirm — clear radius + re-paste starter (keep ownership)
```

Void fall (`Y < island.void-y`, default **8**) → auto `/is home` (clears fall distance).

Death / respawn on the void world → island home (or bed/anchor on an enterable island). Vanilla world spawn is unused for island members.

---

## Commands

### Players (`/is` · `/island`) — `yapblock.use`

| Command | Example | Result |
|---------|---------|--------|
| `create` | `/is create` | New grid slot + starter/schematic |
| `home` | `/is home` | Teleport to island spawn |
| `sethome` | `/is sethome` | Set home (on your island) |
| `info` | `/is info` | Id, level, size, role |
| `level` | `/is level` | Rescan value |
| `top [n]` | `/is top 10` | Leaderboard |
| `invite` | `/is invite Steve` | Coop invite |
| `accept` / `deny` | `/is accept` | Take / refuse invite |
| `kick` / `ban` / `unban` | `/is kick Steve` | Membership |
| `trust` / `untrust` | `/is trust Alex` | Build guest |
| `leave` | `/is leave` | Leave coop |
| `visit` | `/is visit Steve` | Warp to their island (`yapblock.visit`) |
| `settings` | `/is settings` | Flag GUI |
| `upgrade …` | `/is upgrade size` | Buy upgrade (`yapblock.upgrade`) |
| `delete` / `reset` / `confirm` | `/is delete` → `/is confirm` | Lifecycle |

### Admin (`/yapblock`) — `yapblock.admin`

| Command | Example |
|---------|---------|
| `reload` | `/yapblock reload` |
| `tp` | `/yapblock tp Steve` or `/yapblock tp 12` |
| `disband` | `/yapblock disband Steve` |
| `setlevel` | `/yapblock setlevel Steve 5000` |

Bypass protection: `yapblock.admin.bypass`.

---

## Roles

| Role | Build | Manage invite/settings/upgrades | Notes |
|------|-------|----------------------------------|-------|
| OWNER | yes | yes | One per island |
| MEMBER | yes | no | Uses a member slot |
| TRUSTED | yes | no | Guest builder; not a member slot |
| BANNED | no | no | Cannot enter |

---

## Fleet backend

| Field | Value |
|-------|--------|
| Id / server-id | `skyblock` |
| Port | `25571` |
| Inventory | `server` (private) |
| YaPblock | `enabled: true` |
| YaPFactions | `enabled: false` |

Link: `servers.skyblock=127.0.0.1:25571`.

---

## Config

| File | Purpose |
|------|---------|
| `plugins/YaPblock/config.yml` | World name, grid distance, paste Y, upgrades, invites |
| `plugins/YaPblock/generators.yml` | Cobble-gen weighted tables by tier |
| `plugins/YaPblock/block-values.yml` | Level scan points per material |
| `plugins/YaPblock/schematics/` | Optional `island.schem` (YaPWorld); else built-in starter |

Built-in starter (when no schematic is present):

| Island | Qty |
|--------|-----|
| Grass | ~27–30 |
| Dirt | ~51 |
| Bedrock | 1 (bottom center) |
| Oak tree | 1 (~6 logs) |

No sand on the starter (cactus stays in the chest).

**Starter chest:** water bucket, lava bucket, ice×2, sugar cane, melon slice, pumpkin seeds, cactus, string×12, bone, red mushroom, brown mushroom.

Skyblock fleet profile disables **YaPSkills**, **YaPClaims**, and **YaP420** (island protection replaces claims).

Important knobs:

```yaml
world:
  name: yapblock
  grid-distance: 200
  paste-y: 64
island:
  default-size-radius: 50
  default-max-members: 4
  default-gen-tier: 0
  void-y: 8
```

---

## Placeholders

`%yapblock_level%` · `%yapblock_rank%` · `%yapblock_owner%` · `%yapblock_members%` · `%yapblock_size%` · `%yapblock_gen%` · `%yapblock_has_island%`

---

## Troubleshoot

| Symptom | Fix |
|---------|-----|
| Portal lands at spawn, not island | `/portal setarrival <name> island`; YaPblock enabled on skyblock; check dest log for `Portal arrival … island` |
| Create / portal create fails | SQL / YaPDB; world generator `YaPblock`; skyblock console errors |
| Cannot build | Not member/trusted, or LOCK; staff need `yapblock.admin.bypass` |
| Invite ignored | Target must be online; invite TTL; island at max members → upgrade members |
| Friend already has island | They `/is leave` or `/is delete`, then re-invite |
| Die / can't respawn | Respawn goes to island home; void rescue below `island.void-y` |
| Can't break / empty chest | Skyblock must **not** have YaPRegions `spawn-pad` (first island is at 0,0). `/region remove spawn` + `spawn-pad.enabled: false`. Then `/is reset` |
| Skills / claims on skyblock | Disable `YaPSkills` + `claims.enabled` + `YaP420` on the skyblock instance (fleet game profile does this) |
| Visit denied | PUBLIC_VISIT off, LOCK on, or banned |
| Upgrades fail | YaPPlayerData economy on + balance |
| Empty gen | Upgrade generator; check `generators.yml` weights |
| Wrong inventory vs survival | Expected — skyblock uses `inventory-profile: server` |

---

## Related

- [yapportals.md](yapportals.md) — pads, `setarrival island`
- [PORTALS.md](../../network/PORTALS.md) — fleet portal reference
- [yapplayerdata.md](yapplayerdata.md) — economy / inventory profiles
- [PLUGINS.md](../../plugins/PLUGINS.md) · [PERMISSIONS.md](../../ops/PERMISSIONS.md)
