# YaPWorld

Folia-safe **WorldEdit-class** editing + Multiverse-class world management.
Replaces stock WorldEdit / FAWE on the YaPcore product path.

## Quick start

```text
/yapworld          → in-game GUI
/yapworld tool     → golden axe wand
  Left-click  = pos1
  Right-click = pos2
  Shift+RMB   = GUI

//set stone
//copy → //paste
//cyl stone 5 3
```

1. `/yapworld tool` (or GUI → Get edit tool).
2. Select two corners.
3. Use `//set`, GUI **Fill**, or generate with `//sphere` / `//cyl`.
4. Clipboard: `//copy` / `//cut` / `//paste` / `//rotate` / `//flip`.
5. `//undo` / `//redo` as needed.

Aliases: `/we`, `/worldedit`, `/mv`. Classic `//…` commands work the same as WorldEdit.

## WorldEdit-class commands

| Area | Commands |
|------|----------|
| Selection | `//wand` `//pos1` `//pos2` `//expand` `//contract` `//shift` `//outset` `//inset` `//chunk` `//size` `//desel` |
| Edit | `//set` `//replace` `//walls` `//faces` `//hollow` `//outline` `//overlay` `//smooth` `//naturalize` |
| Generate | `//cyl` `//hcyl` `//sphere` `//hsphere` `//pyramid` `//line` `//drain` |
| Clipboard | `//copy` `//cut` `//paste [-a]` `//rotate` `//flip` `//stack` `//move` |
| Near/util | `//replacenear` `//removeabove` `//removebelow` `//extinguish` `//green` `//snow` `//thaw` |
| Nav | `//thru` `//jumpto` `//up` `//ascend` `//descend` |
| History | `//undo` `//redo` |
| Brush | `//brush sphere\|cyl <r> [mat]` |

Patterns accept percentages, e.g. `50%stone,50%dirt`.

Same ops also work as `/yapworld set …`, `/we copy`, etc.

## Plugin commands

| Command | What it does |
|---------|----------------|
| `/yapworld` / `gui` | In-game editor (copy/paste/expand/sphere/cyl buttons) |
| `/yapworld tool` | Give golden axe |
| `/yapworld editor` | Optional browser studio |
| `/yapworld schem save\|paste\|import` | Schematics (`.yschem` / `.schem`) |
| `/yapworld load\|unload\|tp` | World management |
| `/yapworld pregen start` | Pregen selection (needs YaPPregen) |

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

## Regions + pregen

- **YaPRegions** `/region define` uses YaPWorld selection.
- **YaPPregen** `selection` prefers YaPWorld (falls back to WorldEdit on Paper benches).

## Config

`plugins/YaPWorld/config.yml` — max volume, brush radius, schematics folder, browser editor port.

## Related

[PERMISSIONS.md](../ops/PERMISSIONS.md) · [REGIONS.md](../gameplay/REGIONS.md) · [PREGEN.md](PREGEN.md) · [PLUGIN_COMPAT_MATRIX.md](PLUGIN_COMPAT_MATRIX.md)
