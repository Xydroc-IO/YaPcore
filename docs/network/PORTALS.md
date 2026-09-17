# YaPPortals — fleet walk-through portals

First-party Folia plugin that sends players between fleet backends through **YaP Link**.
**CORE+NETWORK product default** — `yap-portals.jar` seeds every fleet instance and
`enabled: true` in shipped YAML. Uses the same BungeeCord `Connect` channel that
`yaplink-server-selector` already handles (`/hub`, `/server`).

## Why not only vanilla nether portals?

Minecraft only has **one** nether-portal block look (purple). Lighting a real
`NETHER_PORTAL` on a lobby also triggers a dimension hop — bad on hubs.

YaPPortals uses:

- Interior fill: dye-colored **stained glass** blocks
- Look: **YaP Portals** resource-pack textures (animated portal sheets for all 16 dyes —
  `scripts/generate-yap-portals.py`, built into `yapcore-default.zip`)
- Soft particle swirl on top
- Enter by walking into / against the pad (adjacent blocks count)

Set color with `/portal setcolor <name> lime` (or create with a color arg).
Accept the server resource pack so the portal sheets load.
Existing pads are repainted on `/portal reload` or plugin enable.

## Requirements

1. YaP Link running with `plugins-enabled=true` and `yaplink-server-selector` installed
2. `yap-portals.jar` on each Folia backend that should host portals (lobby is typical)
3. `server-id` in `plugins/YaPPortals/config.yml` matches this backend’s Link name (`lobby`, `survival`, …)
4. Portal `target-server` values match Link `servers.*` keys exactly
5. **Inventory across backends:** `yap-playerdata.jar` on every backend, shared YaPDB,
   unique `server-id` per instance, and defaults `inventory-profile: global` +
   `sync.inventory: true` (enderchest / XP / vitals similarly). Portal Connect → quit/join
   save/load carries the same profile — including YaPItems custom weapons. Item *definitions*
   also sync fleet-wide on create (see [YAPITEMS.md](../plugins/YAPITEMS.md)).
   Set `inventory-profile: server` only for minigame wipes.

`/hub` remains a **Link** command (selector plugin). YaPPortals does not replace it.

## Quick setup (lobby → survival)

```text
/portal wand
# left-click pos1, right-click pos2 on the pad
/portal create to-survival survival
# or pick a color:
/portal create to-survival survival lime
/portal setcolor to-survival red
```

Or with explicit coordinates:

```text
/portal create to-survival survival cyan at world 0 64 0 2 66 2
```

Walk into the volume. Players joining through Link are sent to `survival` and
land at that backend’s spawn (`/setspawn` via YaPEssentials, else world spawn).

## Commands (`yapportals.admin`)

| Command | Effect |
|---------|--------|
| `/portal wand` | Give selection wand (left=pos1, right=pos2) |
| `/portal pos1` / `pos2` | Set corner at feet |
| `/portal create <name> <server> [color]` | Define from wand selection |
| `/portal delete <name>` | Remove |
| `/portal list` / `info <name>` | Inspect |
| `/portal settarget\|setperm\|setcooldown\|setmessage\|setcolor` | Meta |
| `/portal enable\|disable <name>` | Toggle |
| `/portal go <name\|server>` | Admin test transfer |
| `/portal reload` | Reload config + portals.yml |

Colors are dye names: `purple`, `lime`, `red`, `blue`, `cyan`, `orange`, …

## Permissions

| Node | Default | Role |
|------|---------|------|
| `yapportals.use` | true | Enter portals |
| `yapportals.admin` | op | Manage portals |
| `yapportals.bypass.cooldown` | op | Skip cooldown |
| `yapportals.bypass.permission` | op | Skip per-portal permission |

Starter packs grant `yapportals.use` to `default`.

## NPC hub transfers

With YaPNpcs + YaPPortals:

```text
/npc setserver hub-guide survival
```

Action DSL: `server:survival` (aliases `transfer:`, `connect:`).

## Threading

| Path | Lane |
|------|------|
| `PlayerMoveEvent` containment | Region (entity) thread |
| `sendPluginMessage(Connect)` | `YapSched.entity` |
| `portals.yml` save/load | Async scheduler |

Never blocks the region tick on I/O.

## Files

| Path | Purpose |
|------|---------|
| `plugins/YaPPortals/config.yml` | server-id, default color, messages, channel |
| `plugins/YaPPortals/portals.yml` | Portal definitions (`color` per portal) |

See also: [YAP_LINK.md](YAP_LINK.md) · Link `server-selector` README.
