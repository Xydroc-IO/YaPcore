# Paper / Purpur — scale win (honest)

YaPcore does **not** claim single-thread MSPT victory over Paper/Purpur without a fair
bench. The product win is **regionized scale + encyclopedia + suite**.

## Why Paper/Purpur are a different class

| | Paper / Purpur | YaPcore (YaP-Folia) |
|--|----------------|---------------------|
| Tick model | One main thread | Region thread pool |
| Encyclopedia | Purpur `purpur.yml` (NMS fork) | YaP `knobs.yml` (plugin + optional Folia hooks) |
| Network / MMO | Assemble plugins | First-party CORE+NETWORK + GAMEPLAY |
| Crossplay | Geyser/Via jars | First-party dual-stack |

## Fair benches (product claim)

Use multi-region / high-pop scenarios where Folia-class servers are designed to win:

```bash
# Stock Paper/Purpur competitors (single-thread jars)
./scripts/bench/fetch-competitors.sh

# Multi-region style load on YaP-Folia ship knobs (existing Folia peer path)
./scripts/yapctl cite-fullcite

# Optional: document Paper/Purpur heavypop on same machine for operator context
# (not a Folia-apples MSPT cite — disclose single-thread vs regionized)
YAP_BENCH_COMPETITORS=paper,purpur ./scripts/bench/run-vs-paper-scale.sh heavypop 40
```

## Operator takeaway

- Need **Purpur-class mob/QoL config** without leaving Folia → YaP Encyclopedia ([TUNE.md](../ops/TUNE.md)).
- Need **100 concurrent active** with citeable MSPT → YaP-Folia ship knobs vs stock Folia/Canvas ([REAL_GAINS.md](REAL_GAINS.md)).
- Need **max Paper plugin ecosystem** on one thread → Paper/Purpur (legacy benches only).

## Results note (campaign)

Latest context stamp should be recorded under `bench/results/` and summarized here when operators
run `./scripts/bench/run-vs-paper-scale.sh`. Until then, cite YaP vs Folia/Canvas for MSPT and this
doc for the **product + scale** framing vs Paper/Purpur.

## Non-claims

- Do not market “beats Purpur MSPT on single-thread farms” until `run-vs-paper-scale.sh` fixtures land with disclosed methodology.
- Do not imply Purpur source was ported — encyclopedia is original YaP code.
