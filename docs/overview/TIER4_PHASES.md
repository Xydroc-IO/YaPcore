# Tier 4 — protocol / edge (phased plan)

Complete first-party protocol parity + optional edge ops. Work in order; each phase
has a **gate** before the next starts.

Track status in [COMPLETION_BACKLOG.md](COMPLETION_BACKLOG.md).

---

## Phase 4A — Baseline gates (soak truth)

**Goal:** Know what actually fails before writing code.

**Status:** Automated unit/smoke gates **PASS** (2026-09-01). Live §E checklist still operator-owned.

| Task | Command / artifact |
|------|-------------------|
| JE join matrix | `./scripts/protocol-matrix/run-matrix.sh` → `build/protocol-matrix-latest.json` |
| Bedrock smoke | `./scripts/protocol-matrix/run-bedrock-smoke.sh` → `build/bedrock-smoke-latest.json` |
| Play soak | `./scripts/protocol-matrix/play-soak.sh --all` |
| Network full | `./scripts/smoke-network-full.sh` → `build/smoke-network-full-latest.json` |
| Record failures | Update checklist §E in [VIA_GEYSER_PARITY.md](../protocol/VIA_GEYSER_PARITY.md) |

**Gate:** All automated smokes run to completion (pass or logged fail). Play soak
checklist items marked pass/fail honestly.

---

## Phase 4B — Bedrock play depth

**Goal:** Pure Bedrock clients play vanilla loops on shared YaP-Folia world.

| ID | Work | Parity rows |
|----|------|-------------|
| 4B.1 | Placed skull / player-head textures on BE | G.33 |
| 4B.2 | Inventory vault + hotbar sync soak hardening | G.27 |
| 4B.3 | Column stream at chunk borders (no flat void default) | G.14, P4.5 |
| 4B.4 | Container / villager / enchant UI pass | G.30 |
| 4B.5 | Combat / interact metadata live harden | G.23, G.25 |
| 4B.6 | Forms + skin JE visibility soak | G.50–52 |

**Gate:** §E Bedrock checklist green (move, break/place, inv, chat, command, form).

---

## Phase 4C — JE backwards (1.20.2+)

**Goal:** Older JE clients on YaP-Folia 26.2 without ViaBackwards jar.

| ID | Work | Parity rows |
|----|------|-------------|
| 4C.1 | Window click + creative slot bodies | VB.12, VB.16 |
| 4C.2 | Entity metadata + spawn body reshape | VB.13–15 |
| 4C.3 | Block name / state bridge + unknown-id policy | VB.21, VB.23 |
| 4C.4 | Chat / signed chat / player info | VB.18 |
| 4C.5 | Smithing / new UI on older mid (document limits) | VB.25 → [VIA_BACKWARDS_LIMITATIONS.md](../protocol/VIA_BACKWARDS_LIMITATIONS.md) |
| 4C.6 | Catalog + PlayPacketRemapper heuristics | VB.20, VB.30 |

**Gate:** §E JE mid checklist green (1.20.4 + 1.21.1 minimum).

---

## Phase 4D — JE forward & dumps

**Goal:** Newer JE clients when server lags a Mojang build.

| ID | Work | Parity rows |
|----|------|-------------|
| 4D.1 | Forward heuristics (keepalive/chunk/spawn/item) | V1.3 |
| 4D.2 | Block/item/entity content forward | V1.9 |
| 4D.3 | Next-protocol dump workflow when Mojang ships | V1.8, P4.10, [PROTOCOL_DUMPS.md](../protocol/PROTOCOL_DUMPS.md) |

**Gate:** Forward matrix cases green for pinned bands or documented in limitations.

---

## Phase 4E — Optional edge ops

**Goal:** Third-party edge matches Tebex pattern; native AC coexistence clear.

| ID | Work | Status |
|----|------|--------|
| 4E.1 | Grim fetch + docs | **Done** — [GRIM.md](../ops/GRIM.md) |
| 4E.2 | YaPGuard disable guide when Grim present | **Done** — GRIM.md + ANTICHEAT.md |
| 4E.3 | Dashboard Guard tab hint when `grim.jar` installed | Pending |
| 4E.4 | Release zip includes grim notices when fetched | **Done** — yap-release.gradle.kts |

**Gate:** Operator can fetch Grim, disable YaPGuard, and see status in dashboard or docs.

---

## Phase 4F — Lock & claim language

**Goal:** Tier 4 honestly closable.

| Task | Output |
|------|--------|
| Tick §E checklist in VIA_GEYSER_PARITY.md | Only items actually passed |
| Refresh VIA_BACKWARDS_LIMITATIONS.md | Any remaining Partial rows |
| Update COMPLETION_BACKLOG Tier 4 → Done | With caveats from limitations doc |
| CI: matrix + bedrock smoke in release checklist | TESTING.md / release notes |

**Gate:** Claim language §H in VIA_GEYSER_PARITY.md is accurate for what passed.

---

## Explicit non-goals (all phases)

- ViaRewind 1.8 play depth
- Shipping Via/Geyser/Floodgate jars on product path
- Matrix clone inside YaPGuard
- Floodgate Global Linking

---

## Suggested agent split

| Phase | Theme | Can parallelize after |
|-------|--------|------------------------|
| 4A | Soak / CI | — (first) |
| 4B | Bedrock | 4A gate |
| 4C | JE backwards | 4A gate (parallel with 4B) |
| 4D | JE forward | 4C partial |
| 4E | Ops | anytime after 4E.1 |
| 4F | Docs lock | 4B + 4C gates |
