# Plugin API Guide

YaPcore supports **three** extension kinds: Folia-aware / Spigot-style plugins, YaP plugins, and
**modules**. See [PLUGINS.md](PLUGINS.md) for modules + coverage.
Compatibility matrix: [PLUGIN_COMPAT.md](PLUGIN_COMPAT.md).

**Product game:** **YaP-Folia** (`game-authority=folia`, `folia-jar-source=build`) — not stock Folia, not Paper. Build with `./scripts/build-yap-folia.sh`.

**One folder:** drop first-party Folia-native jars into **`plugins/`**.
`folia-kernel/plugins` (and legacy `paper-kernel/plugins`) symlink to that folder.

First-party plugins use [`YapSched`](../plugins/PLUGINS.md)
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
| `Bukkit.getScheduler().runTask(...)` | Compatibility Bridge → chassis / Folia SYNC |
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
version: 0.0.0.1
api: yap-1
author: You
description: All-in-one store (GUI + economy + DB) without lag
```

## 3. Modules (`modules/` + `module.yml`)

Same pools as YaP plugins; intended for **optional fine-tuning** operators add
like mods. See [PLUGINS.md](PLUGINS.md).

## 4. Shipped jars

**CORE + NETWORK (default)** on `gradle shadowJar` / `assembleRelease`:

`yap-placeholderapi`, `yap-pregen`, `yap-plugin-compat`, `yap-db`, `yap-perms`,
`yap-playerdata`, `yap-moderation`, `yap-essentials`, `yap-admin`, `yap-packs`, `yap-commands`, `yap-chat`, `yap-tab`,
`yap-discord`, `yap-protect`, `yap-world`, `yap-regions`, `yap-portals`, `yap-guard`, `yap-lagguard`,
`yap-map`, `yap-npcs`, `yap-factions`, `yap-floodgate`, `yap-bedrock-ui`, `yap-tailor`,
`yap-bedrock-blocks`, `yap-folia-bridge`, `yap-items` ([YAPITEMS.md](YAPITEMS.md)),
`yap-qol` (timber axe + area excavator — [PLUGINS.md](PLUGINS.md)).

**GAMEPLAY opt-in** (`gradle installGameplayDefaults` or `-PyapGameplay=true`):
`yap-skills` (thin mining/woodcutting/strength/marathon/builder/herbalism/excavation/alchemy/health — [PLUGINS.md](PLUGINS.md)),
`yap-dungeons` (procedural instances L1–50 + prestige 51–100 — [PLUGINS.md](PLUGINS.md)),
`yap-stacker`, `yap-disasters`, `yap-leveled-mobs` (distance-based mob levels),
`yap-gameplay-knobs` (YaP Encyclopedia — [TUNE.md](../ops/TUNE.md)).
Factions ships in CORE+NETWORK.

SQL plugin authors: `compileOnly(project(":yap-db-api"))` and soft-depend `YaPDB`
([YAPDB.md](../data/YAPDB.md)). Ranks: [PERMISSIONS.md](../ops/PERMISSIONS.md).

## Crash reports

On faults, watchdog recoveries, or `crashdump` console command, YaPcore writes:

`logs/crashes/crash-<timestamp>-<kind>.log`

Including: full thread dumps, heap, JVM/OS, config, loaded plugins/modules, bridge queue stats, metrics, and recent console output.


---

## Modules & API

YaPcore is **not** bundling MineMod. It exposes APIs so *you* can drop in
plugins and fine-tune **modules** the same way.

## Three loadable kinds

| Kind | Folder | Manifest | Base class |
|------|--------|----------|------------|
| Legacy Paper/Spigot | `plugins/` | `plugin.yml` | `org.bukkit.plugin.java.JavaPlugin` |
| YaP plugin | `plugins/` | `yap.yml` | `com.yapcore.api.YaPPlugin` |
| **Module** (fine-tune) | `modules/` | `module.yml` | `com.yapcore.api.module.YaPModule` |

Paper and YaP plugin jars share **`plugins/`** (see [PLUGIN_COMPAT.md](PLUGIN_COMPAT.md)).
Modules stay in `modules/`.

**Owner fine-tune path:** drop first-party packaging modules into `modules/` so every
product surface is discoverable (`provides` / `requires`, Modules GUI, `FINE_TUNE.txt`).
Engines and YAML stay in `plugins/` and `config/` — modules do not reimplement tick/
economy logic.

```bash
gradle installProductDefaults      # CORE plugins + CORE fine-tune modules
gradle installFineTuneModules      # all packaging modules → modules/
gradle assemblePluginDist          # …/modules/core + …/modules/gameplay
```

See `modules/README.md` for the full jar table.

Central configs: [TUNE.md](../ops/TUNE.md). The **YaP Encyclopedia** (Purpur-inspired, original YaP
code — not a Purpur port) is the Paper plugin `yap-gameplay-knobs`
(`plugins/YaPGameplayKnobs/knobs.yml`) plus packaging module `provides: [gameplay-knobs]`
(GAMEPLAY tier). Optional Folia NMS crop/fluid hooks: patch `0025-yap-encyclopedia-hooks`.

**Stacker:** Paper plugin `yap-stacker` + optional module `provides: [stacker]`.
See [PLUGINS.md](PLUGINS.md) (`/yapstacker`).

**Skills:** Paper plugin `yap-skills` — thin mining / woodcutting / strength / marathon / builder / herbalism / excavation / alchemy / health. See [PLUGINS.md](PLUGINS.md).

**Shared messages (P0/P1 polish):** [PLUGINS.md](PLUGINS.md) — `yap-messages-api` for Adventure text, permission nodes, reload/DB UX, and light help.

**Dungeons:** Paper plugin `yap-dungeons` — procedural instances L1–50 + prestige 51–100; soft API `yap-dungeons-api`. See [PLUGINS.md](PLUGINS.md).

GUI tabs: **Plugins**, **Modules**, and **Tune**. Headless: [WEB_DASHBOARD.md](../ops/WEB_DASHBOARD.md).

### Example `module.yml`

```yaml
name: SpawnTweaks
main: com.example.SpawnTweaksModule
version: 0.0.0.1
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

