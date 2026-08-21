# Crossplay (Geyser-class) & shared port

YaPcore aims for **one shared world**. Java and Bedrock clients join via a
Geyser-style translation hub (`com.yapcore.crossplay`).

**Product note:** With default `game-authority=paper`, Paper owns the JE game.
Phase 3 spatial tick is live on YapEngine. **Phase 4** polishes dual-stack so BE
and JE feel like one finished join story on that Paper-backed world. See
[PAPER_YAPENGINE_PORT.md](PAPER_YAPENGINE_PORT.md).

## Streamlined one-port join

By default:

```properties
shared-listen-port=true
crossplay-enabled=true
port=25566
bedrock-port=25566
```

| Edition | Socket | Address |
|---------|--------|---------|
| Java | TCP `:25566` | `host:25566` |
| Bedrock | UDP `:25566` | `host:25566` |

Same host **and** same port number — OS allows TCP and UDP to share a port.
Players type one address; the client protocol picks TCP vs UDP.

Disable with `shared-listen-port=false` to use a separate Bedrock UDP port.

## Architecture

```
Java TCP  ──┐                    ┌─ YapEngine spatial cores (same map)
            ├─ DualStackGateway ─┤
Bedrock UDP─┘   CrossplayHub     └─ UnifiedPlayer roster
                GeyserStyleTranslator
```

On join, both editions register a `UnifiedPlayer` into the same spatial
partition. Moves/chats/clicks are translated into shared engine ops.

## Honest scope

This is **Geyser-class architecture** (shared world + translator + dual front
door). Full vanilla RakNet/JE packet parity is still expanding — the join,
shared spawn, and action translation path are live; gameplay codecs deepen
over time.

## GUI

- **Connect** — one Crossplay address + Copy
- **Settings** — Shared listen port + Crossplay toggles
