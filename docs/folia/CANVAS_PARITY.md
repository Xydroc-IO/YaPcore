# Canvas peer parity notes (26.2)

Goal: beat stock Folia ≥5% on fullcite **and** beat Canvas ≥5% on heavypop when fair,
using the **ship knob profile** (smart budget + microtick + subregion partition).

## Ship cite profile

See [`YAP_FOLIA_SOAK.md`](YAP_FOLIA_SOAK.md). Result JSON must include `knob_*`.
`YAP_MSPT_REQUIRE_SHIP_KNOBS=1` enforces entity≥400, microtick≥8, partition=true, async=true.

## Peer heavypop ranking (ship knobs)

Stamp **`20260904T065505Z`** (campaign; hopper=64, entity=400, microtick=8, partition=true, async=true):

| Rank | Label | mspt_mean |
|------|-------|----------:|
| 1 | yap-folia-chassis | 9.9395 |
| 2 | stock-canvas | 10.8144 |
| 3 | stock-folia | 11.9115 |

YaP vs stock Folia **−16.56%** (citeable). YaP vs Canvas **−8.09%** — **citeable ≥5%** under disclosed ship knobs.

Prior stamp **`20260904TshipOn`**: YaP −7.55% vs stock; −3.85% vs Canvas (rank lead only).

Baseline A/B (`20260904TshipBase`, smart knobs off): YaP **−7.19%** vs stock — async+hopper alone.

Fixtures: `src/test/resources/mspt/{stock-folia,yap-folia}-heavypop.json`, `peer-heavypop-canvas.json`, `cite-heavypop-canvas-verdict.txt`.

## Fullcite vs Canvas

Official stock vs Yap peak: **−12.4%** (`20260904TshipFc2`). Latest ship-gate re-verify: **−5.53%** (`20260904T040935Z`, fixtures `cite-fullcite-*.json`). Both citeable under disclosed ship knobs.

Peer ranking fixture `cite-fullcite-canvas.json` (from `20260904TshipFc` 3-way):

| Rank | Label | mspt_mean |
|------|-------|----------:|
| 1 | yap-folia-chassis | 21.6583 |
| 2 | stock-folia | 24.7234 |
| 3 | stock-canvas | 35.3606 |

YaP leads both peers on fullcite under ship knobs. Canvas row is ranking-only (not the pairwise ≥5% gate).

## Canvas ≥5% campaign

```bash
./scripts/bench/cite-canvas-heavypop.sh 40
```

| Stamp | YaP vs Canvas | Claim |
|-------|---------------|-------|
| `20260904TshipOn` heavypop | **−3.85%** | Rank #1 only |
| `20260904T065505Z` heavypop | **−8.09%** | **Citeable ≥5%** |
| fullcite `20260904TshipFc` | YaP #1 (Canvas ~35 mspt) | Ranking-only vs Canvas |

Ship knobs unchanged: entity=400, microtick=8, hopper=64, async, partition. See [`REAL_GAINS.md`](REAL_GAINS.md).

Paper/Purpur scale story (not Folia-peer MSPT): [`PAPER_PURPUR_SCALE.md`](PAPER_PURPUR_SCALE.md).

## Port rules (Canvas bumps)

1. Preserve owning-region mutation.
2. ≤500-line domain files under `threadedregions`.
3. Pass soak-compat + `fuse_ticking_ok`.
