# How to use YaPDungeons

**Jar:** `yap-dungeons.jar` (GAMEPLAY)  
Instanced procedural dungeons: levels **1–50**, prestige **51–100**.

## Prerequisites

- GAMEPLAY install on the backend that hosts dungeons (usually survival)
- Enough RAM/CPU for instances
- Optional: YaPRegions templates around entrances

## Player workflow

```text
/dungeon                 # open / start flow
/dungeon invite Steve    # party
/dungeon accept
/dungeon deny
/dungeon leave
/dungeon status
```

1. Form a party (optional)  
2. Open the level picker (walk through a lit **crying obsidian** dungeon portal, `/dungeon`, or craftable portal right-click) and pick an unlocked level  
   - Frame: outer **4×5 crying obsidian**, empty **2×3** inside → Ender Eye on a frame block → **lime** swirling portal  
   - Regular obsidian is for YaP End doors, not dungeons 
3. Clear the instance  
4. Leave when done (`/dungeon leave`) so instances can clean up  

Dungeon portals (craftable block or crying-obsidian frame) inside a **claim** use the same gate as nether/End portals: owner + `/claim trust` only, unless the owner runs `/claim flag set nether-portal allow`. Wilderness portals stay public. `/dungeon` command is unaffected.

## Ops

```text
/yapdungeons
```

Use admin subcommands for force-end, reload, and diagnostics (see in-game help). Config: `plugins/YaPDungeons/`.

## Design tips

- Put entrance NPCs/portals in hub or spawn that `/server` to survival first  
- Prestige brackets are harder — communicate that in `/rules` or holograms  
- Watch MSPT during concurrent runs; limit party size in config if needed  

## Troubleshoot

| Symptom | Fix |
|---------|-----|
| Cannot start | Level gate / party state / world full |
| Stuck in instance | `/dungeon leave` or admin force via `/yapdungeons` |
| Lag spikes | Fewer concurrent instances; pregen worlds |

## Related

- [PLUGINS.md](../../plugins/PLUGINS.md) · [yapnpcs.md](yapnpcs.md) · [yapportals.md](yapportals.md)
