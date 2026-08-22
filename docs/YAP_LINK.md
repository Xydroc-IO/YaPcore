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

Requires `link-embed=false` (default). When `link-embed=true`, Link starts in-process at JVM boot and start/stop controls are disabled.

## Web dashboard

Open `http://127.0.0.1:8080/` → **Link** tab — start/stop, full proxy settings (backends, try order, forced hosts), dedicated console (SSE), backend forwarding setup. See [WEB_DASHBOARD.md](WEB_DASHBOARD.md).

## Control GUI (Swing)

Open `./scripts/gui.sh` → **Link** tab (same controls as web dashboard):

- **Start Link / Stop Link** — runs `yap-link.jar` as its own JVM (like Velocity)
- **Configure…** — backends (hub, survival, …), try order, forced hosts, bind/MOTD
- **Link console** — `help`, `reload`, `list`, `servers`, `say …`, `stop`
- **Enable backend forwarding** — runs `setup-velocity-forwarding.sh --enable`

Requires `link-embed=false` (default).

## Bedrock / Geyser

Phase 4 — Bedrock UDP routing at Link. Until then, use backend dual-stack or stock Velocity
as a stand-in for BE-heavy networks.

## Related

- [YAP_LINK_NATIVE.md](YAP_LINK_NATIVE.md) — phased Velocity-class parity plan
- [VELOCITY.md](VELOCITY.md) — modern forwarding on Folia backends
- [yap-first-party/link/plugins/](../../yap-first-party/link/plugins/) — chat-bridge, mod-sync, server-selector
