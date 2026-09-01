# Completion backlog (operator checklist)

Honest list of what is **shipped but thin** vs **still to build**. Use this
with [ROADMAP_COMPLETION_PHASES.md](ROADMAP_COMPLETION_PHASES.md).

## Tier 1 — fix broken / incomplete core (do first)

| Item | Owner | Status |
|------|-------|--------|
| YaPTab sidebar on Folia | `yap-tab` | **Done** — packet sidebar via scoreboard-library |
| Claim flags `fire-spread` / `mob-spawning` | `yap-playerdata` | **Done** — event hooks |
| Admin region same flags | `yap-regions` | **Done** |
| Admin menu slot bugs | `yap-admin` | **Done** — 54-slot menus |
| Combat PvE feel | `yap-combat` | **Done** — `pve.*` defaults + config merge on load |

Restart Folia after jar sync to pick up Tier 1 fixes.

## Tier 2 — ops surfaces

| Item | Notes | Status |
|------|--------|--------|
| Dashboard Phase 8 polish | Ops plugins card on status tab; map/discord hints | **Done** |
| Swing Tune vs in-game admin | Documented in [TUNE.md](../ops/TUNE.md) + [ADMIN_MENU.md](../ops/ADMIN_MENU.md) | **Done** |
| Web map polish | Config tuning comments; first render on enable | **Done** |
| Discord MC↔Discord relay | [DISCORD_RELAY.md](../ops/DISCORD_RELAY.md); webhooks on; two-way chat off by default | **Done** |

## Tier 3 — gameplay depth (opt-in)

| Item | Notes | Status |
|------|--------|--------|
| Full RS quest/boss roster | 100 quests + 20 bosses + `starter_chain.yml` in jar; [compendium-npc-bindings.txt](../../examples/yap-npcs/compendium-npc-bindings.txt) | **Done** |
| Legacy combat spells | `/cast` routes through **YaPAbilities** only (VFX/projectiles) | **Done** |
| Bedrock skill UI depth | Skill picker + detail, recipe/hiscore pickers, quest panel | **Done** |
| Ability pose / Bedrock animations | Action-bar cast feedback on Bedrock via `AnimationSync` | **Done** (v1; full animate packets deferred) |
| TAB cross-server sync | Heartbeat, CLEAR, `/yaptab sync`; [YAP_LINK.md](../network/YAP_LINK.md) | **Done** (v1.1) |

## Tier 4 — protocol / edge

Phased plan: **[TIER4_PHASES.md](TIER4_PHASES.md)** — do **4A → 4F** in order (4B ∥ 4C after 4A).

| Phase | Focus | Status |
|-------|--------|--------|
| **4A** | Baseline gates — matrix, bedrock smoke, play-soak, record §E | **Done** (automated); live §E pending |
| **4B** | Bedrock play depth — G.33 skulls, inv, columns, UI, combat | **Next** |
| **4C** | JE backwards 1.20.2+ — inventory, blocks, spawn, chat | Pending (parallel after 4A) |
| **4D** | JE forward + protocol dumps | Pending |
| **4E** | Optional edge — Grim fetch (done), Guard dashboard hint | **Done** |
| **4F** | Lock docs, limitations, claim language, CI checklist | Pending |

### What Tier 4 is (scope)

1. **Protocol edge (big)** — first-party Via/Geyser-class parity on the YaPcore chassis + YaP-Folia backend. Not installing Via/Geyser jars. Work is in `com.yapcore.protocol.*` and `com.yapcore.crossplay.*`: packet remaps, Bedrock gameplay bridge, soak CI, limitation docs. “Done” = supported JE/Bedrock bands pass matrix + live soak without stock Via/Geyser.
2. **Optional third-party edge (small)** — Grim AC, Tebex, etc.: fetch scripts, license notices, operator docs, release-zip inclusion. **No fork** of Grim inside YaPGuard.
3. **Not Tier 4** — rebuilding Matrix in YaPGuard, cloning WorldGuard (that’s YaPRegions/claims, already shipped).

## Anti-cheat decision (locked)

- **WorldGuard-class** = YaPRegions + claims (**not** YaPGuard)
- **YaPGuard** = native lightweight AC
- **Gold-standard AC** = optional Grim (or similar), not a YaPGuard rewrite