**Paper / Folia plugins (`plugin.yml`):** **Folia-native first-party** under product
`game-authority=folia` (`folia-supported` + [`YapSched`](../plugins/PLUGINS.md)). Stock
Paper jars are unsupported. Legacy `game-authority=paper` still exposes complete
Paper API for benches — see [PLUGIN_COMPAT.md](PLUGIN_COMPAT.md).

Runtime matrix: `com.yapcore.api.ApiCoverage`.

**YaP plugins / modules:** Adventure, dual-pool schedulers, modules `provides`/`requires`.

**Folia `RegionScheduler` APIs:** supported on the Folia product path via Folia + YapSched.

Runtime matrix: `com.yapcore.api.ApiCoverage` (see source).

> **Retired:** Paperclip / Phase 3 vendor scripts were removed. Use `./scripts/fetch-folia.sh` and `./scripts/build-yap-folia.sh` on the Folia product path.

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
- [PLUGINS.md](PLUGINS.md) — thin skills plugin
- [PLUGINS.md](PLUGINS.md) — instanced procedural dungeons
- [PLUGINS.md](PLUGINS.md) — mob/item stacker
- `yap-first-party/modules/finetune-modules/` — first-party packaging modules source


---

## Messages

Shared Adventure messaging for first-party plugins. Part of the **P0/P1 polish** program
(one product voice, permission errors with nodes, reload/DB UX, light help).

## Module

| Artifact | Path |
|----------|------|
| `yap-messages-api.jar` | `yap-first-party/api/yap-messages-api/` |

Plugins should **shade/embed** this API in their jar (Folia isolated classloaders), same as `yap-sched` / other small APIs.

## API

```java
import com.yapcore.messages.YapText;
import com.yapcore.messages.YapMessageBundle;
import com.yapcore.messages.YapMessages;
import com.yapcore.messages.YapHelp;
import com.yapcore.messages.YapConfigReload;

YapText.component("&aHello &f{player}", "player", name);

YapMessageBundle msg = YapMessageBundle.fromSection(config.getConfigurationSection("messages"));
msg.noPermission(sender, "yapchat.admin");
msg.playersOnly(sender);
msg.reloaded(sender, "YaPChat");

YapMessages.profileLoading(player);      // sync still applying
YapMessages.databaseNotReady(sender);    // pool down / misconfigured
YapMessages.commandFailed(sender, ex);   // maps pool errors cleanly

YapHelp.simple(sender, "YaPPerms", "/yapperm …");

YapConfigReload.Result r = YapConfigReload.run(() -> { plugin.reloadConfig(); config.reload(); });
YapConfigReload.report(sender, plugin.getLogger(), "YaPPlayerData", r);
```

### Standard keys

| Key | Default |
|-----|---------|
| `prefix` | _(empty)_ |
| `no-permission` | `&cNo permission.&7 Need: &f{node}` |
| `players-only` | `&cPlayers only.` |
| `reloaded` | `&a{plugin} reloaded.` |
| `failed` | `&c{reason}` |

### P1 ops helpers

| Helper | When |
|--------|------|
| `profileLoading` | Player profile sync not ready |
| `databaseNotReady` | YaPDB / SQL pool unavailable |
| `commandFailed` | Prefer DB-not-ready over raw SQLException text |
| `YapConfigReload` | Catch reload parse failures → console + sender |
| `YapHelp` | Shared header/usage voice |

Dashboard YAML editor rejects invalid numbers with **HTTP 400** (`PluginConfigIo.coerce`) instead of silently keeping the old value.

## Migration status

| Plugin | Status |
|--------|--------|
| YaPChat | **Done** — `YapMessageBundle` + `messages.*` (reference) |
| CORE+NETWORK + gameplay cmds | **Done** — `YapMessages` denials / reload |
| YaPPlayerData | **Done** — `SyncService.requireReady` + DB failure mapping |
| Catalog reloads | **Done** — admin/pregen/floodgate/dungeons wired; bedrock-ui/folia-bridge/compat intentional blank |
| PlaceholderAPI shim | Own `Msg` helper (upstream-style) |

## Operator tip

After updating YaPChat, add the new `messages.*` keys (or delete `plugins/YaPChat/config.yml` and re-seed) so `no-permission` includes `{node}`.


---

## PlaceholderAPI

YaPcore ships a **clean-room, clip-compatible** PlaceholderAPI so plugins that
soft/hard-depend on HelpChat’s PlaceholderAPI work **without** installing
`PlaceholderAPI.jar` from SpigotMC / Modrinth.

| Item | Value |
|------|--------|
| Product jar | `plugins/yap-placeholderapi.jar` |
| Plugin name (for `getPlugin` / depends) | **`PlaceholderAPI`** |
| Package | `me.clip.placeholderapi.*` (binary-compatible surface) |
| Built-in expansions | `player`, `server` (full upstream-style placeholder sets) |
| Extra expansions | Drop jars into `plugins/PlaceholderAPI/expansions/` |
| Admin | `/papi` full local command tree |

## Intentional product scope

YaP PlaceholderAPI is a **first-party, curated local-expansions** engine — not a
partial stub and not a HelpChat eCloud mirror.

| In scope | Out of scope (by design) |
|----------|---------------------------|
| Parse API, built-ins `player` / `server` | HelpChat eCloud browse / download / update |
| Jar-drop external expansions | Bundling third-party expansion jars in release zips |
| First-party `%yap*_*%` from other YaP plugins | GPL eCloud coupling |
| `/papi parse\|list\|reload\|register\|dump\|…` | |

Operators curate expansions on disk. That is the supported delivery path.

