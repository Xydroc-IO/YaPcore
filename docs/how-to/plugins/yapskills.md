# How to use YaPSkills

**Jar:** `yap-skills.jar` (GAMEPLAY)  
Lightweight skill progression — not a full McMMO clone.

## Skills

Mining · Woodcutting · Strength · Marathon · Builder · Herbalism · Excavation · Alchemy · Health

XP comes from normal play (break ores, chop logs, etc.). **YaP420** can grant herbalism XP when both plugins are installed.

## Prerequisites

```bash
gradle installGameplayDefaults
# or drop yap-skills.jar into plugins/
```

Note: PlayerData **jobs** are forced off when YaPSkills is loaded.

## Player commands

```text
/skills          # overview GUI / list
/stats           # your stats
/skill           # current focus / info
/skill top       # leaderboard
```

Aliases: `/skill`, `/yskills` (admin reload).

## Ops

```text
/yskills reload
```

Tune XP rates, level caps, and messages under `plugins/YaPSkills/`.

## Workflow — enable on survival only

1. Install jar on survival (optional on lobby)  
2. Restart survival  
3. `/skills` as a test player after mining a bit  
4. If using YaP420, plant/harvest and confirm herbalism XP  

## Troubleshoot

| Symptom | Fix |
|---------|-----|
| No XP | Wrong world disabled in config; or plugin not on that backend |
| `/jobs` gone | Expected — skills replace jobs |
| Skills empty GUI | Reload; check enable log |

## Related

- [PLUGINS.md](../../plugins/PLUGINS.md) · [yap420.md](yap420.md) · [yapplayerdata.md](yapplayerdata.md)
