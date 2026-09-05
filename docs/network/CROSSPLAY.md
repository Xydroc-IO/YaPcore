# Crossplay & multi-version (first-party Geyser + Via class)

YaPcore aims for **one shared world** with first-party protocol coverage:

- **Bedrock:** `GeyserStyleTranslator` / CrossplayHub — not the Geyser jar
- **Older / other JE:** `ProtocolCompat` / `ViaStyleRemapper` — not Via\* jars
- **Floodgate-class auth:** core `FloodgateAuth` + backend `yap-floodgate.jar` behind Velocity/Link

**Supported JE floor: 1.20.2+** onto Folia/Paper 26.2. Bedrock smoke: `geyserParitySmoke=true` on 1.21.50.

**Product note:** Default `game-authority=folia`. Phase 4 join/spawn + core play-depth are green.
Wave 2 closes inventory/forms honesty for native Bedrock; Floodgate-only and some UIs are **Limited** / **Out** (not silent Partial).

## Connection paths

| Path | Transport | Forms | Action bar / sidebar | Inventory authority |
|------|-----------|-------|----------------------|---------------------|
| Native YaPcore Bedrock | UDP dual-stack | Chassis `FormService` (simple/modal/custom) | `BedrockUiBridge` | Shadow + Paper inject |
| Velocity/Geyser → YaPFloodgate | JE TCP to Folia | **Limited** — no chassis session; chat explains forms need native UDP | Paper action bar + scoreboard | Paper Bukkit inventory |
| YaP Link + Bedrock on YaPcore gateway | Proxy UDP terminating on chassis | Same as native | Same | Same |

## Fidelity matrix (Wave 2)

| ID | Area | Status |
|----|------|--------|
| Join / spawn | JE + Bedrock smoke | **Green** |
| Dig / place / chat / commands | Shared world ops | **Green** |
| G.25 | Entity health/nametag | **Green** |
| G.28 | `/clear` inventory | **Green** |
| G.31 | Title / bossbar / scoreboard | **Green** |
| G.33 | Placed skull block actor | **Green** (owner name) |
| G.33 | Item-in-hand player heads | **Green** (SkullOwner Name NBT); full profile hash texture = Stretch |
| G.34 | JE pack → BE offer | **Green** |
| P4.6 | Chest / furnace / hopper open | **Green** (live Paper resync on push) |
| P4.6 | Enchant / workbench / villager | **Green** (best-effort); XP/layout polish ongoing |
| Forms (native UDP) | Simple / modal / custom | **Green** |
| Forms (Floodgate-only) | Admin / custom forms | **Limited** — explicit user message; not Partial |
| Anvil / smithing / loom / stonecutter / cartography | Container UIs | **Green** — Paper-backed open + slot sync; **recipe pick** via CRAFT_RECIPE_OPTIONAL (stonecutter/loom/smithing/cartography); anvil rename FILTER_TEXT coded (deploy after soak) |
| Full Geyser feature matrix | Every BE packet | **Out** — YaP intentional depth |

## Streamlined one-port join

```properties
shared-listen-port=true
crossplay-enabled=true
port=25566
bedrock-port=25566
```

| Edition | Socket (local bind) | Same-PC | Public (nginx / SRV) |
|---------|---------------------|---------|----------------------|
| Java | TCP `:25566` | `127.0.0.1:25566` | `yapcoremc.yaplabs.us:25565` |
| Bedrock | UDP `:25566` | `127.0.0.1:25566` | `yapcoremc.yaplabs.us:25565` |

Disable with `shared-listen-port=false` for a separate Bedrock UDP port.

## Architecture

```
Java TCP  ──┐  ViaStyleRemapper (older JE)   ┌─ Folia regions (default game)
            ├─ DualStackGateway ─────────────┤
Bedrock UDP─┘  CrossplayHub                  └─ UnifiedPlayer roster
               GeyserStyleTranslator
               FloodgateAuth (core) / yap-floodgate (Velocity / YaP Link)
```

## Scope

**Target:** Geyser-class + Via-class coverage in YaP code for supported bands.
**Not claimed:** stock Geyser jar parity, Floodgate-only forms, or every complex JE container on Bedrock.

## §E — live operator checklist (production closeout)

Run once on a live YaP-Folia box before calling crossplay “ops-signed.” Automated matrix rows above stay Green; this is **manual** confirmation.

### Join & shared world

- [x] JE modern client joins (`1.20.2+` / 26.2)
- [ ] Bedrock (or Bedrock Android/Win) joins native UDP path
- [x] Dig / place / chat / one command each side
- [x] Resource pack prompt accepts; pack applies (or decline with `resource-pack-forced=false`)

### Containers (specialty stations)

**JE (2026-09-04):** open + slots work for the full specialty set below.

- [x] Chest / furnace / hopper
- [x] Anvil
- [x] Smithing table (JE)
- [x] Loom (JE)
- [x] Stonecutter (JE)
- [x] Cartography table (JE)
- [x] Enchant table (JE) — workbench / villager still best-effort as needed

**Bedrock (native UDP)** — confirm UI opens, slots sync, and recipe pick selects a result:

- [ ] Smithing / loom / stonecutter / cartography / enchant on Bedrock (recipe pick wired in chassis — retest after deploy)
- [ ] Workbench / villager (best-effort) on Bedrock
- [ ] Anvil rename text (FILTER_TEXT — retest after deploy)

### Optional smoke (gameplay box)

- [ ] `/yapknobs status` (`nmsHooks: present=true` on YaP-Folia with patch 0025)
- [ ] `/skills` open when `yap-skills.jar` is installed and enabled

Retail Xbox / full inv depth: validate on real hardware before marketing “full play depth.”

## GUI

- **Connect** — Crossplay address + Copy
- **Settings** — Shared listen port + Crossplay toggles
- **nginx** — domain / stream ports

## Related

- [VELOCITY.md](VELOCITY.md) — Floodgate behind proxy
- Bedrock forms: native UDP session required; Floodgate-only forms stay **Limited** (this doc)
- [YAPCORE_WHITEPAPER.md](../whitepaper/YAPCORE_WHITEPAPER.md)
