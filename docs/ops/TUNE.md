# Tuning YaPcore (central hub)

One place for day-to-day knobs: **`config/`**. Optional **fine-tune modules** in
`modules/` make each surface discoverable (`provides` / Modules GUI / `FINE_TUNE.txt`)
without forking engines — see [MODULES_AND_API.md](../plugins/MODULES_AND_API.md).

## Desktop Tune vs in-game admin

| Surface | When to use |
|---------|-------------|
| **Swing Tune GUI** (desktop) | Edit `config/` files, Paper templates, module jars — best for cold restarts and packaging |
| **Web dashboard** (`:8080`) | Remote ops: plugins, ranks, regions, map, Discord, console — no SSH |
| **In-game admin menu** (`/yapadmin`, `/staff`) | Live staff on the server: give items/kits, teleport, moderation shortcuts — see [ADMIN_MENU.md](ADMIN_MENU.md) |

Tune does **not** replace the admin menu; they target different operators and lifecycles.

## Layout

| Path | Role |
|------|------|
| `config/server.properties` | YaP product — ports, dual-stack, Phase 3 flags, packs, JVM |
| `config/paper/` | Symlink → `folia-kernel/config/` — `paper-global.yml`, `paper-world-defaults.yml` (**high-pop tuned**) |
| `config/spigot.yml` / `bukkit.yml` | Symlinks into paper-dir (**high-pop tuned**) |
| `config/templates/highpop/` | Canonical Paper/Spigot/Bukkit performance templates |
| `config/paper-server.properties` | Symlink → Paper’s `server.properties` (`view-distance=8`, `simulation-distance=6`) |
| `modules/*.jar` | Packaging modules — point at the knobs below (`gradle installFineTuneModules`) |
| GUI **Tune** / **Modules** tabs | Opens config paths / manages module jars |

### High-pop Paper configs (product)

YaPcore ships high-pop **starting** defaults on the live kernel. **Entity
activation ranges stay uncapped (`0`)** by default so MSPT vs Leaf/Paper is fair.
Tighter EAR lives in `config/templates/highpop-ear/` (opt-in). Packaging module:
`yap-highpop-module.jar` (`provides: [highpop]`).

| Knob | Where | Intent |
|------|--------|--------|
| EAR = 0 (always active) | `spigot.yml` | Fair vs Leaf — no activation free lunch |
| Optional tight EAR | `templates/highpop-ear/` | Production-only, not scoreboard default |
| Spawn limits / ticks-per | `bukkit.yml` | Fewer concurrent mobs |
| `ALTERNATE_CURRENT` redstone | `paper-world-defaults.yml` | Cheaper redstone |
| Hopper / armor-stand / despawn | same | Less TE + entity churn |
| View 8 / sim 6 | `paper-server.properties` | Less simulated chunks |

Farm-heavy worlds that opt into `highpop-ear` may still need higher villager EAR.
Fair MSPT benches also force uncapped EAR themselves.

Created automatically on start (`ConfigHub` / `yap_ensure_config_hub`).

```bash
./scripts/start.sh --fg
gradle installProductDefaults
gradle :gameplay-knobs-plugin:installIntoPlugins   # or installGameplayDefaults
```

## Paper vs gameplay encyclopedia

| Kind | Where |
|------|--------|
| Paper / Spigot / Bukkit | `config/paper/…`, `config/spigot.yml` |
| YaP chassis / network | `config/server.properties` (+ `yap-spatial-module`, `yap-ops-dashboard-module`, …) |
| **Mob / gameplay encyclopedia** | `plugins/yap-gameplay-knobs.jar` → `plugins/YaPGameplayKnobs/knobs.yml` (+ `yap-gameplay-knobs-module`) |

### What the encyclopedia supports (live)

YaP encyclopedia (`yap-gameplay-knobs.jar`) — **original YaP code**, Purpur-inspired surface, not a Purpur port.

| Feature | Status | How |
|---------|--------|-----|
| **WASD ridables** | Wired | `ridable` + `controllable`; perm `yapknobs.ride.<mob>` / `yapknobs.ride.*` |
| **Ridable specials** | Wired | e.g. creeper charge toggle — `yapknobs.special.<mob>` |
| **Attributes** | Wired | `max_health`, `scale`, `movement_speed`, `attack_damage`, `armor`, `follow_range`, `spawn_reinforcements`, … on spawn **and** chunk load; `/yapknobs reload` re-applies |
| **Deep AI** | Wired | `ai.disable-*`, `remove-goals` via Paper MobGoals; `retaliate: false` strips TARGET |
| **Breeding / water / XP / loot** | Wired | Event hooks |
| **Mob grief bypass** | Wired | `bypass-mob-griefing` + `projectiles-bypass-mob-griefing` |
| **Per-mob specials** | Wired | creeper fuse/radius/charged; phantom daylight/torch/grief; bee rain/night; enderman↔endermite; wolf rabid/milk; zombie reinforcements |
| **Blocks** | Wired | barrel rows, anvil cost, beehive max bees, crying-obsidian portal, lightning-rod range, bed explode |
| **gameplay.*** | Wired / Partial | blindness×, void fix, netherite fire resist, totem-in-void, crop slow via `BlockGrowEvent`; `tick-fluids` + fast crops need **E2 NMS** |
| **server-mod-name** | Wired | Best-effort brand / `yap.encyclopedia.server-mod-name` |
| **disable-give-dropping** | Wired | Suppress drops after `/give` (metadata window) |
| **Crop growth NMS / fluid tick** | E2-NMS | `gameplay.crop-growth-nms` + YaP-Folia `YapEncyclopediaHooks` (`vendor/folia/patches/0025-yap-encyclopedia-hooks.patch`) — rebuild `./scripts/build-yap-folia.sh`; soak-gate before enabling |

**Not full Purpur without NMS.** Event-wired encyclopedia features work on any YaP-Folia jar. Crop NMS acceleration and `tick-fluids: false` require patch **0025** (`/yapknobs status` → `nmsHooks: present=true`). Enabling those knobs without the patch logs a WARNING and does nothing at NMS level.

### Enable E2 NMS (ops recipe)

```bash
# 1. Rebuild YaP-Folia (applies 0025 in post phase via folia-patch.sh)
./scripts/build-yap-folia.sh

# 2. Point product at the new jar (folia-jar-source=build / lib/yap-folia-*.jar)
# 3. Keep knobs defaults: crop-growth-nms: false, tick-fluids: true
./scripts/yapctl soak-compat   # PASS before production enable

# 4. Only then (dev/test profile first):
#    gameplay.crop-growth-nms: true
#    # and/or tick-fluids: false
#    /yapknobs reload
#    /yapknobs status   # expect nmsHooks: present=true
```

```yaml
mobs:
  cow:
    ridable: true
    controllable: true
    ridable-in-water: false
    ridable-max-y: 320.0
    attributes:
      max_health: 10
      movement_speed: 0.2
    ai:
      aware: true
      disable-random-stroll: false
      disable-panic: true
      remove-goals: [PANIC]
  creeper:
    explosion-radius: 3.0
    max-fuse-ticks: 30
```

```bash
/yapknobs reload
/yapknobs status   # mobs, specialsWired, attrKeys, nmsHooks
```

Original YaP code — not a PurpurMC GPL port. Goal names match Paper `VanillaGoal` constants.
