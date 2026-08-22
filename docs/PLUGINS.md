# Plugin API Guide

YaPcore supports **three** extension kinds: Paper/Spigot plugins, YaP plugins, and
**modules**. See [MODULES_AND_API.md](MODULES_AND_API.md) for modules + coverage.
Compatibility matrix: [PLUGIN_COMPAT.md](PLUGIN_COMPAT.md).

**One folder:** drop first-party Folia-native jars into **`plugins/`**.
`folia-kernel/plugins` (and legacy `paper-kernel/plugins`) symlink to that folder.

Product path is **`game-authority=folia`**. First-party plugins use `YapSched`
(Folia `GlobalRegionScheduler` / entity / region affinity) and declare
`folia-supported: true`. Stock Paper jars are **unsupported**.

`yap-spatial-tick.jar` is **not** a product plugin — Paper Phase 3 legacy only
(benches). Folia refuses it (`folia-supported: false`).

```bash
cp MyPlugin.jar plugins/
./scripts/check-plugin-layout.sh
./scripts/start.sh --fg
```

## 1. Legacy Spigot / Paper / Purpur (`plugin.yml`)

### On Folia (default — production path)

Put first-party jars in **`plugins/`**. They must declare `folia-supported: true`
and schedule via `com.yapcore.sched.YapSched` (or Folia region APIs directly).

Optional smoke: `./scripts/smoke-folia-plugins.sh`

### On Paper (legacy benches)

Put jars in **`plugins/`**. Within reason, anything that works on stock
Paper 26.2 works here when `game-authority=paper`. Folia-only plugins may not.

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
version: 1.0.0.0
api: yap-1
author: You
description: All-in-one store (GUI + economy + DB) without lag
```

## 3. Modules (`modules/` + `module.yml`)

Same pools as YaP plugins; intended for **optional fine-tuning** operators add
like mods. See [MODULES_AND_API.md](MODULES_AND_API.md).

## 4. Vehicles (Paper plugin + API)

Real vehicle mechanics for plugin authors (cars / bikes / custom chassis — **not**
minecarts or boats): [VEHICLES.md](VEHICLES.md). Soft-depend `YaPVehicles` and
load `VehicleAPI` from `ServicesManager`.

**Shipped by default (CORE + NETWORK)** on `gradle shadowJar` / `assembleRelease`:
`resourcepacks/yapcore-default.zip` (Faithful), **`plugins/yap-placeholderapi.jar`**
(clip-compatible PlaceholderAPI — [PLACEHOLDERAPI.md](PLACEHOLDERAPI.md)),
**`plugins/yap-pregen.jar`** ([PREGEN.md](PREGEN.md)),
**`plugins/yap-plugin-compat.jar`** ([PLUGIN_BACKCOMPAT.md](PLUGIN_BACKCOMPAT.md)),
**`plugins/yap-db.jar`** (shared MariaDB Hikari — [YAPDB.md](YAPDB.md) / [MARIADB.md](MARIADB.md)),
**`plugins/yap-playerdata.jar`** (cross-server data, offline `/login`, modular features — [PLAYERDATA.md](PLAYERDATA.md)),
**`plugins/yap-packs.jar`**, **`plugins/yap-chat.jar`**,
and **`plugins/yap-floodgate.jar`** ([VELOCITY.md](VELOCITY.md)).

**GAMEPLAY opt-in** (`gradle installGameplayDefaults` or `-PyapGameplay=true`):
`plugins/yap-vehicles.jar`, `modules/yap-vehicles-module.jar`,
**`plugins/yap-stacker.jar`** ([STACKER.md](STACKER.md)),
**`plugins/yap-gameplay-knobs.jar`**, vehicles overlay in the default pack.

SQL plugin authors: `compileOnly(project(":yap-db-api"))` and soft-depend `YaPDB`
([YAPDB.md](YAPDB.md)). Ranks: [PERMISSIONS.md](PERMISSIONS.md).

## Crash reports

On faults, watchdog recoveries, or `crashdump` console command, YaPcore writes:

`logs/crashes/crash-<timestamp>-<kind>.log`

Including: full thread dumps, heap, JVM/OS, config, loaded plugins/modules, bridge queue stats, metrics, and recent console output.
