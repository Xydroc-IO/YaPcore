# How to use YaPWorld

**Jar:** `yap-world.jar`  
Multi-world management + Folia-safe WorldEdit-class tools (`/yapworld`).

## Why not stock WorldEdit?

YaP-Folia needs region-aware scheduling. Prefer **`/yapworld`** for edits. The `WorldEdit.jar` shim keeps APIs happy for other plugins.

## Basics

```text
/yapworld
/yapworld help
```

Common patterns (names may vary slightly — check tab-complete):

- Selection + set / replace / walls  
- Copy / paste / undo  
- World create / load / unload / teleport  

Staff contract: `/yapworld <op> …` (same family as `//` where applicable).

## Workflow — flat arena world

1. `/yapworld` create a void/flat world  
2. TP staff in and build with `/yapworld` tools  
3. Protect with [yapregions.md](yapregions.md)  
4. Link from hub via [yapportals.md](yapportals.md) or NPC `setserver`  

## Related

- [YAPWORLD.md](../../plugins/YAPWORLD.md) · [worldedit.md](worldedit.md) · [yapregions.md](yapregions.md)
