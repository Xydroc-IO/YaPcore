# YaPPortals — fleet walk-through portals

First-party Folia plugin that sends players between fleet backends through **YaP Link**.
**CORE+NETWORK product default** — `yap-portals.jar` seeds every fleet instance and
`enabled: true` in shipped YAML. Uses the same BungeeCord `Connect` channel that
`yaplink-server-selector` already handles (`/hub`, `/server`).

## Why not only vanilla nether portals?

Minecraft only has **one** nether-portal block look (purple). Vanilla nether and
end portals still hop like Paper. A real portal **inside an enabled YaP pad** is
cancelled so Link Connect is not raced by a local `world_nether` hop. Hub
transfers use stained-glass pads, not a vanilla frame.

YaPPortals uses:

- Interior fill: dye-colored **stained glass** blocks
- Look: **YaP Portals** resource-pack textures (animated portal sheets for all 16 dyes —
  `scripts/packs/generate-yap-portals.py`, built into `yapcore-default.zip`)
- Soft particle swirl on top
- Enter by walking into / against the pad (adjacent blocks count)

Set color with `/portal setcolor <name> lime` (or create with a color arg).
Accept the server resource pack so the portal sheets load.
Existing pads are repainted on `/portal reload` or plugin enable. Fill only replaces
**air, nether portal, and stained glass** — signs and solid builds in the volume are left
alone.

## Player-built End doors

YaPPortals also supports **vertical End doors** on the same Folia backend as
`world_the_end`: build a standing 4×5 obsidian frame, right-click with an Ender Eye,
fill with **black stained glass** (not `NETHER_PORTAL`), walk through → End spawn.
Claimed doors use the same private-by-default `nether-portal` / `portal` claim flag
as vanilla nether portals. See [yapportals.md](../how-to/plugins/yapportals.md).

## Spawn arrival

Default landing is **server spawn** (`/setspawn` via YaPEssentials, else world spawn):

```text
# Hub → survival spawn (arrival defaults to spawn — no setarrival needed)
/portal create to-survival survival lime

# Hub → factions
/portal create to-factions factions orange

# Same-server pad that warps to this backend's spawn
/portal create to-spawn survival cyan
/portal setarrival to-spawn spawn
```

Same-server spawn pads do not use Link Connect — walk in and teleport to spawn.
Cross-server pads Connect then land at the destination’s spawn on join.

## Random / wild arrival (RTP)

Portals can land players on a **random safe spot** instead of spawn:

```text
# Hub → survival wild
/portal create to-survival-wild survival lime
/portal setarrival to-survival-wild rtp

# Same-server wild pad on survival (target = this server-id)
/portal create wild survival lime
/portal setarrival wild rtp
```

Requires **YaPEssentials** RTP on the destination (`/rtp` / `/wild`). Claimed land is skipped when
`rtp.avoid-claims: true`. Same-server wild pads do not use Link Connect.

Players can also run `/rtp` (alias `/wild`) directly on survival/factions backends.

## Home arrival

Portals can land players at **their** YaPPlayerData home (`/sethome`):

```text
# Same-server pad on survival (target = this server-id)
/portal create home-pad survival lime
/portal setarrival home-pad home

# Named home (optional)
/portal setarrival home-pad home cabin

# Lobby → survival home
/portal create to-survival-home survival cyan
/portal setarrival to-survival-home home
```

Requires **YaPPlayerData** with `features.homes: true` on the destination. Same-server home
pads do not use Link Connect. If the player has no home set, they get a message (fleet
transfers fall back to spawn).

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
   Set `inventory-profile: server` on creative / minigame backends so they keep a separate
   inventory (profile key = that instance’s `server-id`).

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
| `/portal settarget\|setperm\|setcooldown\|setmessage\|setcolor\|setarrival` | Meta (`setarrival` = spawn / rtp / home) |
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
