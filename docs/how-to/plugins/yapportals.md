# How to use YaPPortals

**Jar:** `yap-portals.jar`  
Colored walk-through pads that send players between fleet backends via YaP Link, or warp locally to spawn/RTP/home.

## What it does

Pads are stained-glass volumes. Walk in → Connect to another Folia JVM (or local teleport). Requires **YaP Link** for cross-server.

## Prerequisites

- Join through Link (`:25565`), not direct Folia ports
- Matching `server-id` / `target-server` names (`lobby`, `survival`, `creative`, …)
- Destination `/setspawn` for spawn arrival
- Resource pack accepted (portal textures)

## Quick start — hub → survival

On **lobby**, select a region (wand) or use `at` coords:

```text
/portal wand
# left-click pos1, right-click pos2 — or:
/portal create to-survival survival lime
```

On **survival**:

```text
/setspawn
/portal create to-hub lobby purple
```

Walk the lobby pad → should Connect → see `Arrived at survival spawn.`

## Create & edit

| Command | Purpose |
|---------|---------|
| `/portal wand` · `pos1` · `pos2` | Selection |
| `/portal create <name> <server> [color]` | From selection |
| `/portal create <name> <server> [color] at <world> x1 y1 z1 x2 y2 z2` | Exact cuboid |
| `/portal delete <name>` | Remove |
| `/portal list` · `info <name>` | Inspect |
| `/portal enable\|disable <name>` | Toggle |
| `/portal settarget <name> <server>` | Change destination |
| `/portal setcolor <name> <dye>` | Look |
| `/portal setarrival <name> spawn\|rtp\|home\|island [homeName]` | Landing mode |
| `/portal setcooldown <name> <sec>` | Delay |
| `/portal setperm <name> [node]` | Extra permission |
| `/portal setmessage <name> [text]` | `{server}` ok |
| `/portal setshape <name> <full\|frame\|oval\|…>` | Fill style |
| `/portal paint <name\|off>` | Wand paint mode |
| `/portal go <name\|server>` | Admin force transfer |
| `/portal reload` | Reload YAML |

## Arrival modes

| Mode | Behavior |
|------|----------|
| `spawn` (default) | Destination `/setspawn` (Essentials), else world spawn |
| `rtp` | Essentials RTP on destination |
| `home` | PlayerData `/sethome` (fallback spawn if missing) |
| `island` | YaPblock island home (auto-create on first visit; needs `yap-block.jar`) |

Same-server pads (target = this `server-id`) teleport locally — no Link Connect.

```text
/portal setarrival to-survival-wild rtp
/portal setarrival to-survival-home home cabin
/portal setarrival to-skyblock island
```

## Player-built End doors

On survival (same Folia world as `world_the_end`), players can build a **vertical** End portal:

1. Build a standing **hollow obsidian ring** like a nether portal (about **4×5**, corners optional) — not crying obsidian
2. **Clear the middle** (air or cave air only — no water/stone/torches in the opening)
3. Right-click any **obsidian** frame block with an **Ender Eye** (main or off hand)
4. The opening fills with **black stained glass** (not a real nether-portal block) so Folia never starts a Nether hop
5. Walk through → teleport to The End spawn on this server

If lighting fails, chat names the real problem (e.g. clear water inside) instead of nearby terrain.

Claimed doors are **private by default** (same flag as nether portals):

```text
/claim flag set nether-portal allow
```

Config: `end-doors` in `plugins/YaPPortals/config.yml` (`interior: BLACK_STAINED_GLASS`). Dungeon base portals use **crying obsidian** frames so they do not collide with End doors; walk-through opens the dungeon level picker and does not send you to the Nether.

Vanilla flat End portals still work; those in claims are gated the same way.

## Troubleshoot

| Symptom | Fix |
|---------|-----|
| Nothing happens | Join via Link; check `enabled: true`; stand inside pad |
| Transfer but wrong place | `/setspawn` on destination; ensure new portals jar; look for `Portal arrival` in dest log |
| `/hub` / failover not at spawn | SoftSwitch marks SPAWN pending; destination needs YaPPortals + `/setspawn`; lobby/creative `teleport-on-join: true` |
| “Already logged in” | `/yapdata unlock <player>`; wait for session unlock |
| Purple missing textures | Accept pack; rebuild `yapcore-default.zip` |
| Return pad missing | Recreate on that backend; check `portals.yml` not `{}` |

## Related

[PORTALS.md](../../network/PORTALS.md) · [YAP_LINK.md](../../network/YAP_LINK.md) · [yapessentials.md](yapessentials.md)
