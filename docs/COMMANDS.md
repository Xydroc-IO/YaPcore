# Commands

## In-game (players)

Under **Paper game authority** (default), players use **real Paper / vanilla /
plugin commands** — `/give`, `/tp`, `/gamemode`, `/difficulty`, WorldEdit, LuckPerms,
etc.

**You must be OP** for vanilla commands to show in tab-complete and run. Non-ops only
see Bukkit help/plugins/version — that is normal Paper behavior.

### Grant OP (not automatic)

| Method | How |
|--------|-----|
| Seed list | `ops=YourName` in `config/server.properties` → written to `ops.json` at start |
| Console | `op YourName` in YaP Control Panel / stdin (forwards to Paper) |
| Opt-in auto | `auto-op=true` only if you *want* every joiner OP’d (default **false**) |

After OP, reconnect (or re-open chat) — you should see `/gamemode`, `/give`, etc.

## YaP Control Panel / stdin / web dashboard

YaP builtins first (`help`, `status`, `stop`, packs, …).  
**Anything else** is forwarded to Paper when Paper is running.

Same command path works from:

- Control Panel console
- Headless stdin (`./start.sh --fg`)
- **Web dashboard** console (`http://127.0.0.1:8080/` — [WEB_DASHBOARD.md](WEB_DASHBOARD.md))

```
give @p diamond 64
tp Steve 0 80 0
gamemode creative @a
whitelist add Steve
yapvehicle spawn lambo
yapvehicle shop
yappregen status
yapstacker gui
yapstacker give wand
luckperms user Steve permission set example.perm true
```

**Ranks / groups:** install LuckPerms, then apply the YaP pack:

```bash
./scripts/install-luckperms.sh
# running server:
ranks apply
lp user Steve parent set vip
```

See [PERMISSIONS.md](PERMISSIONS.md) · [`examples/luckperms/`](../examples/luckperms/) ·
dashboard **Ranks** tab.

Leading `/` is optional. Feedback from Paper appears in Paper logs
(`paper-kernel/logs/`) and often in the YaP console bus.

### First-party plugin commands (summary)

| Plugin | Command | Doc |
|--------|---------|-----|
| YaP Vehicles | `/yapvehicle …` | [VEHICLES.md](VEHICLES.md) |
| Pregen | `/yappregen …` | [PREGEN.md](PREGEN.md) |
| Stacker | `/yapstacker …` | [STACKER.md](STACKER.md) |
| Gameplay knobs | `/yapknobs …` | [TUNE.md](TUNE.md) |
| Plugin compat | `/yapcompat …` | [PLUGIN_BACKCOMPAT.md](PLUGIN_BACKCOMPAT.md) |
| PlaceholderAPI | `/papi …` | [PLACEHOLDERAPI.md](PLACEHOLDERAPI.md) |
| Player data | see plugin | [PLAYERDATA.md](PLAYERDATA.md) |

## Implementation

| Path | Role |
|------|------|
| `YaPcoreServer.executeCommand` | Builtins → else `PaperKernel.dispatchConsoleCommand` |
| `PaperCommandBridge` | Phase 3: `Bukkit.dispatchCommand(console, …)` on main thread |
| Phase 2 process | Writes the line to Paper’s stdin |

See [TUNE.md](TUNE.md) for config hub; [PLUGIN_COMPAT.md](PLUGIN_COMPAT.md) for plugins;
[PERMISSIONS.md](PERMISSIONS.md) for node → rank map.