## Product rule

Do **not** also install HelpChat / clip PlaceholderAPI. Two jars both named
`PlaceholderAPI` will conflict. YaP’s jar is the supported path.

```bash
gradle :placeholderapi-plugin:installIntoPlugins
# or: gradle shadowJar / assembleRelease
```

## Local expansions workflow

1. Create (auto on first enable) or open `plugins/PlaceholderAPI/expansions/`
2. Copy a compatible expansion `.jar` into that folder
3. Run `/papi reload` or `/papi register <jar>`
4. Confirm with `/papi list` / `/papi info <id>`

```text
plugins/PlaceholderAPI/expansions/
```

`/papi ecloud` explains this workflow (eCloud download UX is intentionally absent).

## Commands

| Command | Purpose |
|---------|---------|
| `/papi help` | Command list |
| `/papi parse <me\|--null\|player> <text…>` | Parse placeholders |
| `/papi bcparse <target> <text…>` | Broadcast parsed text |
| `/papi cmdparse <target> <text…>` | Dispatch parsed text as a command |
| `/papi parserel <p1> <p2> <text…>` | Relational placeholders |
| `/papi dump` | Local dump file + optional paste.helpch.at upload |
| `/papi list` / `info [id]` / `reload` | Expansion admin |
| `/papi register <jar>` / `unregister <id>` | Expansion folder admin |
| `/papi version` | Engine version |
| `/papi ecloud` | Points at local expansions folder workflow (no eCloud) |

## What works for other plugins

- `softdepend: [PlaceholderAPI]` / `depend: [PlaceholderAPI]`
- `Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null`
- `PlaceholderAPI.setPlaceholders(player, text)` (+ bracket / relational)
- `new MyExpansion().register()` extending `PlaceholderExpansion`
- Expansion register / unregister events
- `PlaceholderAPIPlugin.getAdventure()` (BukkitAudiences)
- `Msg`, `booleanTrue/False`, `getDateFormat()`, `getServerVersion()`
- Configurable / Cacheable / Cleanable / Taskable / Relational / VersionSpecific

## Built-in placeholders

**Player** — name, displayname, uuid, health*, food, level/exp*, gamemode, world*,
coords, yaw/pitch, biome*, direction*, ping/colored_ping, armor_*, item_in_hand*,
permissions (`has_permission_*`), potion effects, locale*, flight/sneak/sprint/dead
flags, bed/compass, first/last join, and more.

**Server** — name, online / `online_<world>`, max_players, unique_joins, version/build,
tps / colored tps, uptime, ram_*, whitelist, total_* entity/chunk counts, `time_*`
patterns, countdown/countup helpers.

## First-party expansions

Registered **in-process** by other YaP jars when installed (no jar drop needed):

| Plugin | Identifier | Notes |
|--------|------------|--------|
| yap-perms | `yapperms` | Permission / group placeholders |
| skills | `yapskills` | Skills / levels — [PLUGINS.md](PLUGINS.md) |
| dungeons | `yapdungeon` | Dungeon progress / run — [PLUGINS.md](PLUGINS.md) |
| factions | `yapfactions` | Faction data |
| stacker | `yapstacker` | See [PLUGINS.md](PLUGINS.md) |

## Ops extras

- **bStats** charts (YaP service id, not HelpChat’s)
- **Update checker** — `check-updates` + optional `update-check-url`
- **Adventure** — `getAdventure()` for expansions that send components

## Honesty bar

Clean-room compatibility engine — not a GPL port. Expansion delivery is
**intentionally local and operator-curated**; eCloud download UX is out of scope.
Everything else above is in-tree.

See also [PLUGIN_COMPAT.md](PLUGIN_COMPAT.md) · [PLUGINS.md](PLUGINS.md).


---

## LagGuard

Per-chunk lag-machine governor for **YaP-Folia** (`folia-supported: true`). Cancels excess
entity spawns, primed TNT, hopper moves, redstone / observer updates, and piston cycles when
a chunk is hot. Optional minecart density cap.

Runs on the product game path (`game-authority=folia`, `folia-jar-source=build`). Build with `./scripts/build-yap-folia.sh`.

## Install

```bash
gradle installProductDefaults   # → plugins/yap-lagguard.jar
# or included in assembleRelease
./scripts/start.sh --fg
```

## Config (`plugins/YaPLagGuard/config.yml`)

| Knob | Default | Notes |
|------|---------|-------|
| `enabled` | `true` | Master switch |
| `max-entities-per-chunk` | `72` | Soft density (items + mobs + projectiles) |
| `max-primed-tnt-per-chunk` | `8` | Cannon / dupe protection |
| `entity-caps.items` | `40` | Per-chunk ground-item cap (`0` = off) |
| `entity-caps.mobs` | `48` | Per-chunk living (non-player) cap (`0` = off) |
| `entity-caps.projectiles` | `24` | Per-chunk projectile cap (`0` = off) |
| `max-minecarts-per-chunk` | `16` | Minecart spawn cap (`0` = off) |
| `exempt-regions` | `[]` | Soft-depend YaPRegions: skip budgets inside named admin regions |
| `max-hopper-transfers-per-window` | `48` / 20 ticks | Hopper clocks |
| `max-redstone-events-per-window` | `96` / 20 ticks | Rapid redstone (non-observer) |
| `max-piston-events-per-window` | `64` / 20 ticks | Piston extend/retract (`0` = off) |
| `max-observer-events-per-window` | `64` / 20 ticks | Observer updates, separate from general redstone (`0` = off) |
| `escalation.enabled` | `false` | **Dangerous** — see below |
| `escalation.trips-threshold` | `50` | Trips in window before one cull |
| `escalation.window-ticks` | `200` | Escalation window |
| `escalation.max-items-removed` | `32` | Cap on items removed per cull |
| `stats-write-interval-ticks` | `100` | Writes `stats.json` for Prometheus/dashboard |
| `log-trips` | `false` | Rate-limited trip logs (off by default — budgets still enforce) |
| `world-multipliers` | `{}` | Per-world budget multipliers (e.g. `creative: 2.0`) |
| `alert.trips-per-minute` | `0` | Log (+ optional webhook) when trips/min exceed threshold; `0` disables |
| `alert.webhook-url` | `""` | Discord-compatible webhook URL |

