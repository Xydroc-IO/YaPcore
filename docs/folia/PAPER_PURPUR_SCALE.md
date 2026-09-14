# Paper / Purpur — scale win (honest)

YaPcore does **not** claim single-thread MSPT victory over Paper/Purpur.
The product win is **regionized scale** under spread concurrent activity (plus encyclopedia + suite).

## Why Paper/Purpur are a different class

| | Paper / Purpur | YaPcore (YaP-Folia) |
|--|----------------|---------------------|
| Tick model | One main thread (`tick_model=single_thread`) | Region thread pool (`tick_model=regionized`) |
| Encyclopedia | Purpur `purpur.yml` (NMS fork) | YaP `knobs.yml` (plugin + optional Folia hooks) |
| Network / survival | Assemble plugins | First-party CORE+NETWORK + GAMEPLAY |
| Crossplay | Geyser/Via jars | First-party dual-stack |

## Fair benches (product claim)

Use **spread fullcite** (active bots on the 32-cell grid + redstone/hopper/TNT fixtures) —
the load where single-thread Paper is expected to collapse first:

```bash
./scripts/bench/fetch-competitors.sh
./scripts/bench/cite-paper-scale.sh
# or: YAP_BENCH_PLAYERS=150 YAP_BENCH_SECONDS=40 ./scripts/bench/cite-paper-scale.sh

# Lower-level wrapper (defaults: fullcite, paper,purpur,yapcore)
./scripts/bench/run-vs-paper-scale.sh fullcite 40
```

Methodology:

- Same host, same `YAP_BENCH_PLAYERS`, same view/sim distance, ship knobs on YaP only
- Paper/Purpur workdirs install **bench (+ popsim)** only — no Folia knobs/vehicles
- Result JSON includes `tick_model` (`single_thread` | `regionized`)
- Verdict via `scripts/bench/compare-paper-scale.py` — **scale win**, not Folia ≥5% MSPT peer gate

Scale-win heuristic (tune after stamps): Paper `tps_1m` &lt; 15 **or** `mspt_mean` &gt; 50, while YaP stays playable (`tps_1m` ≥ 18 and `mspt_mean` &lt; 50).

## Operator takeaway

- Need **Purpur-class mob/QoL config** without leaving Folia → YaP Encyclopedia ([TUNE.md](../ops/TUNE.md)).
- Need **citeable Folia-peer MSPT** at ~100 active → YaP-Folia ship knobs vs stock Folia/Canvas ([REAL_GAINS.md](REAL_GAINS.md)).
- Need **what single-thread Paper cannot hold** under spread pop → this doc + `cite-paper-scale.sh`.
- Need **max Paper plugin ecosystem** on one thread → Paper/Purpur (legacy benches only).

## Results

Latest stamp pointer: `bench/results/cite-paper-scale-latest-stamp.txt` + `cite-paper-scale-latest-verdict.txt`.

| Stamp | Players | Outcome |
|-------|---------|---------|
| `20260913T091729Z` | 100 | Paper **29.75 mspt / 20 TPS**; Purpur **29.92 mspt / 20 TPS** (`tick_model=single_thread`). **No scale win** — Paper still playable. YaPcore row **incomplete** (chassis `BenchRegionLoad.preparePopBench` hung before `highpop-ready.port`). |

Next: raise `YAP_BENCH_PLAYERS` (150+) once yapcore fullcite ready-marker is unblocked; that population becomes the public scale claim when single_thread collapses and YaP holds.

Known gap: `stop_bots` now also matches `node swarm.js` (was leaving Paper/Purpur swarms alive). Chassis pop-prep hang is separate — regionized fullcite fixtures never reached `startPopBench` on YaP workdir.

## Non-claims

- Do not market “beats Purpur MSPT on single-thread farms.”
- Do not reuse Folia peer ≥5% MSPT gates against Paper/Purpur.
- Do not imply Purpur source was ported — encyclopedia is original YaP code.
- Do not treat keepalive-only / hold-only population as a scale win.
