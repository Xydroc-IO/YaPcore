# YaPLagGuard

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
| `log-trips` | `true` | Rate-limited trip logs |
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

See also [EDGE_HARDEN.md](../network/EDGE_HARDEN.md) · [EDGE_RATE_LIMIT.md](../network/EDGE_RATE_LIMIT.md).