### Entity-type caps

On `EntitySpawnEvent`, LagGuard categorizes the entity:

| Category | Matches | Config key |
|----------|---------|------------|
| items | ground `Item` entities | `entity-caps.items` |
| mobs | `LivingEntity` except players | `entity-caps.mobs` |
| projectiles | `Projectile` | `entity-caps.projectiles` |
| minecarts | `Minecart` | `max-minecarts-per-chunk` |

Category caps are checked **before** the global `max-entities-per-chunk` ceiling. Set a key to
`0` to disable that category. World multipliers apply to enabled category caps the same way as
other per-chunk budgets. TNT still uses `max-primed-tnt-per-chunk` only.

**Per-type (creeper = N) caps** live on the YaP Encyclopedia (`mobs.<type>.max-per-chunk` in
`knobs.yml`) — Purpur-style gameplay surface — not LagGuard. Both can apply; encyclopedia
cancels that type, LagGuard still enforces category/global ceilings. See [TUNE.md](../ops/TUNE.md).

### Piston / observer / minecart

- **Pistons:** `BlockPistonExtendEvent` / `BlockPistonRetractEvent` cancelled when the chunk
  exceeds `max-piston-events-per-window` in the piston tick window.
- **Observers:** counted on `BlockRedstoneEvent` when the block is an observer, using the
  observer window (not the general redstone budget).
- **Minecarts:** spawn cancelled when the chunk already has ≥ `max-minecarts-per-chunk`.

### Region exemptions (soft-depend YaPRegions)

```yaml
exempt-regions:
  - spawn
  - hub
```

When YaPRegions is present, spawn/hopper/redstone/piston/observer/minecart budgets are skipped
inside matching admin regions (`RegionServices.find()` → `at(location)`). If YaPRegions is
absent, the list is ignored.

### Escalation cull (off by default — dangerous)

```yaml
escalation:
  enabled: false
  trips-threshold: 50
  window-ticks: 200
  max-items-removed: 32
```

When **enabled**, after a chunk accumulates ≥ `trips-threshold` budget trips inside
`window-ticks`, LagGuard may **remove excess ground `Item` entities** once per window
(down toward `entity-caps.items`, capped by `max-items-removed`).

**This destroys player drops and farm output.** Keep `enabled: false` unless operators
explicitly accept that risk (e.g. anarchy lag cleanup). Prefer raising caps or fixing the
machine over turning escalation on.

**Survival (public):** keep defaults or tighten TNT/entities slightly.  
**Creative / redstone lab:** raise hopper + redstone + piston windows (e.g. 256 / 512) or set `world-multipliers.creative: 2.0`.  
**Anarchy:** leave on — lag cannons trip TNT + entity budgets first; escalation stays off unless you want aggressive item culls.

Commands: `/yaplagguard status|reload|top [n]` (`yaplagguard.admin`).

API: `LagGuardService.topChunks(n)` → `(world, cx, cz, trips)`.

## Metrics

- File: `plugins/YaPLagGuard/stats.json` (includes `hotChunks`; written on an interval; last snapshot survives process death, but **in-memory trip counters reset on reload/restart**)
- Chassis scrape: `yapcore_lagguard_*` on `GET /metrics`
- Dashboard: `GET/POST /api/lagguard` (settings + reload + `hotChunks`) · `/api/status` → `observability.lagguard`

See also [NETWORKING.md](../network/NETWORKING.md) · [NETWORKING.md](../network/NETWORKING.md).


---

## Pregen

First-party **Chunky-class** world pregen for YaPcore.
Shipped as `plugins/yap-pregen.jar` (default product install).

**Folia-supported.** Chunk loads run on the owning region via `YapSched`, hold plugin
chunk tickets while inflight, and use per-region inflight caps. Global MSPT throttling
applies on Paper only (Folia MSPT is region-local).

## Commands

`/yappregen` (aliases: `pregen`, `yapchunky`) — permission `yappregen.admin` (op):

```
/yappregen start <world> radius <chunks> [x z]
/yappregen start <world> circle <blockRadius> [x z]
/yappregen start <world> corners <x1> <z1> <x2> <z2>
/yappregen start <world> polygon <x1> <z1> <x2> <z2> <x3> <z3> ...
/yappregen start <world> worldborder
/yappregen start <world> selection          # WorldEdit //sel (soft-depend)
/yappregen pause [world|all]
/yappregen resume [world|all]
/yappregen cancel [world|all]
/yappregen status [world|all]
/yappregen reload
```

One job **per world** (parallel across worlds). Shared global inflight budget plus
per-region caps on Folia.

## Config (`plugins/YaPPregen/config.yml`)

```yaml
chunks-per-tick: 5
max-mspt: 40.0                 # Paper only
broadcast-interval-sec: 30
auto-resume: true
max-worlds: 4
max-inflight: 32
max-inflight-per-region: 8    # Folia region buckets
```

Progress: `plugins/YaPPregen/progress/<world>.yml` (resume after restart when `auto-resume: true`).

## Web dashboard

Tab **Pregen** at `http://127.0.0.1:8080/` — or:

- `GET /api/pregen` — status text / job map (`regionized`, `activeRegions`)
- `POST /api/pregen` — `{ "action":"start", "world":"world", "shape":"radius", "radius":"8" }`

## WorldEdit

Prefer **YaPWorld** on Folia: select with `/yapworld tool`, then:

```bash
/yappregen start <world> selection
# aliases: sel | we | yapworld
```

