# YaPWorld

Folia-safe **WorldEdit-class** world editing + Multiverse-class world management.
Replaces stock WorldEdit / FAWE on the YaPcore product path.

## Quick start (easiest)

```text
/yapworld          → opens the in-game GUI
/yapworld tool     → golden axe
  Left-click block  = pos1
  Right-click block = pos2
  Shift + right-click = GUI
```

1. Get the tool (`/yapworld tool` or GUI → Get edit tool).
2. Select two corners.
3. Pick a material in the GUI palette.
4. Click **Fill**, **Walls**, or **Hollow**.
5. **Undo** / **Redo** if needed.

Aliases: `/we`, `/worldedit`, `/mv` (world load/unload/tp).

## Commands

| Command | What it does |
|---------|----------------|
| `/yapworld` / `gui` | In-game editor |
| `/yapworld tool` | Give golden axe |
| `/yapworld editor` | Optional browser studio |
| `/yapworld pos1` / `pos2` / `clear` | Selection helpers |
| `/yapworld fill\|walls\|shell\|hollow\|outline [mat]` | Edit selection |
| `/yapworld replace <from> <to>` | Replace blocks in selection |
| `/yapworld brush <r> [mat]` | Sphere brush (blaze rod) |
| `/yapworld schem save\|paste\|import` | Schematics (`.yschem` / `.schem`) |
| `/yapworld undo` / `redo` | History |
| `/yapworld pregen start` | Pregen current selection (needs YaPPregen) |
| `/yapworld load\|unload\|tp` | World management |

## Permissions

| Node | Default | Use |
|------|---------|-----|
| `yapworld.selection` | op | Wand, GUI, fill/walls |
| `yapworld.brush` | op | Brush mode |
| `yapworld.schematic` | op | Save/paste |
| `yapworld.editor` | op | Browser studio |
| `yapworld.pregen` | op | Pregen from selection |
| `yapworld.load` / `unload` / `teleport` | op | World mgmt |
| `yapworld.admin` | op | Reload / status |

## Regions + pregen

- **YaPRegions** `/region define` uses YaPWorld selection.
- **YaPPregen** `selection` / `sel` / `yapworld` prefers YaPWorld (falls back to WorldEdit on Paper benches).

## Config

`plugins/YaPWorld/config.yml` — max volume, brush radius, schematics folder, browser editor port.

## Related

[PERMISSIONS.md](PERMISSIONS.md) · [REGIONS.md](REGIONS.md) · [PREGEN.md](PREGEN.md) · [PLUGIN_COMPAT_MATRIX.md](PLUGIN_COMPAT_MATRIX.md)
