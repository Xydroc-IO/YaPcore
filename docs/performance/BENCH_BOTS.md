# Mineflayer bot swarm — highpop / fullcite benches

**Purpose:** Load stock Folia / Paper / YaP-Folia with **real JE clients** (Mineflayer)
for `highpop` and `fullcite` MSPT scenarios. The bench plugin counts
`Bukkit.getOnlinePlayers()` and requires **≥90%** of `YAP_BENCH_PLAYERS` held stable
before sampling.

**Harness:** [`scripts/bench/bots/swarm.js`](../../scripts/bench/bots/swarm.js) ·
[`scripts/bench/run-vs-folia.sh`](../../scripts/bench/run-vs-folia.sh)

---

## Quick run

```bash
# Install + patch protocol 26.2 support (also runs on npm postinstall)
cd scripts/bench/bots && npm install && ./patch-minecraft-data.sh

# 100 bots, stock Folia, 30s sample
YAP_BENCH_PLAYERS=100 YAP_BENCH_COMPETITORS=folia \
  ./scripts/bench/run-vs-folia.sh highpop 30

# 200 bots (cite-stable: physics OFF — default at ≥150)
YAP_BENCH_PLAYERS=200 YAP_BENCH_COMPETITORS=folia \
  NODE_OPTIONS="--max-old-space-size=8192" \
  ./scripts/bench/run-vs-folia.sh highpop 30

# Full cite: bots + fixtures (600 entities, 128 heavy hoppers)
YAP_BENCH_PLAYERS=100 YAP_BENCH_COMPETITORS=folia,yapcore,paper \
  ./scripts/bench/run-vs-folia.sh fullcite 40
```

**Stop live YaPcore** before bot benches — port `25566` must be free; bench servers
use `25680`–`25686`.

---

## Why bots failed (2026-09-01) — root cause

| Stage | Symptom | Cause |
|-------|---------|-------|
| 1 | `unsupported protocol version: 26.2` | `YAP_BOT_VERSION=26.2` passed to Mineflayer before protocol data existed |
| 2 | `Outdated client! Please use 26.2` | Fallback to `1.21.4` — server requires **protocol 776** |
| 3 | `No chunk implementation for pc 26.2` | `minecraft-data` indexes 26.2 but shipped no `pc/26.2/` folder |
| 4 | `No liquid gravity settings` | `prismarine-physics` feature flags stopped at `26.1` |

**Not the cause:** ready-marker timing (`highpop-ready.port` worked). Live YaPcore on
`:25566` did not block bench ports.

---

## Fix: `patch-minecraft-data.sh`

[`scripts/bench/bots/patch-minecraft-data.sh`](../../scripts/bench/bots/patch-minecraft-data.sh)
runs on `npm postinstall` and before each swarm launch:

1. Clone `minecraft-data` **26.1 → 26.2** with `version.json` protocol **776**
2. Inject `26.2` block into `minecraft-data/data.js`
3. Add `26.2` to Mineflayer `testedVersions`
4. Add `26.2` to `prismarine-chunk` and `prismarine-physics` version tables

Default client version: **`YAP_BOT_VERSION=26.2`** (Paper/Folia build label).

Upstream Mineflayer will ship native 26.2 eventually — remove or narrow the patch when
`npm view minecraft-data` includes a real `pc/26.2/` tree.

---

## Bot modes

| Mode | When | Behavior |
|------|------|----------|
| **`active`** | `YAP_BENCH_PLAYERS` &lt; 150 (default) | Movement, look, combat, inventory after join quiet |
| **`cite-stable`** | `PLAYERS` ≥ 150 (default) | Keepalive only — physics OFF through sample (fair MSPT cite) |
| **Force active** | `YAP_BOT_CITE_STABLE=0` | Active physics even at 150–250 bots (stress; may drop bots on Paper) |

Join quiet: no world interact until **≥90%** spawned (`YAP_BOT_STAGGER_MS` 150–500 ms).

