# YaP-Folia soak & knob profiles

Operator guide for YaP-Folia performance knobs and soak gates.
Patch inventory: [YAP_FOLIA_PATCHES.md](YAP_FOLIA_PATCHES.md) · cites: [REAL_GAINS.md](REAL_GAINS.md).

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
| `folia-microtick-budget-ms` | **8** | Soft Mob AI deadline on hot regions (AI time-slice; not a finer clock) |
| `folia-aligned-microticks` | **true** | Real micro/sub-tick phases + soft cross-region waves (0026–0030); universal RTQ tagging |
| `folia-micro-phases` | **4** | Phase count when aligned microticks on (2–4) |
| `folia-tick-wave-max-wait-ms` | **2** | Soft barrier max wait ms |
| `folia-physics-substeps` | **true** | Internal travel/move N-step physics (0031); plugin tick stays 20 TPS |
| `folia-physics-substep-count` | **4** | Substep count 2–8 |
| `folia-subregion-partition` | **true** | Parallel shards when hot + geometry allows |
| `folia-subregion-mspt-clear` | **16** | Hysteresis vs engage threshold (20) |
| `folia-subregion-coalesce-min-wall-ms` | **30000** | Anti-thrash before coalesce |

**Official cites use this ship profile.** Stock Folia / Canvas ignore YaP `-D` knobs — that is the product delta. Result JSON records `knob_*` fields; `YAP_MSPT_REQUIRE_SHIP_KNOBS=1` fails the gate if micro/subregion/entity are missing or below ship floor.

Scheduler: `folia-kernel/config/paper-global.yml` → `threaded-regions.scheduler: WORK_STEALING`.

## Aligned micro/sub-ticks gate

Ship default **on** (`folia-aligned-microticks=true`) after smoke PASS with universal RTQ tagging (`0030`). Soft-wave timeout logs are expected under load (not a TickThread failure).

```bash
./scripts/build-yap-folia.sh
YAP_FOLIA_ALIGNED_MICROTICKS=true ./scripts/smoke-folia.sh
./scripts/yapctl soak-compat
./scripts/yapctl cite-fullcite   # disclose knob_aligned_microticks in JSON
```

Rollback: `folia-aligned-microticks=false`.

## Physics sub-steps gate

Ship default **on** (`folia-physics-substeps=true`, patch `0031`). Feel/combat stability, not MSPT capacity.

```bash
./scripts/build-yap-folia.sh
# product defaults already forward -Dyap.folia.physics-substeps=*
./scripts/yapctl soak-compat
```

Rollback: `folia-physics-substeps=false`.

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
