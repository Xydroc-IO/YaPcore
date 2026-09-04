# Canvas peer parity notes (26.2)

Goal: beat stock Folia ≥5% on fullcite **and** rank ahead of / competitive with Canvas
using the **ship knob profile** (smart budget + microtick + subregion partition).

## Ship cite profile

See [`YAP_FOLIA_SOAK.md`](YAP_FOLIA_SOAK.md). Result JSON must include `knob_*`.
`YAP_MSPT_REQUIRE_SHIP_KNOBS=1` enforces entity≥400, microtick≥8, partition=true, async=true.

## Peer heavypop ranking (ship knobs)

Stamp **`20260904TshipOn`** (hopper=64, entity=400, microtick=8, partition=true, async=true):

| Rank | Label | mspt_mean |
|------|-------|----------:|
| 1 | yap-folia-chassis | 10.7830 |
| 2 | stock-canvas | 11.1986 |
| 3 | stock-folia | 11.6637 |

YaP vs stock Folia **−7.55%** (citeable). YaP vs Canvas **−3.85%** (ranked #1; within 5% noise vs Canvas — claim rank lead, not ≥5% Canvas beat).

Baseline A/B (`20260904TshipBase`, smart knobs off): YaP **−7.19%** vs stock — async+hopper alone.
Ship knobs add a small heavypop edge here (region MSPT ≈11 sits near the 12ms budget gate).

Fixtures: `src/test/resources/mspt/{stock-folia,yap-folia}-heavypop.json`, `peer-heavypop-canvas.json`.

## Fullcite vs Canvas

Official stock vs Yap peak: **−12.4%** (`20260904TshipFc2`). Latest ship-gate re-verify: **−5.53%** (`20260904T040935Z`, fixtures `cite-fullcite-*.json`). Both citeable under disclosed ship knobs.

Peer ranking fixture `cite-fullcite-canvas.json` (from `20260904TshipFc` 3-way):

| Rank | Label | mspt_mean |
|------|-------|----------:|
| 1 | yap-folia-chassis | 21.6583 |
| 2 | stock-folia | 24.7234 |
| 3 | stock-canvas | 35.3606 |

YaP leads both peers on fullcite under ship knobs. Canvas row is ranking-only (not the pairwise ≥5% gate).

## Port rules (Canvas bumps)

1. Preserve owning-region mutation.
2. ≤500-line domain files under `threadedregions`.
3. Pass soak-compat + `fuse_ticking_ok`.
