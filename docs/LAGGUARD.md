# YaPLagGuard

Per-chunk lag-machine governor for **YaP-Folia** (`folia-supported: true`). Cancels excess
entity spawns, primed TNT, hopper moves, and redstone updates when a chunk is hot.

Runs on the product game path (`game-authority=folia`, `folia-jar-source=build`). See [FOLIA_FORK.md](FOLIA_FORK.md).

## Install

```bash
gradle installProductDefaults   # → plugins/yap-lagguard.jar
# or included in assembleRelease
```

Smoke: `./scripts/smoke-lagguard.sh` (compile). `LIVE=1` also runs YaP-Folia smoke.

## Config (`plugins/YaPLagGuard/config.yml`)

| Knob | Default | Notes |
|------|---------|-------|
| `enabled` | `true` | Master switch |
| `max-entities-per-chunk` | `72` | Soft density (items + mobs + projectiles) |
| `max-primed-tnt-per-chunk` | `8` | Cannon / dupe protection |
| `max-hopper-transfers-per-window` | `48` / 20 ticks | Hopper clocks |
| `max-redstone-events-per-window` | `96` / 20 ticks | Rapid redstone |
| `stats-write-interval-ticks` | `100` | Writes `stats.json` for Prometheus/dashboard |
| `log-trips` | `true` | Rate-limited trip logs |

**Survival (public):** keep defaults or tighten TNT/entities slightly.  
**Creative / redstone lab:** raise hopper + redstone windows (e.g. 256 / 512).  
**Anarchy:** leave on — lag cannons trip TNT + entity budgets first.

Commands: `/yaplagguard status|reload` (`yaplagguard.admin`).

## Metrics

- File: `plugins/YaPLagGuard/stats.json`
- Chassis scrape: `yapcore_lagguard_*` on `GET /metrics`
- Dashboard: `/api/status` → `observability.lagguard`

See also [EDGE_HARDEN.md](EDGE_HARDEN.md) · [EDGE_RATE_LIMIT.md](EDGE_RATE_LIMIT.md).
