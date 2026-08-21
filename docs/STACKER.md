# YaP Stacker

First-party **VortexStacker-class** mob / item / spawner stacker for YaPcore / Paper 26.2.
Shipped as `plugins/yap-stacker.jar` (default product install).

**Design:** stack sizes live in Bukkit **PersistentDataContainer** (PDC) on entities,
item meta, and spawner tile states — **no NMS**, so it stays stable across Paper updates.

**Not a YaP `modules/` jar.** Runtime is a normal Paper plugin (`plugin.yml`). A thin
`provides: [stacker]` packaging module is optional later; v1 does not need one.

## Commands

`/yapstacker` (alias `stacker`) — permission `yapstacker.admin` (op):

```
/yapstacker status
/yapstacker reload
/yapstacker gui
/yapstacker stats
/yapstacker give <wand|tool|aura> [player]
```

| Permission | Default | Role |
|------------|---------|------|
| `yapstacker.admin` | op | Full admin |
| `yapstacker.gui` | op | Open admin chest GUI |
| `yapstacker.give` | op | Give tools |
| `yapstacker.wand` | op | Spawner wand |
| `yapstacker.tool` | op | Mob stack tool |
| `yapstacker.aura` | op | Kill-aura item |

## Features

| Area | Behavior |
|------|----------|
| **Mobs** | Same-type merge on spawn; nametag `{type} x{size}`; sheep color / slime size / age gates |
| **Kill modes** | `DECREMENT` (default) — kill one, respawn stack−1 · `INSTANT` — multiply loot/XP |
| **Per-mob rules** | `mob-rules` — max stack, loot/XP multipliers, slime preserve, sheep color |
| **Items** | Ground-item merge; PDC for counts above vanilla max; full pickup |
| **Spawners** | Place-merge, break-one (sneak = whole stack), wand GUI / absorb nearby |
| **Tools** | Wand (spawners), tool (force-merge mobs), kill aura (held item pulses) |
| **Remerge** | Chunk `EntitiesLoadEvent` + periodic wander merge |
| **Hooks** | Soft-skip Citizens NPCs + MythicMobs (`softdepend`) |
| **PlaceholderAPI** | `%yapstacker_*%` when `yap-placeholderapi` is present |
| **Metrics** | In-process counters via `/yapstacker stats` and admin GUI |

## Config

`plugins/YaPStacker/config.yml` (created on first enable). Defaults:

- Mobs: merge radius `5`, max stack `100`, kill mode `DECREMENT`
- Items / spawners: enabled with their own radii and caps
- Blacklist: dragon, wither, warden, elder guardian
- Skip named / tamed / leashed by default

Rebuild / install:

```bash
gradle :stacker-plugin:installIntoPlugins
# or full product defaults
gradle shadowJar
```

## Placeholders (`%yapstacker_<id>%`)

| Id | Value |
|----|--------|
| `enabled` | Global enable |
| `kill_mode` | `DECREMENT` / `INSTANT` |
| `mob_merges` / `merges` | Merge counter |
| `mob_kills` / `kills` | Stacked death handling counter |
| `item_merges` | Item merges |
| `spawner_stacks` / `spawners` | Spawner stack ops |
| `aura_kills` | Kill-aura pulses |
| `max_stack` | Global mob max |
| `merge_radius` | Mob merge radius |

## Manual test checklist

1. Spawn eggs → zombies merge → nametag `Zombie xN`
2. Kill once → stack decreases by 1, 1× loot (`DECREMENT`)
3. Drop many diamonds → one ground stack with count nametag when over 64
4. Place spawners of same type nearby → absorb; wand right-click opens GUI
5. `/yapstacker give aura` → hold near stacked mobs → units die one-by-one

## Related

- [PLUGINS.md](PLUGINS.md) — plugin folder layout
- [PLACEHOLDERAPI.md](PLACEHOLDERAPI.md) — built-in PAPI
- [MODULES_AND_API.md](MODULES_AND_API.md) — plugin vs module
- `stacker-plugin/` — source
