# Tebex

Canonical setup, package recipes, Hub-only install, and checklist live in
**[INTEGRATIONS.md](INTEGRATIONS.md#tebex)** (Discord + Tebex).

Quick path:

1. `./scripts/fetch-tebex.sh` → Hub / lobby only (`plugins/tebex.jar`)
2. Web admin → **Tebex store** → paste game-server secret
3. Copy package console commands (`{username}`) from the tab into creator.tebex.io
   **or** enable **webhook endpoint** (`yap-tebex.jar`) and map package IDs in
   `plugins/YaPTebex/config.yml` (do not use both for the same package)
4. Prefer `yapperm` + `kit grant` so ranks and kits deliver network-wide via YaPDB

Examples: [`examples/tebex/`](../../examples/tebex/) · Legal: [`third-party/tebex/`](../../third-party/tebex/)
