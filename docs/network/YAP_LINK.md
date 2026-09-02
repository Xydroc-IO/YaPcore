# YaP Link

**YaP Link** is YapLabs’ **native network proxy** — a first-party Netty implementation in
[`yap-first-party/link/native/`](../../yap-first-party/link/native/) (`com.yapcore.link.*`), built to **Velocity-class**
feature parity in phases.

It is **not** a Velocity fork. See the full roadmap:
**[YAP_LINK_NATIVE.md](YAP_LINK_NATIVE.md)**.

## Architecture

```text
Players → YaP Link (:25565) → YaPcore Via (:25566) → YaP-Folia (:25567)
              ↑ yap-link.jar              ↑ protocol edge         ↑ game (fork)
```

| Path | Role |
|------|------|
| [`yap-first-party/link/native/`](../../yap-first-party/link/native/) | **Product proxy** (native) |
| [`yap-first-party/link/protocol/`](../../yap-first-party/link/protocol/) | Shared JE frame / zlib / forwarding wire |
| [`yap-first-party/link/plugins/`](../../yap-first-party/link/plugins/) | Native Link plugins |

**Join wire (important):** Outbound packets use a **single** handler
(`McOutboundPacketEncoder`) that applies optional zlib + length framing — the same
idea as chassis `ViaProxyPipeline.writeFramed`. Do **not** stack a Netty
`MessageToMessageEncoder` compress handler before a frame encoder; that path can
emit `VarInt(0)` frames and vanilla clients die with
`CorruptedFrameException: Frame length cannot be zero`.

**GUI / process jar:** `LinkProcessManager` prefers repo-root `yap-link.jar`, then
the Gradle shadow jar. After Link protocol changes run
`gradle :yap-link-native:shadowJar` and copy/publish so the root jar stays current
(`gradle publishReleasesFolder`).

## Folia backend (YaP-Folia fork)

Product default: **`folia-jar-source=build`** (YaP-Folia under `lib/yap-folia-*.jar`),
not stock Fill Folia. Build with `./scripts/build-yap-folia.sh`.

Two forwarding modes:

1. **Velocity modern forwarding** (recommended for production multi-proxy):

```properties
velocity-enabled=true
velocity-secret-file=forwarding.secret
velocity-online-mode=false   # match Link online-mode
velocity-bind-localhost=true
online-mode=false
game-authority=folia
```

Same `forwarding.secret` next to Link’s `link.properties`.

2. **Forwarding disabled** (common local Link → Via → YaP-Folia): Folia
`proxies.velocity.enabled=false`. Link still bridges on Login Success; it does
**not** require `velocity:player_info` when the backend never asks for it.

## Run

```bash
# Enable Velocity modern forwarding on the Folia backend (shared secret)
./scripts/setup-velocity-forwarding.sh --enable
./scripts/start.sh              # Folia game
./scripts/start-yap-link.sh     # native YaP Link
# players → :25565
```

Build manually:

```bash
gradle :yap-link-native:shadowJar
# GUI resolves root jar first — keep it in sync:
cp -f yap-first-party/link/native/build/libs/yap-link.jar yap-link.jar
java -jar yap-link.jar --home link-data
```

Config: **`link.properties`** in link home (not `velocity.toml`). Seeded on first start.

Smoke: 

Requires `link-embed=false` (default). When `link-embed=true`, Link starts in-process at JVM boot and start/stop controls are disabled.

## Web dashboard

Open `http://127.0.0.1:8080/` → **Link** tab — start/stop, full proxy settings (backends, try order, forced hosts), dedicated console (SSE), backend forwarding setup. See [WEB_DASHBOARD.md](../ops/WEB_DASHBOARD.md).

## Control GUI (Swing)

Open `./scripts/gui.sh` → **Link** tab (same controls as web dashboard):

- **Start Link / Stop Link** — runs `yap-link.jar` as its own JVM (like Velocity)
- **Configure…** — backends (hub, survival, …), try order, forced hosts, bind/MOTD
- **Link console** — `help`, `reload`, `list`, `servers`, `say …`, `stop`
- **Enable backend forwarding** — runs `setup-velocity-forwarding.sh --enable`

Requires `link-embed=false` (default).

## TAB cross-server sync (v1.1)

When running multiple Folia backends behind Link, install **`yap-link-tab-bridge`** on the proxy
and **`yap-tab`** on each backend. Per-backend config (`plugins/YaPTab/config.yml`):

```yaml
network-sync:
  enabled: true
  server-id: hub          # unique per backend — hub, survival, etc.
  heartbeat-seconds: 30   # republish header/footer/sidebar to peers
```

- Backends publish on enable, reload, player join, and heartbeat.
- Sending `CLEAR|<server-id>` drops stale overlay when a backend stops.
- Admin: `/yaptab sync` forces a publish; `/yaptab reload` reloads and syncs.

Smoke: 

## Bedrock / Geyser

Bedrock UDP edge at Link is **shipped** (`BedrockUdpForwarder`, phase 4/6) but
**off by default** (`bedrock-enabled=false` in `link.properties`). For single-box
crossplay, prefer chassis dual-stack on the YaP-Folia backend. For BE-heavy multi-backend
networks, enable Link Bedrock bind or use stock Velocity as a stand-in.

## Related

- [YAP_LINK_NATIVE.md](YAP_LINK_NATIVE.md) — phased Velocity-class parity plan
- [VELOCITY.md](VELOCITY.md) — modern forwarding on Folia backends
- [yap-first-party/link/plugins/](../../yap-first-party/link/plugins/) — chat-bridge, mod-sync, server-selector
