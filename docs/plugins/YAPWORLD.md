# YaPWorld

Folia-safe **FAWE-class** world editing + Multiverse-class world management.
Replaces stock WorldEdit / FAWE on the YaPcore product path
([FAWE Hangar](https://hangar.papermc.io/IntellectualSites/FastAsyncWorldEdit) feature surface — first-party, not the FAWE jar).

## Quick start

```text
/yapworld          → in-game GUI
/yapworld tool     → golden axe wand
  Left-click  = pos1
  Right-click = pos2
  Shift+RMB   = GUI

//gmask #air
//set stone
//sel sphere
//copy → //paste
//brush smooth 5
```

Aliases: `/we`, `/worldedit`, `/mv`. Classic `//…` commands work like WorldEdit/FAWE.

## FAWE-parity matrix

| Area | Status |
|------|--------|
| Cuboid `//` edit + clipboard | **Done** |
| Patterns (`%`, block-data, `#solid`) | **Phase 1 done** |
| `//mask` / `//gmask` | **Phase 1 done** |
| `//sel cuboid\|sphere\|cyl\|poly` | **Phase 1 done** |
| Biomes / regen / forest / deform / twist | **Phase 1 done** |
| Brushes: sphere, cyl, smooth, gravity, clipboard, butcher | **Phase 1 done** |
| Tools: farwand, superpickaxe, info, tree | **Phase 1 done** |
| Parallel chunk apply, `//fast`, limits | **Phase 2 done** |
| Entity schematics (`.yschem` v2) + `EditApplyService` API | **Phase 3 done** |
| WorldEdit API shim (`com.sk89q.worldedit` soft-deps) | **Phase 4 planned** |
| WorldEditCUI selection outlines | **Phase 4 planned** |
| Tile-entity NBT schematics + multi-clipboard | **Phase 4 planned** |
| Clipboard web upload/download | **Phase 4 planned** (optional) |
| NMS section placement / FAWE CFI | Out of scope / Phase 4.5 only if needed |

## WorldEdit-class commands

| Area | Commands |
|------|----------|
| Selection | `//sel` `//wand` `//farwand` `//pos1` `//pos2` `//expand` `//contract` `//shift` `//outset` `//inset` `//chunk` `//size` `//desel` |
| Masks | `//mask` `//gmask` (`#air` `#solid` `#existing` `#region` materials `!negate`) |
| Edit | `//set` `//replace <mask> <pattern>` `//walls` `//faces` `//hollow` `//outline` `//overlay` `//smooth` `//naturalize` |
| Generate | `//cyl` `//sphere` `//pyramid` `//line` `//drain` `//regen` `//forest` `//flora` `//pumpkins` |
| Biome/deform | `//setbiome` `//biomeinfo` `//biomelist` `//deform` `//twist` `//center` `//curve` |
| Clipboard | `//copy` `//cut` `//paste [-a]` `//rotate` `//flip` `//stack` `//move` |
| Near/util | `//replacenear` `//removeabove` `//removebelow` `//extinguish` `//green` `//snow` `//thaw` |
| Nav | `//thru` `//jumpto` `//up` `//ascend` `//descend` |
| History | `//undo` `//redo` `//clearhistory` `//fast` |
| Brush | `//brush sphere\|cyl\|smooth\|gravity\|clipboard\|butcher <r> [pat]` `//size` `//mat` |
| Tools | `//superpickaxe` `//info` `//tree` `//none` |

Patterns: `stone`, `50%stone,50%dirt`, `oak_log[axis=y]`, `#solid`.

## Soft API

Other plugins can use Bukkit services:

- `SelectionService` — pos1/pos2 cuboid (regions / pregen)
- `EditApplyService` — `fillPattern` / `replaceMask` (not `com.sk89q.worldedit`)

```java
WorldServices.editApply().ifPresent(edit ->
    edit.fillPattern(player, sel, "stone"));
```

## Permissions

| Node | Default | Use |
|------|---------|-----|
| `yapworld.selection` | op | Wand, GUI, `//` edits |
| `yapworld.brush` | op | Brush mode |
| `yapworld.schematic` | op | Save/paste schematics |
| `yapworld.editor` | op | Browser studio |
| `yapworld.pregen` | op | Pregen from selection |
| `yapworld.load` / `unload` / `teleport` | op | World mgmt |
| `yapworld.admin` | op | Reload / status |

## Config

`plugins/YaPWorld/config.yml` — max volume, brush radius, undo depth, `limits.*` (max-changes, parallel-chunks), browser editor port.

## Related

[PERMISSIONS.md](../ops/PERMISSIONS.md) · [REGIONS.md](../gameplay/REGIONS.md) · [PREGEN.md](PREGEN.md) · [PLUGIN_COMPAT_MATRIX.md](PLUGIN_COMPAT_MATRIX.md)
