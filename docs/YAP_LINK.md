# YaP Link

**YaP Link** is YapLabs’ **native network proxy** — a first-party Netty implementation in
[`yap-first-party/link/native/`](../../yap-first-party/link/native/) (`com.yapcore.link.*`), built to **Velocity-class**
feature parity in phases.

It is **not** a Velocity fork. See the full roadmap:
**[YAP_LINK_NATIVE.md](YAP_LINK_NATIVE.md)**.

## Architecture

```text
Players → YaP Link (yap-link.jar) → Folia / YaPcore backend(s)
              ↑ native proxy              ↑ velocity-enabled=true
```

| Path | Role |
|------|------|
| [`yap-first-party/link/native/`](../../yap-first-party/link/native/) | **Product proxy** (native) |
| [`yap-first-party/link/plugins/`](../../yap-first-party/link/plugins/) | Native Link plugins |

## Folia backend

```properties
velocity-enabled=true
velocity-secret-file=forwarding.secret
velocity-online-mode=false   # match Link online-mode
velocity-bind-localhost=true
online-mode=false
game-authority=folia
```

Same `forwarding.secret` next to Link’s `link.properties`.

## Run

```bash
./scripts/setup-velocity-forwarding.sh --enable
./scripts/start.sh              # Folia game
./scripts/start-yap-link.sh     # native YaP Link
# players → :25565
```

Build manually:

```bash
gradle :yap-link-native:shadowJar
java -jar yap-first-party/link/native/build/libs/yap-link.jar --home link-data
```

Config: **`link.properties`** in link home (not `velocity.toml`). Seeded on first start.

Smoke: `./scripts/smoke-yap-link-folia.sh`

## Bedrock / Geyser

Phase 4 — Bedrock UDP routing at Link. Until then, use backend dual-stack or stock Velocity
as a stand-in for BE-heavy networks.

## Related

- [YAP_LINK_NATIVE.md](YAP_LINK_NATIVE.md) — phased Velocity-class parity plan
- [VELOCITY.md](VELOCITY.md) — modern forwarding on Folia backends
- [yap-first-party/link/plugins/](../../yap-first-party/link/plugins/) — chat-bridge, mod-sync, server-selector