Workers: at **≥150** players with `cite-stable`, **2** Node workers; with
`YAP_BOT_CITE_STABLE=0` and `active`, **4** workers (`YAP_BOT_WORKERS` overrides).

---

## Verified results (2026-09-01, post-fix)

Stock **Folia 26.2**, `highpop`, game JVM 8G/12G, `measurement_scope=game_tick_mspt`.

| Stamp | Bots | Mode | Players start/end | `players_ok` | MSPT mean | Notes |
|-------|-----:|------|------------------:|:------------:|----------:|-------|
| `20260901T221000Z-botjoin3` | 10 | active | 10 / 10 | **true** | 5.96 | Smoke after patch |
| `20260901T232100Z-bots100` | 100 | active | 100 / 100 | **true** | 10.87 | Held 100/100 through sample |
| `20260901T232700Z-bots200` | 200 | cite-stable | 200 / 200 | **true** | 14.04 | 2×100 workers; physics OFF |

### fullcite (100 bots + 600 entities + 128 heavy hoppers)

| Stamp | Peer | Players | `players_ok` | MSPT mean |
|-------|------|--------:|:------------:|----------:|
| `20260901T235600Z-fullcite` | stock Folia | 100 / 100 | **true** | 19.18 |
| `20260901T235600Z-fullcite` | yap-folia-chassis | 100 / 100 | **true** | 18.62 |
| `20260901T235600Z-fullcite` | stock Paper | 100 / 100 | **true** | 28.94 |

Artifacts:

- `bench/results/20260901T235600Z-fullcite-fullcite-{folia,yapcore,paper}.json`

---

## Invalid / do not cite

| Stamp | Issue |
|-------|-------|
| `20260901T210712Z-speedtest` **fullcite** rows | `players_ok: false`, `players_end: 0` — pre-fix bot version |
| `20260901T215113Z-botfix` | Kicked `Outdated client! Please use 26.2` — partial fix only |

**Spawncollapse** rows from the same speedtest stamp remain **valid** (no bots).

---

## Fairness rules (bot scenarios)

| Rule | Value |
|------|-------|
| Game JVM | `YAP_BENCH_GAME_XMS=8G` `YAP_BENCH_GAME_XMX=12G` (default for bot scenarios) |
| `online-mode` | `false` (offline Mineflayer usernames `yapbot_NNN`) |
| Join gate | ≥90% online, 15s stable hold, then warmup + post-warmup settle |
| `players_ok` | Start **and** end sample must be ≥90% of target |
| Cooldown | 30s between competitor runs (default) |

---

## Environment reference

| Variable | Default | Role |
|----------|---------|------|
| `YAP_BENCH_PLAYERS` | 100 (`highpop`/`fullcite`) | Target bot count |
| `YAP_BOT_VERSION` | `26.2` | Mineflayer protocol version |
| `YAP_BOT_STAGGER_MS` | `min(500, max(150, 100000/PLAYERS))` | Connect stagger |
| `YAP_BOT_WORKERS` | auto (2 or 4) | Node swarm processes |
| `YAP_BOT_CITE_STABLE` | auto | `0` = force active physics |
| `YAP_BENCH_JOIN_TIMEOUT` | `180 + PLAYERS` seconds | Max wait for join gate |
| `NODE_OPTIONS` | `--max-old-space-size=4096` | Raise for 200+ bots |

---

## Next steps (bench backlog)

1. ~~**fullcite 100 bots** — folia, yapcore, paper with `players_ok: true`~~ **Done** (`20260901T235600Z-fullcite`)
2. **Active 200 bots** — `YAP_BOT_CITE_STABLE=0` stress (optional)
3. **Drop patch** when PrismarineJS ships native 26.2 protocol data

---

## Related

- [BENCH_VS_FOLIA.md](BENCH_VS_FOLIA.md) — MSPT scoreboard, spawncollapse, ecosystem speedtest
- [TESTING.md](../start/TESTING.md) — release gates + production battery
- [scripts/README.md](../../scripts/README.md) — script index
