# YaPcore testing

Product path is **YaP-Folia**. Prefer live smokes and MSPT benches over the old
Paper chassis lab. Script index: [scripts/README.md](../../scripts/README.md).

## Quick commands

| Command | What it runs |
|---------|----------------|
| `./scripts/test-unit.sh` | `gradle test` |
| `./scripts/test-fray.sh` | `gradle frayTest` |
| `./scripts/test-all.sh` | `gradle verifyConcurrency` |
| `./scripts/smoke-folia.sh` | Boot YaP-Folia + ready hold |
| `./scripts/soak-yap-folia.sh compat` | Compat soak profile |
| `./scripts/smoke-network-full.sh` | Full release gate |
| `./scripts/smoke-phase7-soak.sh` | Phase 7 play soak (600s JE+BE + gameplay) |
| `./scripts/bench/run-vs-folia.sh` | Stock Folia vs YaP-Folia MSPT |
| `./scripts/bench/run-vs-all.sh` | Full ecosystem speedtest (Folia + Paper line) |
| `./scripts/validate-mmo-content.sh` | MMO quest/content validation |
| `./scripts/heap-dump.sh` | Heap dump for MAT |

## Concurrency (chassis)

```bash
gradle test          # unit (excludes @FrayTest / soak tags)
gradle frayTest      # deterministic interleavings
gradle verifyConcurrency
gradle jcstress      # optional low-level atomics
gradle spotbugsMain  # static analysis
```

Reports: `build/reports/`, `build/jcstress-results/`.

## Product smokes

```bash
./scripts/build-yap-folia.sh
FOLIA_JAR_SOURCE=build ./scripts/smoke-folia.sh
./scripts/soak-yap-folia.sh compat
./scripts/smoke-network-full.sh          # assembleRelease + Link/Folia gates
FAST=1 ./scripts/smoke-network-full.sh   # skip Bedrock live boot
```

Link suite: `smoke-yap-link-folia.sh`, `smoke-yap-link-plugins.sh`,
`smoke-yap-link-bedrock.sh`, `smoke-yap-link-two-backend.sh`.

Protocol matrix (Via/Geyser parity): `scripts/protocol-matrix/` — see
[VIA_GEYSER_PARITY.md](../protocol/VIA_GEYSER_PARITY.md).

**Tier 4 release gates** (with YaPcore listening on `25566`):

```bash
gradle :test --tests 'com.yapcore.protocol.via.*' --tests 'com.yapcore.crossplay.bedrock.*'
./scripts/protocol-matrix/play-soak.sh --all
HOST=127.0.0.1 PORT=25566 ./scripts/protocol-matrix/run-matrix.sh
HOST=127.0.0.1 PORT=25566 ./scripts/protocol-matrix/run-bedrock-smoke.sh
# optional full network bench (slow):
./scripts/smoke-network-full.sh
```

Baseline artifact: `build/tier4-4a-baseline.json`. Phased plan:
See [VIA_GEYSER_PARITY.md](../protocol/VIA_GEYSER_PARITY.md).

**Production battery** (release gate + stress):

```bash
gradle verifyConcurrency
./scripts/smoke-network-full.sh
HOST=127.0.0.1 PORT=25566 ./scripts/protocol-matrix/run-matrix.sh
HOST=127.0.0.1 PORT=25566 ./scripts/protocol-matrix/run-bedrock-smoke.sh
./scripts/smoke-bedrock-play.sh
SOAK_SECS=300 ./scripts/soak-yap-folia.sh compat   # 5 min hold; perf profile = 600s
./scripts/smoke-phase7-soak.sh                     # §E + gameplay (600s; FAST_PHASE7=1 for 60s)
```

Summary artifact: `build/production-test-battery-latest.json`.

## MSPT benches (Folia / bots)

Spawncollapse (no bots): [BENCH_VS_FOLIA.md](../performance/BENCH_VS_FOLIA.md).

**Mineflayer bot swarm** (`highpop` / `fullcite`): [BENCH_BOTS.md](../performance/BENCH_BOTS.md).

```bash
# Install bots + Paper 26.2 protocol patch
cd scripts/bench/bots && npm install

# 100 active bots — stock Folia
YAP_BENCH_PLAYERS=100 YAP_BENCH_COMPETITORS=folia \
  ./scripts/bench/run-vs-folia.sh highpop 30

# 200 bots (cite-stable: keepalive only)
YAP_BENCH_PLAYERS=200 NODE_OPTIONS="--max-old-space-size=8192" \
  YAP_BENCH_COMPETITORS=folia ./scripts/bench/run-vs-folia.sh highpop 30
```

Gate: JSON must show `players_ok: true` and `players_end` ≥ 90% of target.
Citeable win: `python3 scripts/bench/compare-folia.py` reports **≥5%** lower MSPT on YaP vs stock Folia.

```bash
# Citeable fullcite (ship knobs default on YaP side)
YAP_BENCH_PLAYERS=100 YAP_BENCH_COMPETITORS=folia,yapcore \
  ./scripts/bench/run-vs-folia.sh fullcite 40
python3 scripts/bench/compare-folia.py \
  bench/results/<stamp>-fullcite-folia.json \
  bench/results/<stamp>-fullcite-yapcore.json
```

## Endurance FAIL codes (chassis harness)

If you run the Gradle endurance harness and see FAIL codes:

| Code | Meaning | Fix |
|------|---------|-----|
| `LIVE_TOKEN_RETENTION` | `SequenceToken.LIVE` not draining | `forget()` in `finally`; prune orphans |
| `STREAM_KEY_LEAK` | Unique stream keys per event | Stable keys + `forgetStream` on disconnect |
| `QUEUE_BACKLOG` | T7/T8 not draining | Lease deadlock / logging flood / backpressure |
| `HANDOFF_LOSS` | processed ≪ submitted | Same as backlog |
| `METRIC_CARDINALITY` | Unbounded metric keys | Fixed action names |
| `HEAP_GROWTH` / `THREAD_GROWTH` | WARN | JFR / MAT / executor shutdown |

> `build/reports/problems/problems-report.html` is Gradle’s deprecation UI — not a soak report.
