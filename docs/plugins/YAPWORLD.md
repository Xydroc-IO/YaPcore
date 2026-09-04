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
//copy → //paste -ae
//brush smooth 5
//generate y<noise*0.4+0.5 stone
//fixlighting
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
| Entity schematics (`.yschem` v2+) + `EditApplyService` API | **Phase 3 done** |
| WorldEdit API shim (`com.sk89q.worldedit` / `WorldEdit.jar`) | **Phase 4 done** |
| WorldEditCUI selection outlines | **Phase 4 done** |
| Tile-entity NBT schematics (`.yschem` v3) + multi-clipboard | **Phase 4 done** |
| Clipboard web upload/download | **Phase 4 done** (optional; `editor.clipboard-web`) |
| Entity-aware clipboard + paste flags `-a -e -b -o -s` | **Phase 5 done** |
| `//fixlighting` + optional `limits.auto-relight` | **Phase 5 done** |
| `//generate` + expression `//deform` subset | **Phase 5 done** |
| Brushes: erode, raise, lower, melt, fill, forest | **Phase 5 done** |
| `.schem` export + `.schematic` / `.litematic` import | **Phase 5 done** |
| `//limit` session override + progress messages | **Phase 5 done** |
| WE shim clipboard + schematic format classes | **Phase 5 done** |
| Large paste path (preload, high parallelism, auto-fast, % progress, deferred relight) | **Phase 5.1 done** |
| NMS section placement / FAWE CFI | **Stretch** (Folia region-threading; next when still bottlenecked) |

## WorldEdit-class commands

| Area | Commands |
|------|----------|
| Selection | `//sel` `//wand` `//farwand` `//pos1` `//pos2` `//expand` `//contract` `//shift` `//outset` `//inset` `//chunk` `//size` `//desel` |
| Masks | `//mask` `//gmask` (`#air` `#solid` `#existing` `#region` materials `!negate`) |
| Edit | `//set` `//replace <mask> <pattern>` `//walls` `//faces` `//hollow` `//outline` `//overlay` `//smooth` `//naturalize` |
| Generate | `//cyl` `//sphere` `//pyramid` `//line` `//drain` `//regen` `//forest` `//flora` `//pumpkins` `//generate <expr> [pattern]` |
| Biome/deform | `//setbiome` `//biomeinfo` `//biomelist` `//deform` `//twist` `//center` `//curve` `//fixlighting` |
| Clipboard | `//copy [-m slot]` `//cut` `//paste [-a\|-e\|-b\|-o\|-s]` `//rotate` `//flip` `//stack` `//move` `//clipboard [slot]` |
| Schematics | `//schem list\|load\|save\|delete\|formats\|paste` (`.yschem` native; `.schem` import/export; `.schematic` / `.litematic` import) |
| Near/util | `//replacenear` `//removeabove` `//removebelow` `//extinguish` `//green` `//snow` `//thaw` |
| Nav | `//thru` `//jumpto` `//up` `//ascend` `//descend` |
| History | `//undo` `//redo` `//clearhistory` `//fast` `//limit [n]` |
| Brush | `//brush sphere\|cyl\|smooth\|gravity\|clipboard\|butcher\|erode\|raise\|lower\|melt\|fill\|forest <r> [pat]` `//size` `//mat` |
| Tools | `//superpickaxe` `//info` `//tree` `//none` |

Patterns: `stone`, `50%stone,50%dirt`, `oak_log[axis=y]`, `#solid`.

`//generate` / `//deform` expression vars: `x y z` (0..1), `rx ry rz` (absolute), `h`, `noise`, `rand`, `+ - * /`, comparisons, `&& ||`.

Paste flags: `-a` ignore air, `-e` entities, `-b` biomes, `-o` original origin, `-s` select pasted region (combinable, e.g. `-aes`).

## Soft API

Install **`yap-world.jar`** plus **`WorldEdit.jar`** (YaPWorld shim — not stock EngineHub/FAWE). Soft-deps that look for plugin `WorldEdit` / package `com.sk89q.worldedit` get a Folia-safe facade flushed through YaPWorld.

Supported shim packages (minimal):

- `com.sk89q.worldedit` — `WorldEdit`, `LocalSession` (selection + clipboard holder)
- `com.sk89q.worldedit.extent.EditSession` — setBlock / flush via YaPWorld
- `com.sk89q.worldedit.extent.clipboard` — `Clipboard`, `ClipboardHolder`
- `com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat` — format detection (real I/O via YaPWorld)
- Regions / math / BukkitAdapter — as in Phase 4

Bukkit services:

- `SelectionService` — pos1/pos2 cuboid (regions / pregen)
- `EditApplyService` — `fillPattern` / `replaceMask`

```java
WorldServices.editApply().ifPresent(edit ->
    edit.fillPattern(player, sel, "stone"));
```

WorldEditCUI clients receive outlines on channel `worldedit:cui` when `cui.enabled` is true.

Clipboard web (browser studio session): `GET/POST /api/world-edit/clipboard/{download|upload}?token=…` when `editor.clipboard-web` is true.

## World create

```text
/yapworld create flat_hub --type flat --env overworld
/yapworld create hell_arena --type normal --env nether --seed 42
/yapworld create custom --type amplified --generator SomePlugin --no-structures
```

| Flag | Values |
|------|--------|
| `--type` / `-t` | `normal` · `flat` · `large_biomes` · `amplified` |
| `--env` / `-e` | `overworld` · `nether` · `end` |
| `--seed` / `-s` | long integer (omit = random) |
| `--generator` / `-g` | plugin generator id (omit = vanilla) |
| `--no-structures` | skip villages/strongholds/etc. |

Dashboard **World → Create world** exposes the same pickers. Type/seed apply only on first generation; `load` still opens an existing folder.

## Permissions

| Node | Default | Use |
|------|---------|-----|
| `yapworld.selection` | op | Wand, GUI, `//` edits |
| `yapworld.brush` | op | Brush mode |
| `yapworld.schematic` | op | Save/paste schematics |
| `yapworld.cui` | op | WorldEditCUI outlines (also covered by `yapworld.selection`) |
| `yapworld.editor` | op | Browser studio |
| `yapworld.pregen` | op | Pregen from selection |
| `yapworld.load` / `create` / `unload` / `teleport` | op | World mgmt (create supports type/env/seed/generator) |
| `yapworld.admin` | op | Reload / status |

## Config

`plugins/YaPWorld/config.yml` — max volume, brush radius, undo depth, `limits.*`
(`max-changes`, `parallel-chunks`, `parallel-chunks-large`, `large-paste-blocks`,
`auto-fast-large`, `defer-relight-large`, `progress-messages`, `auto-relight`),
`cui.enabled`, browser editor port, `editor.clipboard-web`.

Large pastes (≥ `large-paste-blocks`, default 50k): higher chunk wave size, chunk preload,
auto skip-undo (unless you need history — set `auto-fast-large: false` or use small edits),
percent progress, deferred `//fixlighting` after paste when `defer-relight-large: true`.

## Related

[PERMISSIONS.md](../ops/PERMISSIONS.md) · [REGIONS.md](../gameplay/REGIONS.md) · [PREGEN.md](PREGEN.md) · [PLUGIN_COMPAT_MATRIX.md](PLUGIN_COMPAT_MATRIX.md)
