# Plugin API Guide

YaPcore supports **three** extension kinds: Paper/Spigot plugins, YaP plugins, and
**modules**. See [MODULES_AND_API.md](MODULES_AND_API.md) for modules + coverage.
Compatibility matrix: [PLUGIN_COMPAT.md](PLUGIN_COMPAT.md).

**One folder:** drop Paper/Spigot and YaP (`yap.yml`) jars into **`plugins/`**.
`paper-kernel/plugins` is a symlink to that folder. The Phase 3 bridge
`yap-spatial-tick.jar` is installed there automatically.

```bash
cp MyPlugin.jar plugins/
./scripts/check-plugin-layout.sh
./scripts/start.sh --fg
```

## 1. Legacy Spigot / Paper / Purpur (`plugin.yml`)

### On Paper (default — production path)

Put jars in **`plugins/`**. Within reason, anything that works on stock
Paper 26.2 works here. Folia-only plugins do **not**.

Optional smoke: `./scripts/smoke-paper-plugins.sh`

### YaP Compatibility Bridge (non-Paper authority only)

When Paper is not game authority, lightweight `plugin.yml` jars can load through
the Compatibility Bridge (soft-fail). Prefer Paper authority for production plugins.

| Bukkit call (facade) | YaPcore routing |
|----------------------|-----------------|
| `Bukkit.getScheduler().runTask(...)` | Compatibility Bridge → GameCore (SYNC) |
| `runTaskAsynchronously(...)` | Heavy I/O pool |
| inventory / block helpers | Bridged → SYNC where implemented |

## 2. Next-gen YaP plugins (`yap.yml`) — same `plugins/` folder

New plugins extend `com.yapcore.api.YaPPlugin` and declare work on the right pool:

```java
getScheduler().runHeavy(() -> {
    boolean ok = database.charge(player, price);
    if (ok) {
        getScheduler().runSync(() -> {
            player.getInventory().addItem(reward);
            getScheduler().runUi(() -> openSuccessMenu(player));
        });
    }
});
```

### `yap.yml`

```yaml
name: MegaStore
main: com.example.MegaStorePlugin
version: 1.0.0
api: yap-1
author: You
description: All-in-one store (GUI + economy + DB) without lag
```

## 3. Modules (`modules/` + `module.yml`)

Same pools as YaP plugins; intended for **optional fine-tuning** operators add
like mods. See [MODULES_AND_API.md](MODULES_AND_API.md).

## Crash reports

On faults, watchdog recoveries, or `crashdump` console command, YaPcore writes:

`logs/crashes/crash-<timestamp>-<kind>.log`

Including: full thread dumps, heap, JVM/OS, config, loaded plugins/modules, bridge queue stats, metrics, and recent console output.
