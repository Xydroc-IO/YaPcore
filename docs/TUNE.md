# Tuning YaPcore (central hub)

One place for day-to-day knobs: **`config/`**. Optional **fine-tune modules** in
`modules/` make each surface discoverable (`provides` / Modules GUI / `FINE_TUNE.txt`)
without forking engines — see [MODULES_AND_API.md](MODULES_AND_API.md).

## Layout

| Path | Role |
|------|------|
| `config/server.properties` | YaP product — ports, dual-stack, Phase 3 flags, packs, JVM |
| `config/paper/` | Symlink → `paper-kernel/config/` — `paper-global.yml`, `paper-world-defaults.yml` (**high-pop tuned**) |
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

| Feature | How |
|---------|-----|
| **WASD ridables** | `ridable: true` + `controllable: true` — passenger `Input` steers mount every tick |
| **Ridable in water / max Y** | `ridable-in-water`, `ridable-max-y` |
| **Deep AI** | `ai.disable-ai`, `disable-random-stroll`, `disable-panic`, `remove-goals: [VANILLA_GOAL_NAME]` via Paper MobGoals |
| **Attributes** | `attributes.max_health`, `scale` on spawn |
| **Breeding delay / water dmg / always-drop-exp / pick-up-loot** | Event hooks |
| **Blocks** | barrel rows, anvil cost, beehive max bees, crying-obsidian portal frame, lightning-rod range, bed explode |

```yaml
mobs:
  cow:
    ridable: true
    controllable: true
    ridable-in-water: false
    ridable-max-y: 320.0
    ai:
      aware: true
      disable-random-stroll: false
      disable-panic: true
      remove-goals: [PANIC]
```

```bash
/yapknobs reload
```

Original MIT code — not a PurpurMC GPL port. Goal names match Paper `VanillaGoal` constants.