Falls back to stock WorldEdit `//sel` when YaPWorld is not installed (Paper benches only).
On the Folia product path prefer YaPWorld (`//sel`, wand) — stock WE/FAWE jars are not supported.

## Example

```bash
/yappregen start world radius 32
/yappregen status
/yappregen pause world
/yappregen resume world
```


---

## YaP-QoL

First-party quality-of-life tools for YaP-Folia: **timber axe** and **area excavator** (3×3 / 6×6 / 9×9).

| | |
|--|--|
| Jar | `yap-qol.jar` |
| Config | `plugins/YaP-QoL/config.yml` |
| Commands | `/yapqol` (reload / give / staff GUI) |
| Kit | VIP kit includes timber + excavator (`yapqol give excavator:3`) |
| Staff | Admin menu → QoL; Fabric **yap-staff** Links screen |

## Defaults

Fleet seed + release boxes ship `yap-qol.jar` alongside `yap-items.jar` when present in the catalog. Claims / Protect / Skills extras respect break events (nested `BlockBreakEvent` for Folia-safe multi-break).

## Rebuild

```bash
gradle :qol-plugin:installIntoPlugins
# or full box:
gradle publishReleasesFolder -PyapGameplay=true
```

See [PLUGINS.md](PLUGINS.md) · [WEB_DASHBOARD.md](../ops/WEB_DASHBOARD.md).


---

## Skills

Optional gameplay plugin (`yap-skills.jar`). Thin progression — **mining**, **woodcutting**, **strength**, **marathon**, **builder**, **herbalism**, **excavation**, **alchemy**, **health**, plus a real **overall player level**.

## Progression

| Track | Cap (default) | How XP works |
|-------|---------------|--------------|
| Per-skill | **120** | Breaks (mine/chop/dig/harvest) + melee → strength + travel → marathon + placing → builder + brewing → alchemy + damage taken → health; RS curve |
| **Overall** | **120** | Stored in `yap_player_overall`. Each skill XP grant also adds `amount × overall.xp-share` (default **0.5**) to overall. Same RS curve, separate table. Continues after a skill is maxed. |
| Total level | n/a | Sum of skill levels (display / PlaceholderAPI only) |

Overall is **not** derived from skill averages — it has its own XP row and levels up independently when skill actions feed it.

Config (`plugins/YaPSkills/config.yml`):

```yaml
xp-table:
  max-level: 120
overall:
  max-level: 120
  xp-share: 0.5
  maxed-xp-share: 0.75
  multiplier: 1.0
power:
  break-speed-bonus-at-max: 2.0   # 3x mine/chop speed at 120
  extra-drops-at-max: 2.0         # +2 copies (3x loot) at 120
  damage-bonus-at-max: 2.0        # 3x melee damage at 120
  movement-speed-bonus-at-max: 1.0  # 2x walk speed (Marathon) at 120
  place-reach-bonus-at-max: 1.0     # +1 place/break reach (Builder) at 120
  keep-block-chance-at-max: 0.25    # 25% keep the placed block (Builder) at 120
  extra-hearts-at-max: 10.0         # +5 hearts (Health) at 120
  brew-speed-bonus-at-max: 1.0      # 2x brewing (Alchemy) at 120
```

Builder does **not** speed placing (YaPGuard scaffold / Grim). Extra reach is additive on YaPEssentials `block-reach` (~6.5 survival → ~7.5 at 120). Keep-block refunds a blank extra of the same block type (not buckets, shulkers, or heads).

Max-level abilities (12s window, 90s cooldown; sneak + right-click **air** with the tool):

| Skill | Unlock at 120 |
|-------|----------------|
| Mining | **Super Breaker** — insta-mine ores/stone with a pickaxe |
| Woodcutting | **Tree Feller** — chops connected logs (cap 32, no leaves; VIP timber axe still better) |
| Herbalism | **Green Terra** — harvest fully grown crops and replant (needs a seed) |
| Excavation | Rare dig loot: clay ~8%, glowstone dust ~1.5%, diamond ~1/2500 |
| Alchemy | Brew speed scales 1x→2x (hopper arrays without a nearby player stay vanilla) |
| Health | Extra max health as you level; **combat regen** at 120 |

## Requirements

