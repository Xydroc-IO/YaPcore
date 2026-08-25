# Agent 3 Folia patches (Phase 3)

| File | Workstream | Status |
|------|------------|--------|
| `0010-yap-async-chunk-save.patch` | 3.3 Moonrise flush off region thread | **landed** (post-apply) |
| `0011-yap-scoreboard-swmr.patch` | 3.4 CraftScoreboard SWMR | **landed** (post-apply) |
| `0012-yap-entity-tick-budget.patch` | 3.2 hot-region Mob AI budget | **landed** (post-apply) |

**Apply order:** `folia-patch.sh pre` → `applyAllPatches` → `folia-patch.sh post`  
(see `scripts/build-yap-folia.sh`).

## Knobs

| System property | Default | Effect |
|-----------------|---------|--------|
| `-Dyap.folia.async-chunk-save=true` | false | Enqueue Moonrise flush off region thread |
| `-Dyap.folia.scoreboard-swmr=true` | false | Allow Bukkit scoreboard mutations under write lock |
| `-Dyap.folia.entity-tick-budget=N` | 0 (off) | Max **Mob** AI ticks per region tick; TNT/players/vehicles/items always tick |

## Citeable bench

Stamp **`20260824T234919Z`** spawncollapse (region MSPT @ chunk 0,0):

| Side | mspt_mean | fuse_ok |
|------|----------:|:-------:|
| stock Folia | 25.2466 | yes |
| YaP-Folia | 21.4509 | yes |

**−15.0%** with `-Dyap.folia.entity-tick-budget=300`. See `docs/BENCH_VS_FOLIA.md`.

## Perf soak (Next Wave)

- `./scripts/smoke-folia-async-save.sh` — stamp `20260825T014032Z` (**missed ≤50% spike target**; load floor ~6 ms)
- `./scripts/smoke-folia-scoreboard.sh` — SWMR live PASS (`EXPECT_FAIL=0`)
- Operator knobs: `folia-async-chunk-save` / `folia-entity-tick-budget` / `folia-scoreboard-swmr` in `server.properties` (**defaults OFF**)
- Profiles: `docs/YAP_FOLIA_SOAK.md`
