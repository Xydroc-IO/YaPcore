# Crossplay & multi-version (full Geyser + Via parity — first-party)

YaPcore aims for **one shared world** and **full** protocol coverage:

- **Bedrock:** built-in Geyser **parity** (`GeyserStyleTranslator` / CrossplayHub) — not the Geyser jar
- **Older / other JE:** built-in ViaVersion + ViaBackwards + ViaRewind **parity**
  (`ProtocolCompat` / `ViaStyleRemapper`) — not Via\* jars
- **Floodgate-class auth:** first-party `FloodgateAuth` / Xbox chain in core; behind Velocity use
  **`yap-floodgate.jar`** (not the Floodgate jar) — [VELOCITY.md](VELOCITY.md)

**Supported JE floor: 1.20.2+** onto Folia/Paper 26.2. Pre-1.19 / Rewind-depth is **best-effort**,
not product DoD for play remaps. Live matrix (2026-08-21): JE **7/7** join/spawn under zlib;
Bedrock smoke `geyserParitySmoke=true` on 1.21.50 (~1599 itemstates).

**Phase 4 DoD** is that parity on the Folia-backed world (product default). Slice roll-up:
[PHASE4_PROTOCOL.md](../protocol/PHASE4_PROTOCOL.md). Feature-by-feature checklist:
[VIA_GEYSER_PARITY.md](../protocol/VIA_GEYSER_PARITY.md).
Bedrock terrain defaults to **column stream** from the game authority (P4.5); flat is opt-in
(`-Dyapcore.bedrock.flat-chunks=true`). Automated soak preamble:
`./scripts/protocol-matrix/play-soak.sh`.

**Product note:** With default `game-authority=folia`, **Folia** owns the JE game.
Phase 3 Paper spatial tick is **not** product default (opt-in for Paper benches only;
Folia path has no Phase 3 spatial tick). Phase 4 finishes dual-stack depth —
join/spawn green; core play depth via `smoke-bedrock-play.sh` (do not claim full Geyser play parity yet).
See [PHASE4_PROTOCOL.md](../protocol/PHASE4_PROTOCOL.md) · [WHAT_WE_ARE.md](../overview/WHAT_WE_ARE.md).

## Streamlined one-port join

By default:

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

Same host **and** same **local** port number — OS allows TCP and UDP to share a port.
With nginx, players on the internet use **25565**; the origin still listens on **25566**.

Disable with `shared-listen-port=false` to use a separate Bedrock UDP port.

Domain / Cloudflare: [CLOUDFLARE_AND_NGINX.md](CLOUDFLARE_AND_NGINX.md).

## Architecture

```
Java TCP  ──┐  ViaStyleRemapper (older JE)   ┌─ Folia regions (default game)
            ├─ DualStackGateway ─────────────┤
Bedrock UDP─┘  CrossplayHub                  └─ UnifiedPlayer roster
               GeyserStyleTranslator
               FloodgateAuth (core) / yap-floodgate (Velocity / YaP Link)
```

On join, both editions register a `UnifiedPlayer` into the same shared world.
Moves/chats/clicks are translated into shared engine ops.

## Scope (Phase 4)

**Target:** full Geyser feature parity + full Via\* feature parity in YaP code.
**Join/spawn replace claim:** JE matrix green + Bedrock smoke green — operators do **not**
need Via\* or Geyser jars for supported bands. Soft gameplay depth (richer BE metadata,
block-state catalogs) still hardens from live clients — see [PHASE4_PROTOCOL.md](../protocol/PHASE4_PROTOCOL.md).

Re-run:

```bash
HOST=127.0.0.1 PORT=25566 ./scripts/protocol-matrix/run-matrix.sh
HOST=127.0.0.1 PORT=25566 ./scripts/protocol-matrix/run-bedrock-smoke.sh
```

## GUI

- **Connect** — Crossplay address + Copy (local vs public ports)
- **Settings** — Shared listen port + Crossplay toggles
- **nginx** — domain `yapcoremc.yaplabs.us`, stream/HTTP ports, install script