- **YaPDB** (soft-depend; shared MariaDB/Postgres/SQLite pool)
- Enable in `plugins/YaPSkills/config.yml`: `enabled: true`

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/skills` `[player]` `/stats` | `yapskills.use` | Skills menu (overall + per-skill). `/skill` with no args opens the same menu |
| `/skill top` `<skill\|overall>` `[page]` | `yapskills.use` | Leaderboard |
| `/skill set` / `addxp` … | `yapskills.admin` | Staff level/XP |
| `/yskills reload` | `yapskills.admin` | Reload config + skill packs |

## Placeholders (PlaceholderAPI)

- `%yapskill_overall_level%` / `%yapskill_level%` — overall level (1–120)
- `%yapskill_overall_xp%` — stored overall XP
- `%yapskill_total_level%` — sum of skill levels
- `%yapskill_combined_xp%` — sum of skill XP
- `%yapskill_<skill>_level%` / `%yapskill_<skill>_xp%` — per skill

## Dashboard

**Gameplay → Skills** — `GET/POST /api/skills` (jar presence, enable flag, online sample).

Install via `gradle installGameplayDefaults` or a full release box (`-PyapGameplay=true`).


---

## Dungeons

Optional gameplay plugin (`yap-dungeons.jar`). Procedural **instanced dungeons** with party invites, skill gates, shared lives, and ephemeral world cleanup.

Layouts use **non-overlapping** rooms, framed **doorways**, themed **room templates** (entrance / combat / treasure / trap / boss), corridors with lighting, and a bedrock foundation pad so the dungeon sits as a readable complex rather than floating boxes.

## Progression

| Band | Levels | Notes |
|------|--------|-------|
| Locked | — | YaPSkills **overall &lt; 10** → no entry |
| Core | **1–50** | Clear L unlocks L+1; can replay any cleared level |
| Prestige | **51–100** | Requires clear of dungeon **50**; separate prestige clear ladder |

Per-level **mining / strength** floors ship in `plugins/YaPDungeons/dungeons/gates.yml` (all 100 levels).

## Requirements

- **YaPDB** (soft; shared MariaDB/Postgres/SQLite pool)
- **YaPSkills** (soft; required for gates — overall + mining/strength)
- **YaPWorld** (soft; preferred for `createWorld` / `deleteWorld`; Bukkit fallback exists)
- Enable in `plugins/YaPDungeons/config.yml`: `enabled: true`

## Player loop

1. **Craftable portal:** craft a Dungeon Portal item, place it, right-click.
2. **Buildable portal (base):** build a standing **4×5** obsidian frame (like a nether portal). When complete you get a chat tip — right-click any frame block with an **Ender Eye** to activate. Right-click the lit portal to open the menu.
3. Or `/dungeon open` from anywhere.
4. Pick an unlocked level → instance generates into an ephemeral `yd_*` world.
5. Invite with `/dungeon invite <player>`; they `/dungeon accept <prefix>`.
6. Kill the **Dungeon Boss** to clear → loot + unlock next → world deleted after grace.
7. Shared **party lives** (base 3 + 1 per extra member, cap 6). Inventory kept on death.

### Buildable frame shape (default)

Outer **4 wide × 5 tall** obsidian (inner opening **2×3**), facing north/south or east/west — same proportions as a nether portal. Configurable under `portal.structure` in `config.yml`.

**Materials**

| Portal type | Materials |
|-------------|-----------|
| Buildable frame | Obsidian (frame) + 1 Ender Eye to activate |
| Craftable item | 4 Obsidian + 2 Deepslate + 3 Ender Eyes (3×3 recipe) |

Staff: `/yapdungeons giveportal [player]`.

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/dungeon` `[open]` | `yapdungeons.use` | Level picker GUI |
| `/dungeon invite\|accept\|deny\|leave\|status` | `yapdungeons.use` | Party + run status |
| `/yapdungeons reload` | `yapdungeons.admin` | Reload config + packs |
| `/yapdungeons forcestop <runId>` | `yapdungeons.admin` | Fail + cleanup run |
| `/yapdungeons giveportal [player]` | `yapdungeons.admin` | Give portal item |

## Config packs

Under `plugins/YaPDungeons/dungeons/`:

| File | Contents |
|------|----------|
| `gates.yml` | overall / mining / strength per level 1–100 |
| `difficulty.yml` | rooms, mobs, HP/damage mults, boss HP, traps |
| `themes.yml` | material + mob palettes by band |
| `loot.yml` | guaranteed / rare drops + economy per level |

## Placeholders

- `%yapdungeon_highest%` / `%yapdungeon_prestige%`
- `%yapdungeon_completions%`
- `%yapdungeon_in_run%` / `%yapdungeon_level%` / `%yapdungeon_lives%`
- `%yapdungeon_active_runs%`

## API

```java
DungeonServices.find().ifPresent(dungeons ->
    dungeons.startRun(player, 1));
```

Jar: `yap-dungeons-api.jar` (`com.yapcore.dungeons.DungeonService`).

## Install

```bash
gradle installGameplayDefaults
# or full box:
gradle assembleRelease -PyapGameplay=true
```

Set `enabled: true` in `plugins/YaPDungeons/config.yml` after first boot.


---

## Stacker

First-party **VortexStacker-class** mob / item / spawner stacker for YaPcore /
**YaP-Folia 26.2** (`folia-supported: true`).
Shipped as `plugins/yap-stacker.jar` (**GAMEPLAY** opt-in:
`gradle installGameplayDefaults` or `assembleRelease -PyapGameplay=true`).

**Design:** stack sizes live in Bukkit **PersistentDataContainer** (PDC) on entities,
item meta, and spawner tile states — **no NMS**, so it stays stable across Folia updates.

**Not a YaP `modules/` jar.** Runtime is a normal Folia-aware plugin (`plugin.yml`). A thin
`provides: [stacker]` packaging module is optional later; v1 does not need one.

## Commands

`/yapstacker` (alias `stacker`) — permission `yapstacker.admin` (op):

```
/yapstacker status
/yapstacker reload
/yapstacker gui
/yapstacker stats
/yapstacker give <wand|tool|aura> [player]
```

| Permission | Default | Role |
|------------|---------|------|
| `yapstacker.admin` | op | Full admin |
| `yapstacker.gui` | op | Open admin chest GUI |
| `yapstacker.give` | op | Give tools |
| `yapstacker.wand` | op | Spawner wand |
| `yapstacker.tool` | op | Mob stack tool |
| `yapstacker.aura` | op | Kill-aura item |

## Features

| Area | Behavior |
|------|----------|
| **Mobs** | Same-type merge on spawn; nametag `{type} x{size}`; sheep color / slime size / age gates |
| **Kill modes** | `DECREMENT` (default) — kill one, respawn stack−1 · `INSTANT` — multiply loot/XP |
| **Per-mob rules** | `mob-rules` — max stack, loot/XP multipliers, slime preserve, sheep color |
| **Items** | Ground-item merge; PDC for counts above vanilla max; full pickup |
| **Spawners** | Place-merge, break-one (sneak = whole stack), wand GUI / absorb nearby |
| **Tools** | Wand (spawners), tool (force-merge mobs), kill aura (held item pulses) |
| **Remerge** | Chunk `EntitiesLoadEvent` + periodic wander merge |
| **Hooks** | Soft-skip Citizens NPCs + MythicMobs (`softdepend`) |
| **PlaceholderAPI** | `%yapstacker_*%` when `yap-placeholderapi` is present |
| **Metrics** | In-process counters via `/yapstacker stats` and admin GUI |

