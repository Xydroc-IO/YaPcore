# MSPT scoreboard — YaPcore vs stock Paper

**Gate rule:** Do not claim “faster than Paper” without a fresh row in
[`bench/results/`](../bench/results/) produced by
[`scripts/bench/run-vs-paper.sh`](../scripts/bench/run-vs-paper.sh).

## What we measure

| Scenario | Load |
|----------|------|
| `idle` | Empty-ish world — regression guard |
| `entity` | 120 primed TNT × 4 interior quads (always-tick; EAR-proof) |
| `farm` | Wheat on farmland in 4 interior quads |

Same seed (`yap-bench-1` on stock workdir; YaPcore uses existing `paper-kernel` world
unless reset). Sample window defaults to 30s after 15s warmup. Metrics:
`Server.getAverageTickTime()` (MSPT) and `Server.getTPS()[0]`.

## How to run

```bash
# Java 25+, lib/paper-26.2.jar (stock) + lib/paper-26.2-yap.jar (YaP)
./scripts/bench/run-vs-paper.sh entity 30
./scripts/bench/run-vs-paper.sh farm 30
./scripts/bench/run-vs-paper.sh idle 20

python3 scripts/bench/compare-results.py \
  bench/results/<stamp>-entity-stock.json \
  bench/results/<stamp>-entity-yapcore.json
```

Exit code of `compare-results.py`: `0` = YaPcore win or tie (≤2%), `1` = loss.

## Win condition (Beat Paper)

On **entity** and **farm**, YaPcore Phase 3.5 (YaP Paperclip + leased interior
entity / block / fluid / random tick) shows **lower `mspt_mean`** than stock
Paper 26.2 on the same machine.

Idle must not regress materially (tie allowed).

## Phase mapping

| Phase | Role |
|-------|------|
| 3 | Interior entity tick on cores 3–6 |
| **3.5** | Scoreboard + interior block/fluid/random under same leases |
| 4 | Dual-stack + YaP plugins polish (after scoreboard exists) |

## Results table

Fresh run 2026-08-21 (~23:00 local) — Java 26, fair `bench/workdir-*`, same seed:

| Scenario | Stock MSPT | YaP MSPT | Delta | Verdict |
|----------|------------|----------|-------|---------|
| idle | 0.247 | 0.245 | +1.0% | **WIN** |
| entity (480 primed TNT) | 0.260 | 0.248 | +4.7% | **WIN** |
| farm | 0.248 | 0.241 | +2.9% | **WIN** |

Artifacts: `bench/results/20260821T055528Z-*`, `…T055657Z-*`, `…T055825Z-*`.
