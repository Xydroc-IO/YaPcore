# Plugin API Guide

YaPcore supports **three** extension kinds: Folia-aware / Spigot-style plugins, YaP plugins, and
**modules**. See [MODULES_AND_API.md](MODULES_AND_API.md) for modules + coverage.
Compatibility matrix: [PLUGIN_COMPAT.md](PLUGIN_COMPAT.md).

**Product game:** **YaP-Folia** (`game-authority=folia`, `folia-jar-source=build`) — not stock Folia, not Paper. Build with `./scripts/build-yap-folia.sh`.

**One folder:** drop first-party Folia-native jars into **`plugins/`**.
`folia-kernel/plugins` (and legacy `paper-kernel/plugins`) symlink to that folder.

First-party plugins use [`YapSched`](../plugins/MODULES_AND_API.md)
(Folia `GlobalRegionScheduler` / entity / region affinity) and declare
`folia-supported: true`. Stock Paper jars are **unsupported** on the product path.

`yap-spatial-tick.jar` is **not** a product plugin — Paper Phase 3 legacy only
(benches). YaP-Folia refuses it (`folia-supported: false`).

```bash
cp MyPlugin.jar plugins/
./scripts/start.sh --fg
```

## 1. Legacy Spigot / Paper / Purpur (`plugin.yml`)

### On YaP-Folia (default — production path)

Put first-party jars in **`plugins/`**. They must declare `folia-supported: true`
and schedule via `com.yapcore.sched.YapSched` (or Folia region APIs directly).

### On Paper (legacy benches)

Put jars in **`plugins/`**. Within reason, anything that works on stock
Paper 26.2 works here when `game-authority=paper`. Folia-only plugins may not.

### YaP Compatibility Bridge (non-game authority only)

When Paper/YaP-Folia is not game authority, lightweight `plugin.yml` jars can load through
the Compatibility Bridge (soft-fail). Prefer Folia authority for production plugins.

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
version: 1.0.0.0
api: yap-1
author: You
description: All-in-one store (GUI + economy + DB) without lag
```

## 3. Modules (`modules/` + `module.yml`)

Same pools as YaP plugins; intended for **optional fine-tuning** operators add
like mods. See [MODULES_AND_API.md](MODULES_AND_API.md).

## 4. Shipped jars

**CORE + NETWORK (default)** on `gradle shadowJar` / `assembleRelease`:

`yap-placeholderapi`, `yap-pregen`, `yap-plugin-compat`, `yap-db`, `yap-perms`,
`yap-playerdata`, `yap-moderation`, `yap-essentials`, `yap-admin`, `yap-packs`, `yap-commands`, `yap-chat`, `yap-tab`,
`yap-discord`, `yap-protect`, `yap-world`, `yap-regions`, `yap-guard`, `yap-lagguard`,
`yap-map`, `yap-npcs`, `yap-factions`, `yap-floodgate`, `yap-bedrock-ui`, `yap-folia-bridge`.

**GAMEPLAY opt-in** (`gradle installGameplayDefaults` or `-PyapGameplay=true`):
`yap-skills` (thin mining/woodcutting/strength — [SKILLS.md](SKILLS.md)),
`yap-stacker`, `yap-disasters`, `yap-gameplay-knobs` (YaP Encyclopedia — [TUNE.md](../ops/TUNE.md)).
Factions ships in CORE+NETWORK.

SQL plugin authors: `compileOnly(project(":yap-db-api"))` and soft-depend `YaPDB`
([YAPDB.md](../data/YAPDB.md)). Ranks: [PERMISSIONS.md](../ops/PERMISSIONS.md).

## Crash reports

On faults, watchdog recoveries, or `crashdump` console command, YaPcore writes:

`logs/crashes/crash-<timestamp>-<kind>.log`

Including: full thread dumps, heap, JVM/OS, config, loaded plugins/modules, bridge queue stats, metrics, and recent console output.