## Config

`plugins/YaPStacker/config.yml` (created on first enable). Defaults:

- Mobs: merge radius `5`, max stack `100`, kill mode `DECREMENT`
- Items / spawners: enabled with their own radii and caps
- Blacklist: dragon, wither, warden, elder guardian
- Skip named / tamed / leashed by default

Rebuild / install:

```bash
gradle :stacker-plugin:installIntoPlugins
# or full product defaults
gradle shadowJar
```

## Placeholders (`%yapstacker_<id>%`)

| Id | Value |
|----|--------|
| `enabled` | Global enable |
| `kill_mode` | `DECREMENT` / `INSTANT` |
| `mob_merges` / `merges` | Merge counter |
| `mob_kills` / `kills` | Stacked death handling counter |
| `item_merges` | Item merges |
| `spawner_stacks` / `spawners` | Spawner stack ops |
| `aura_kills` | Kill-aura pulses |
| `max_stack` | Global mob max |
| `merge_radius` | Mob merge radius |

## Manual test checklist

1. Spawn eggs → zombies merge → nametag `Zombie xN`
2. Kill once → stack decreases by 1, 1× loot (`DECREMENT`)
3. Drop many diamonds → one ground stack with count nametag when over 64
4. Place spawners of same type nearby → absorb; wand right-click opens GUI
5. `/yapstacker give aura` → hold near stacked mobs → units die one-by-one

## Related

- [PLUGINS.md](PLUGINS.md) — plugin folder layout
- [PLUGINS.md](PLUGINS.md) — built-in PAPI
- [PLUGINS.md](PLUGINS.md) — plugin vs module
- `yap-first-party/gameplay/stacker-plugin/` — source


---

## Map

Flat Leaflet map + optional BlueMap-class **3D voxel mesh** viewer. Served from the dashboard (`/map/`) or YaPcore pack HTTP when `http.use-yapcore-server: true`.

| Item | Value |
|------|--------|
| Product jar | `plugins/yap-map.jar` |
| Config | `plugins/YaPMap/config.yml` |
| Tiles | `plugins/YaPMap/map/tiles/{world}/{layer}/{zoom}/{x}_{z}.png` |
| Meshes | `plugins/YaPMap/map/meshes/{world}/{layer}/lod{N}/{chunkX}_{chunkZ}.json` + `.ymesh` + `manifest.json` |
| POI | `plugins/YaPMap/poi.json` (example copied from jar on first enable) |
| Markers | `GET /map/markers.json` (players; optional NPC/region/claim/POI) |
| Telemetry | `plugins/YaPMap/render-status.json` (dashboard map snapshot) |

## Scope

- **Flat:** world + layer (surface / cave) + Leaflet tiles + live markers
- **3D:** greedy-merged boxes (format **v2**, milliblock units) per chunk, streamed by camera distance; layers `full` / `surface` / `cave`; stairs/slab/carpet/fence **models**; LOD0–2 pyramid; binary `.ymesh`; Three.js InstancedMesh + orbit controls
- Configurable render origin: `render-origin.mode: spawn|fixed`
- Sample window of `sample-chunk-radius²` chunks (tiles); mesh may use `mesh.extra-radius` / `mesh.follow-players`
- Dirty-chunk incremental re-render on block break/place (tiles + meshes)
- Tile **and mesh** retention via config + `/yapmap prune`
- Telemetry: `lastRenderTime`, `tileDiskBytes`, `meshDiskBytes`, `meshChunkCount`, `renderStatus`, `dirtyChunkCount`

Historical time-slider / render generations are **not** shipped (prefer prune + layers).

## Open the map

| View | URL |
|------|------|
| Flat (default) | `http://127.0.0.1:<port>/map/` or `?view=flat` |
| 3D | `http://127.0.0.1:<port>/map/?view=3d` |
| 3D cave | `http://127.0.0.1:<port>/map/?view=3d&layer=cave` |

`<port>` is the **dashboard** port when browsing via admin UI, or **resource-pack-http-port** (default 8081) when `http.use-yapcore-server: true`, or `http.port` (default 8082) for the embedded map server.

Use the **View** dropdown (Flat / 3D) and **Layer** select in the map chrome. After enable, wait for the first render (~2s) or run `/yapmap render`. 3D needs mesh assets under `/meshes/{world}/{layer}/manifest.json`.

| Asset | URL |
|-------|-----|
| UI | `/map/` |
| Tiles | `/tiles/{world}/{layer}/{zoom}/{x}_{z}.png` |
| Mesh chunk (JSON) | `/meshes/{world}/{layer}/lod{N}/{chunkX}_{chunkZ}.json` |
| Mesh chunk (binary) | `/meshes/{world}/{layer}/lod{N}/{chunkX}_{chunkZ}.ymesh` |
| Mesh manifest | `/meshes/{world}/{layer}/manifest.json` |
| Markers | `/map/markers.json` |

## Mesh config

```yaml
mesh:
  enabled: true
  # max-y: 320   # optional; omit/0 → use max-height (nether still ≤126)
  layers:
    - full
    - surface
    - cave
  default-layer: full
  models: true          # stairs / slabs / carpets / fences as multi-box shapes
  binary: true          # write .ymesh alongside JSON
  max-lod: 2            # 0–2 server LOD pyramid
  follow-players: false # re-center / expand toward players near window edge
  extra-radius: 0       # extra chunks beyond sample-chunk-radius for mesh
```

## Mesh format (v2)

Compact chunk JSON **v2** (milliblock boxes):

`{"v":2,"u":1000,"cx":N,"cz":N,"n":N,"d":[lx,y,lz,sx,sy,sz,rgb,…]}`

- Positions and sizes are **milliblocks** (`u=1000` → 1.0 block). Viewer divides by `u`.
- `rgb` 0xRRGGBB; multiple boxes per block for stairs/fences
- Hidden faces culled by merging adjacent same-color solids (full cubes only; models stay separate)

