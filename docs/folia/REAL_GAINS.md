# Real MSPT gains (ship profile)

Prove YaP-Folia **ship knobs** beat stock Folia and rank ahead of Canvas — knobs
**disclosed** in result JSON (`knob_*`).

## Truth table (2026-09-04)

| Scenario | Stamp | Outcome |
|----------|-------|---------|
| **fullcite** ship (peak) | `20260904TshipFc2` | YaP **−12.40%** vs stock — **citeable**; knobs proven |
| **fullcite** re-verify | `20260904T040935Z` | YaP **−5.53%** vs stock — **citeable** ship gate PASS (knobs disclosed) |
| **fullcite** 3-way | `20260904TshipFc` | YaP #1 vs Folia & Canvas (Canvas ~35 mspt); pairwise −4.8% that run (noise) |
| **heavypop** ship | `20260904TshipOn` | YaP **−7.55%** vs stock; **#1 vs Canvas** (−3.85%) |
| **heavypop** baseline | `20260904TshipBase` | YaP **−7.19%** (async+hopper only; smart knobs off) |
| **heavypop** Canvas campaign | `20260904T065505Z` | YaP **−16.56%** vs stock; **−8.09% vs Canvas** — **citeable ≥5%** |

## Ship cite profile

| Knob | Value |
|------|-------|
| async-chunk-save | true |
| hopper-tick-budget | 64 |
| entity-tick-budget | 400 (MSPT-gated @ 12) |
| microtick-budget-ms | 8 |
| subregion-partition | true |

See also: [CANVAS_PARITY.md](CANVAS_PARITY.md) · [PAPER_PURPUR_SCALE.md](PAPER_PURPUR_SCALE.md) · [TUNE.md](../ops/TUNE.md) (encyclopedia).

## Why micro / subregion / parallel

1. **Smart entity budget + microtick** — soft-cap Mob AI when a region is hot (≥12 ms MSPT)
2. **Subregion partition** — parallel Folia shards when hot + geometry allows

Product features, disclosed on every cite. Baseline A/B (`YAP_BENCH_CITE_BASELINE=1`)
shows async+hopper alone (~−7% heavypop); ship profile adds headroom under hot fullcite.

## Stability

```bash
./scripts/yapctl soak-compat   # PASS 20260904T033554Z (ship knobs ON)
./scripts/yapctl soak-perf 30  # PASS 20260904T033626Z
./scripts/yapctl cite-fullcite # PASS 20260904T040935Z (−5.53%; peak cite −12.40% at shipFc2)
./scripts/yapctl soak-long 12  # background uptime; not a cite gate
./scripts/bench/cite-canvas-heavypop.sh 40  # Canvas ≥5% campaign (heavypop)
```

Encyclopedia E2 NMS (`0025-yap-encyclopedia-hooks.patch`): **defaults off**. Not part of the soft-launch claim.
Enable `gameplay.crop-growth-nms` / `tick-fluids=false` only after:

1. `./scripts/build-yap-folia.sh` (post patches include `002*.patch`)
2. `./scripts/yapctl soak-compat` PASS
3. `/yapknobs status` shows `nmsHooks: present=true`

Without the patch, enabling those knobs logs a WARNING (event-wired encyclopedia still works).

## Anti-gaming

- Disclose knobs; same load proofs; no keepalive-only population cites
- Do not claim “vanilla Folia settings” when ship smart knobs are on
