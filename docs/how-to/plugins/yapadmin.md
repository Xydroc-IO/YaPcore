# How to use YaPAdmin

**Jar:** `yap-admin.jar`  
In-game staff super menu (`/yapadmin`) and soft/hard plugin manager (`/yapplugins`).

## Open the menu

```text
/yapadmin
/staff
/adminmenu
```

Permission: `yapadmin.menu`.

Hub sections typically include: players, give (presets/kits/materials), money, spawnmob, moderation, self tools, YaPItems, YaP420, deep-links to other tools.

## Plugin manager

```text
/yapplugins list
/yapplugins enable yap-420 soft
/yapplugins disable yap-disasters soft
/yapplugins disable some-jar hard --force
/yapplugins install path/to/plugin.jar
/yapplugins uninstall optional-id --force
```

| Mode | Effect |
|------|--------|
| **soft** | YAML `enabled: false` — can re-enable without deleting jar |
| **hard** | Renames/disables jar — **Folia restart required** (no hot-unload) |

Dashboard **Plugin manager** mirrors these ops.

## Canonical menu commands

Staff UIs should emit:

- `/yapadmin give|money|spawnmob <thing> [amount] [player]`
- `/yapadmin kick|warn|mute|tempban <player> [reason…]`
- `/yapadmin troll <type> <player>`

See [COMMANDS.md](../../ops/COMMANDS.md) staff contracts.

## Related

[WEB_DASHBOARD.md](../../ops/WEB_DASHBOARD.md) · [yap420.md](yap420.md) · [yapitems.md](yapitems.md)