**Binary `.ymesh`:** little-endian `YMSH` + version/flags + cx/cz + boxCount + unit + packed `i32×7` per box. Prefer binary when present; JSON kept for tooling/compat.

**v1 compatibility:** older files `{"v":1,"d":[lx,y,lz,rgb,…]}` (unit cubes, flat path `meshes/{world}/…`) are still readable. After upgrading, run `/yapmap render` so chunks are rewritten as **v2 under `{world}/{layer}/lod{N}/`**.

### Greedy meshing + block models (Phase 4)

Extraction builds dense RGB + model-kind/state buffers, applies the mesh layer filter, then `GreedyMesher`:

1. Special materials (stairs, slabs, carpets, fences/walls) emit simplified multi-box models from `BlockModelRegistry`
2. Remaining solids greedy-merge into axis-aligned milliblock boxes

Done bar: stairs/slabs/fences look distinct from full cubes in the 3D viewer.

### LOD pyramid (Phase 5)

| LOD | Path | Behavior |
|-----|------|----------|
| 0 | `…/lod0/` | Full greedy + models |
| 1 | `…/lod1/` | Drop thin features (carpets/fence bars), merge adjacent same-color boxes |
| 2 | `…/lod2/` | Coarser quantize + larger thin-feature threshold |

`map-3d.js` picks LOD by camera–chunk distance (near → LOD0, mid → LOD1, far → LOD2).

### Binary transport (Phase 6)

Server writes JSON + `.ymesh` when `mesh.binary: true`. HTTP serves both (`application/octet-stream` for `.ymesh`). Viewer tries `.ymesh` first, falls back to JSON. Typical savings: tens of percent smaller than JSON v2 for the same boxes.

### Beyond sample window (Phase 7)

| Setting | Effect |
|---------|--------|
| `mesh.follow-players: true` | When average player chunk drifts ≥ ~½ window from center, re-center mesh origin and enqueue the new window |
| `mesh.extra-radius: N` | Mesh sample radius = `sample-chunk-radius + N`; also pads dirty renders toward players near the edge |

New chunks run on a **background queue** (async drain, small budget per turn) so Folia region threads are not blocked by a full re-mesh burst—only the usual per-chunk `regionChunk` read.

#### Ops cost

| Knob | Cost note |
|------|-----------|
| `sample-chunk-radius` | Flat tiles + base mesh window; cost ∝ radius² × Y span |
| `mesh.extra-radius` / `follow-players` | Extra mesh disk + CPU; can grow beyond the tile window |
| `mesh.max-lod` | ≈ +(LOD count)× write amplification (LOD1/2 are cheaper than LOD0) |
| `mesh.binary` | ~2× files on disk vs JSON-only; bandwidth drops when clients use `.ymesh` |
| `mesh.models` | Slightly more boxes in builds with stairs/fences; still far below unit-cube |

For a small VPS prefer `sample-chunk-radius: 4–6`, `max-lod: 1`, `follow-players: false`. Dedicated hosts can raise radius and enable follow with `extra-radius: 2–4`.

### Chunk streaming (viewer)

`map-3d.js` loads the layer manifest, then **lazily fetches** chunk `.ymesh`/JSON by camera-target distance / frustum (capped concurrent downloads), switching LOD as you orbit. Each chunk is its own `InstancedMesh`. Marker polling updates player spheres only — it does **not** rebuild terrain.

## Layers

### Flat tiles

```yaml
layers:
  surface: true
  cave: true
  cave-max-y: 48
  biome-tint: true
```

| Layer | Behavior |
|-------|----------|
| `surface` | Highest solid ≤ `max-height` (nether uses roof-aware cap ≤126) |
| `cave` | Underground slice ≤ `cave-max-y`, preferring solids with air above |

UI: `?layer=surface` / `?layer=cave`.

### 3D mesh layers

| Layer | Behavior |
|-------|----------|
| `full` | All solids in the mesh Y range (nether roof-aware) |
| `surface` | Highest solid per column (same idea as flat surface) |
| `cave` | Open cave solids ≤ `min(surfaceY−1, cave-max-y)` — same preference for air-above as flat cave |

3D UI Layer select uses `mesh.layers` / `mesh.default-layer` (`?view=3d&layer=…`).

## Retention / prune

```yaml
retention:
  max-age-days: 14   # 0 = off
  max-disk-mb: 512   # 0 = off
```

`/yapmap prune [days]` deletes aged **PNG tiles** and **mesh chunk JSON/ymesh** (skips `manifest.json`), and/or oldest files until each store is under the disk budget. Pass days to override `max-age-days` for that run; disk budget still comes from config. Affected mesh world/layer manifests are rebuilt after prune.

## Markers config

```yaml
render-origin:
  mode: spawn   # or fixed
  chunk-x: 0
  chunk-z: 0
markers:
  players: true
  npcs: false           # needs yap-npcs
  regions: false        # needs yap-regions (RegionService.listRegions)
  pois: true            # plugins/YaPMap/poi.json
  claims: false         # soft-depend YaPPlayerData ClaimService
  faction-colors: false # optional tint via YaPFactions claim overlays
  poll-seconds: 5
```

### Static POI (`poi.json`)

```json
[
  {"name": "Spawn", "world": "world", "x": 0, "y": 64, "z": 0, "icon": "star"}
]
```

Both viewers poll `/map/markers.json`. Flat mode maps coords relative to the render origin; 3D places player spheres at world X/Y/Z without touching chunk meshes.

## Commands

`/yapmap reload|render|prune`

## Related

- [WEB_DASHBOARD.md](../ops/WEB_DASHBOARD.md) — Map tab
- [PLUGIN_COMPAT.md](../plugins/PLUGIN_COMPAT.md) — Dynmap / BlueMap → yap-map
