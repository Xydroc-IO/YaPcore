# How to use YaPRegions

**Jar:** `yap-regions.jar`  
WorldGuard-class protected regions with flags, gamemode, worldborder, templates.

## Quick start — protect spawn

1. Select with WorldEdit/`//wand` style selection (YaPWorld tools)
2. Define and flag:

```text
/region define spawn
/region flag set spawn build deny
/region flag set spawn pvp deny
/region priority spawn 100
/region gamemode spawn adventure
```

## Commands

| Command | Purpose |
|---------|---------|
| `/region define <name>` | Cuboid from selection |
| `/region definepoly <name>` | Polygon |
| `/region polyadd` / `polyclear` | Edit polygon |
| `/region redefine <name>` | Reselect |
| `/region remove <name>` | Delete |
| `/region info <name>` | Flags / owners |
| `/region priority <name> <int>` | Higher wins |
| `/region flag set <name> <flag> allow\|deny` | Flags |
| `/region gamemode <name> <mode\|clear>` | Force GM |
| `/region worldborder <name>` | Border helper |
| `/region template save\|apply-template` | Reuse flag sets |
| `/region message set <name> greeting\|farewell <text>` | Enter/leave |
| `/region list` · `reload` | |

## Tips

- Staff COMMAND exempt so `/spawnmob` works in protected spawn when configured
- Use templates for dungeon entrances / arenas

## Related

[GAMEPLAY.md](../../gameplay/GAMEPLAY.md) · [yapworld.md](yapworld.md)
