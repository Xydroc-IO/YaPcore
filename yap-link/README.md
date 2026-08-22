# YaP Link

**YaP Link** is YapLabs’ **full Velocity-class proxy** — a first-party fork of
[PaperMC Velocity](https://github.com/PaperMC/Velocity) (GPL-3.0), maintained in-tree as our
network edge for Folia backends.

This is **not** a shim or partial proxy. It is the complete Velocity proxy codebase
(protocol, modern forwarding, `/server`, plugins API, compression, online-mode, etc.),
branded and packaged as YaP Link.

Java package names remain `com.velocitypowered.*` so **Velocity plugins keep working**.

## License

Upstream Velocity is **GPL-3.0**. YaP Link inherits that license. See [`LICENSE`](LICENSE)
and [`NOTICE`](NOTICE).

## Build / run

```bash
# From YaPcore root
./scripts/start-yap-link.sh

# Or manually
cd yap-link
./gradlew :velocity-proxy:shadowJar
java -jar proxy/build/libs/yap-link.jar
```

Default config is still `velocity.toml` (Velocity-compatible). Point backends at Folia with
modern forwarding — see [`../docs/YAP_LINK.md`](../docs/YAP_LINK.md) and
[`../docs/VELOCITY.md`](../docs/VELOCITY.md).

## Relation to YaPcore

| Process | Role |
|---------|------|
| **YaP Link** (`yap-link.jar`) | Public proxy (this tree) |
| **YaPcore + Folia** | Game backends (`velocity-enabled=true`) |
| **yap-link-lite/** | Archived experimental thin proxy — not product |

## Upstream

Based on PaperMC Velocity `dev/3.0.0`. YapLabs will carry Folia/product patches here.
