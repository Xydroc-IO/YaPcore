# Crossplay & multi-version (full Geyser + Via parity — first-party)

YaPcore aims for **one shared world** and **full** protocol coverage:

- **Bedrock:** built-in Geyser **parity** (`GeyserStyleTranslator` / CrossplayHub) — not the Geyser jar
- **Older / other JE:** built-in ViaVersion + ViaBackwards + ViaRewind **parity**
  (`ProtocolCompat` / `ViaStyleRemapper`) — not Via\* jars

**Phase 4 DoD** is that parity on the Paper-backed world. Slice plan:
[PHASE4_PROTOCOL.md](PHASE4_PROTOCOL.md).

**Product note:** With default `game-authority=paper`, Paper owns the JE game.
Phases 3–3.7 spatial tick is live on YapEngine (default on; high-pop target).
Phase 4 finishes dual-stack + full first-party Via/Geyser feature sets.
See [PAPER_YAPENGINE_PORT.md](PAPER_YAPENGINE_PORT.md).

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
Java TCP  ──┐  ViaStyleRemapper (older JE)   ┌─ YapEngine spatial cores (same map)
            ├─ DualStackGateway ─────────────┤
Bedrock UDP─┘  CrossplayHub                  └─ UnifiedPlayer roster
               GeyserStyleTranslator
```

On join, both editions register a `UnifiedPlayer` into the same spatial
partition. Moves/chats/clicks are translated into shared engine ops.

## Scope (Phase 4)

**Target:** full Geyser feature parity + full Via\* feature parity in YaP code.
Scaffold (join, shared spawn, action path, band registry) is live; codecs and
remaps expand until the [PHASE4_PROTOCOL.md](PHASE4_PROTOCOL.md) checklists pass.
Do not treat “Geyser-class / Via-class” as “good enough forever.”

## GUI

- **Connect** — Crossplay address + Copy (local vs public ports)
- **Settings** — Shared listen port + Crossplay toggles
- **nginx** — domain `yapcoremc.yaplabs.us`, stream/HTTP ports, install script
