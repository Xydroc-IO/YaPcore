# Modules & Plugin API coverage

YaPcore is **not** bundling MineMod. It exposes APIs so *you* can drop in
plugins and fine-tune **modules** the same way.

## Three loadable kinds

| Kind | Folder | Manifest | Base class |
|------|--------|----------|------------|
| Legacy Paper/Spigot | `plugins/` | `plugin.yml` | `org.bukkit.plugin.java.JavaPlugin` |
| YaP plugin | `plugins/` | `yap.yml` | `com.yapcore.api.YaPPlugin` |
| **Module** (fine-tune) | `modules/` | `module.yml` | `com.yapcore.api.module.YaPModule` |

Paper and YaP plugin jars share **`plugins/`** (see [PLUGIN_COMPAT.md](PLUGIN_COMPAT.md)).
Modules stay in `modules/`. GUI tabs: **Plugins** and **Modules**. Config: `modules-dir=modules`.

### Example `module.yml`

```yaml
name: SpawnTweaks
main: com.example.SpawnTweaksModule
version: 1.0.0
api: yap-module-1
author: You
description: Optional spawn radius / MOTD tweaks
provides: [spawn-tweaks]
requires: []
```

Modules can declare `provides` / `requires` so operators compose only what they need.

## Threading contract (multithreaded server)

| Work | Pool | How |
|------|------|-----|
| Inventory, blocks, teleport, world | **SYNC** | `Bukkit.getScheduler().runTask` or `getScheduler().runSync` |
| DB / HTTP / files / proxy sync | **HEAVY** | `runTaskAsynchronously` or `runHeavy` |
| Menu polish / animations | **UI** | `getScheduler().runUi` (YaP plugins/modules) |

`ThreadPools` tags the current thread. Bridge drain runs as SYNC. World/inv
mutations auto-queue to the Compatibility Bridge when called off-SYNC.

**Race rule for plugin authors:** never mutate world/inventory from HEAVY/UI
without hopping to SYNC. Keep caches labeled by owning pool.

## API coverage (what authors can use)

See `com.yapcore.api.ApiCoverage` at runtime. Highlights:

- Adventure (`Component`, `Audience`) via Kyori
- Inventory GUIs: `InventoryHolder`, click / drag / close events, `ItemMeta`
- `OfflinePlayer`, permissions attachments, `Sound`, `World` / `Block`
- Plugin messaging (`Messenger`) for Velocity-style channels
- Chat: `AsyncPlayerChatEvent` + Paper `AsyncChatEvent`
- Expanded `Material` catalog

Still growing: full Paper event surface, Brigadier, deep NMS/schematic APIs
(track with world streaming). Missing Paper classes are added against real
plugin import lists — not by vendoring a specific plugin.

## Author checklist

1. Put world/inv changes in `runTask` / `runSync`.
2. Put SQL/HTTP in async / `runHeavy`.
3. Prefer Adventure for text; legacy `§` strings still work.
4. Use modules for optional features operators can toggle by adding/removing jars.
5. Test on YaPcore; keep a Paper jar only if you also ship a Paper backend.

## Related

- [PLUGINS.md](PLUGINS.md) — YaPPlugin dual-pool examples
- `examples/yap-allinone` — sample YaP plugin
- `examples/yap-module-demo` — sample module
