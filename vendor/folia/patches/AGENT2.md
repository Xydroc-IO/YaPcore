# Agent 2 Folia patches (Phase 2)

| File | Workstream | Status |
|------|------------|--------|
| `0001-yap-teleport-transactions.patch` | Cross-region TP integrity | **landed** (also `folia-server/minecraft-patches/features/0012-…`) |

Scheduler shim is **not** a Folia patch — it is `yap-sched-agent` (`-javaagent`).
See `docs/FOLIA_SCHED_COMPAT.md`.

**Do not** edit Agent 3 files (`0010`/`0011` drafts, regionizer).
After merge: Agent 3 clear for region / async-save work.
