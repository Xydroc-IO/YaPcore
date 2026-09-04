# YaP-Folia soak & knob profiles

Operator guide for YaP-Folia performance knobs and soak gates.
Patch inventory: [`vendor/folia/patches/AGENT3.md`](../../vendor/folia/patches/AGENT3.md) ·
[`AGENT4.md`](../../vendor/folia/patches/AGENT4.md).

## Product defaults (ship) — what we cite

| Knob | Default | Why |
|------|---------|-----|
| `folia-async-chunk-save` | **true** | Moonrise flush off region thread |
| `folia-hopper-tick-budget` | **64** | Soft-defer excess hopper transfers per region tick |
| `folia-scoreboard-swmr` | true | Correctness under Folia |
| `folia-teleport-transactions` | true | Cross-region TP integrity |
| `folia-entity-tick-budget` | **400** | MSPT-gated Mob AI cap (never players/TNT/vehicles/items/bosses) |
| `folia-budget-mspt-threshold` | **12** | Shared gate for entity budget + microtick |
| `folia-entity-tick-max-deferred` | **40** | Anti-starve: force-tick after N consecutive skips |
| `folia-microtick-budget-ms` | **8** | Soft Mob AI deadline on hot regions |
| `folia-subregion-partition` | **true** | Parallel shards when hot + geometry allows |
| `folia-subregion-mspt-clear` | **16** | Hysteresis vs engage threshold (20) |
| `folia-subregion-coalesce-min-wall-ms` | **30000** | Anti-thrash before coalesce |

**Official cites use this ship profile.** Stock Folia / Canvas ignore YaP `-D` knobs — that is the product delta. Result JSON records `knob_*` fields; `YAP_MSPT_REQUIRE_SHIP_KNOBS=1` fails the gate if micro/subregion/entity are missing or below ship floor.

Scheduler: `folia-kernel/config/paper-global.yml` → `threaded-regions.scheduler: WORK_STEALING`.

## Soak ladder

```bash
./scripts/yapctl soak-compat          # boot + API (~5–15 min) — partition/budget ON
./scripts/yapctl soak-perf 30         # heap/thread samples
./scripts/yapctl soak-long 12         # 12h default, 8h floor
./scripts/yapctl cite-fullcite        # stock Folia vs YaPcore ≥5% with **ship knobs**
./scripts/bench/cite-canvas-heavypop.sh 40  # Canvas ≥5% heavypop campaign
```

## Profile: ship cite (default)

```bash
# cite-fullcite.sh already exports ship knobs + NO_DIG + VD/sim 8
./scripts/yapctl cite-fullcite

# Heavypop + Canvas peer:
./scripts/bench/cite-canvas-heavypop.sh 40
# or:
YAP_BENCH_COMPETITORS=folia,canvas,yapcore YAP_BENCH_SHUFFLE=0 \
YAP_MSPT_REQUIRE_SHIP_KNOBS=1 \
./scripts/bench/run-vs-folia.sh heavypop 40
```

## Profile: baseline A/B (prove knobs help)

```bash
# Async+hopper only — no smart budget / microtick / partition
YAP_BENCH_CITE_BASELINE=1 \
YAP_FOLIA_ASYNC_CHUNK_SAVE=true YAP_FOLIA_HOPPER_TICK_BUDGET=64 \
./scripts/bench/run-vs-folia.sh heavypop 40
```

Compare baseline vs ship on the same machine/stamp pair.

## Profile: spawn-hot (aggressive — lab)

```bash
YAP_FOLIA_ENTITY_TICK_BUDGET=300 \
YAP_FOLIA_MICROTICK_BUDGET_MS=8 \
YAP_BENCH_FULL_STACK=1 \
./scripts/bench/run-vs-folia.sh spawncollapse 40
```

## Gaming blacklist

- Softening the 5% cite gate or MSPT&lt;2 noise rules
- Physics-off / keepalive-only bots as a “population” cite
- YaP-only lobe layouts vs stock contiguous without fair-paired disclosure
- Claiming chassis JVM time as game-tick MSPT
- Hiding ship knobs — always disclose `knob_*` in JSON / whitepaper
- Claiming “vanilla Folia knobs” when ship smart budget / partition are on
