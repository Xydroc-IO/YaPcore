# YaP Link

**YaP Link** is YapLabs’ **native network proxy** — a first-party Netty implementation in
[`yap-first-party/link/native/`](../../yap-first-party/link/native/) (`com.yapcore.link.*`), built to **Velocity-class**
feature parity in phases.

For **multi-server fleet control** (instances, migrate lobby, remote agents, deploy), see **[NETWORKING.md](NETWORKING.md)**.

It is **not** a Velocity fork. See the full roadmap:
**[YAP_LINK.md](YAP_LINK.md)**.

## Architecture

```text
Players → YaP Link (:25565) → YaPcore Via (:25566) → YaP-Folia (:25567)
              ↑ yap-link.jar              ↑ protocol edge         ↑ game (fork)
```

| Path | Role |
|------|------|
| [`yap-first-party/link/native/`](../../yap-first-party/link/native/) | **Product proxy** (native) |
| [`yap-first-party/link/bedrock/`](../../yap-first-party/link/bedrock/) | **Bedrock product path** (`bedrock-mode=native`) — RakNet + Floodgate + JE downstream translators |
| [`yap-first-party/link/protocol/`](../../yap-first-party/link/protocol/) | Shared JE frame / zlib / forwarding wire |
| [`yap-first-party/link/plugins/`](../../yap-first-party/link/plugins/) | Native Link plugins |

**Bedrock:** default `bedrock-mode=native` — Link owns Bedrock UDP (`BedrockSessionHost`); chassis `bedrock-enabled=false`. See [CROSSPLAY.md](CROSSPLAY.md) and [YAP_LINK.md](YAP_LINK.md). Use `forwarder` for legacy Link→chassis UDP, or `geyser-backup` for Standalone.

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
velocity-online-mode=false   # match Link; set true when Link online-mode=true
velocity-bind-localhost=true
online-mode=false            # Folia stays offline; Link handles auth / skin forwarding
game-authority=folia
```

**Skins:** Link `online-mode=false` still looks up Mojang textures by username and forwards
them via modern `velocity:player_info`. For full premium auth + skins, set Link
`online-mode=true` and matching `velocity-online-mode=true`.
Same `forwarding.secret` next to Link’s `link.properties`.

2. **Forwarding disabled** (`velocity-enabled=false`): Folia
`proxies.velocity.enabled=false`. Use for direct chassis `:25566` joins without Link.
Link still bridges on Login Success but skins will not apply. Product **default is enabled**.

## Run

```bash
# Product default: velocity-enabled=true + forwarding.secret (skins).
# Optional re-seed / toggle:
./scripts/setup-velocity-forwarding.sh          # enable (default)
./scripts/setup-velocity-forwarding.sh --disable  # direct joins without Link
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
- **Backend forwarding** — ON/OFF toggle (`setup-velocity-forwarding.sh --enable|--disable`). Restart Folia after changing.

`gui.sh` **does not rebuild** by default (fast open). Pass `--build` after chassis/GUI source changes.

### MOTD player max (aggregate)

With `aggregate-player-count=true` (fleet sync default), the JE/Bedrock ping **max** is the
**sum of slot caps from backends that are UP right now** (lobby 250 alone → `0/250`;
lobby+survival → `0/500`). Stopping survival must shrink the advertised max.
`link.properties` `max-players` is only a **ceiling**, never a floor that keeps `500` alive
after a backend is down.

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

- [YAP_LINK.md](YAP_LINK.md) — phased Velocity-class parity plan
- [YAP_LINK.md](YAP_LINK.md) — modern forwarding on Folia backends
- [yap-first-party/link/plugins/](../../yap-first-party/link/plugins/) — chat-bridge, mod-sync, server-selector


---

## Native Link setup

**Product decision:** YaP Link is a **first-party Netty proxy** (`com.yapcore.link.*`), evolved from
[`yap-first-party/link/native/`](../../yap-first-party/link/native/). It is **not** a Velocity fork.

**Status:** **Phases 0–6** in native Link (`0.6.0-phase6`).

See also: [`yap-first-party/link/api/`](../../yap-first-party/link/api/) · [`yap-first-party/link/protocol/`](../../yap-first-party/link/protocol/) ·
[`yap-first-party/link/plugins/`](../../yap-first-party/link/plugins/)

---

## Velocity parity matrix

| Feature | Velocity | YaP Link | Phase |
|---------|----------|----------|-------|
| Modern player-info forwarding | ✓ | ✓ | **0** ✓ |
| Multi-backend + try order | ✓ | ✓ `link.properties` / `link.toml` | **0** ✓ |
| Online / offline login | ✓ | ✓ | **0** ✓ |
| Compression bridge | ✓ | ✓ | **0** ✓ |
| `/server` + Transfer reconnect | ✓ | ✓ | **0** ✓ |
| `link.properties` + `link.toml` | — | ✓ | **0** ✓ |
| Ping **passthrough** | ✓ | ✓ cached backend probe | **1** ✓ |
| Forced hosts | ✓ | ✓ `forced-host.<host>=server` | **1** ✓ |
| Backend health + try failover | ✓ | ✓ `BackendMonitor` | **1** ✓ |
| Connect / login / read timeouts | ✓ | ✓ | **1** ✓ |
| Play-phase system chat | ✓ | ✓ `PlayChat` | **1** ✓ |
| Aggregate player count in ping | ✓ | ✓ `aggregate-player-count` | **2** ✓ |
| Cross-server chat relay | plugins | ✓ `ChatRelay` + `say` console | **2** ✓ |
| Config reload | ✓ | ✓ console `reload` | **2** ✓ |
| **YaP Link plugin API** | Velocity API | ✓ `yap-link-api` | **3** ✓ |
| chat-bridge / mod-sync / server-selector | Velocity plugins | ✓ `yap-first-party/link/plugins/` | **3** ✓ |
| Bedrock UDP edge + per-backend routing | Geyser on proxy | ✓ `BedrockUdpForwarder` | **4** ✓ |
| Floodgate key forwarding | Floodgate on proxy | ✓ `floodgate-key.pem` | **4** ✓ |
| Metrics hooks | partial | ✓ `LinkMetrics` + `/metrics` | **5** ✓ / **Edge** ✓ |
| Connect / handshake rate limit | plugins | ✓ per-IP (default ON) | **Edge** ✓ |
| `link-embed` in YaPcore | — | ✓ `link-embed=true` | **5** ✓ |
| Release bundle | — | ✓ `yap-link.jar` in `assembleRelease` | **5** ✓ |
| Velocity fork retired | — | ✓ archived | **5** ✓ |
| Play plugin-message relay (`yap:chat`) | plugins | ✓ `PluginMessagePackets` | **6** ✓ |
| BungeeCord `Connect` / `ConnectOther` (portal plugins) | plugins | ✓ `server-selector` + HANDLED swallow | **6** ✓ |
| Two-backend probe + chat relay | — | ✓ | **6** ✓ |
| Bedrock UDP forward to backend | — | ✓ | **6** ✓ |

---

## Phase 6 — Hardening

| Task | Gate |
|------|------|
| Play-phase `PluginMessageEvent` for registered channels | Unit tests + chat-bridge relay |
| Multi-backend probe + `say` chat relay |  |
| Bedrock UDP datagram forward to backend |  |

---

## Config

**Home:** `link-data/` (or `--home`). Prefer **`link.toml`** or **`link.properties`**.

```properties
bind=0.0.0.0:25565
motd=YaP Link
servers.lobby=127.0.0.1:25566
servers.survival=127.0.0.1:25567
servers.lobby.bedrock=127.0.0.1:19132
try=lobby,survival
ping-passthrough=true
plugins-enabled=false
# First run: ./scripts/start-yap-link.sh seeds plugins-enabled=true + plugin jars
floodgate-key-file=floodgate-key.pem
bedrock-enabled=false
bedrock-bind=0.0.0.0:19132
bedrock-backend=127.0.0.1:19132
```

Plugins load from `link-data/plugins/*.jar` with `link-plugin.json` descriptors.

---

## Build & run

```bash
gradle :yap-link-native:shadowJar
gradle :yap-link-plugin-chat-bridge:installIntoLinkPlugins   # optional
./scripts/start-yap-link.sh
```

**Embedded Link (dev / single-box):** `config/server.properties`:

```properties
link-embed=true
link-embed-home=link-data
```

Requires `yap-link.jar` on the classpath alongside `yapcore.jar`.

**Release:** `gradle assembleRelease` ships `yapcore.jar`, `yap-link.jar`, and `link-data/plugins/`.

**Console commands:** `help` · `reload` · `list` · `servers` · `say <msg>` · `stop`

---

## Architecture

```text
Players (JE TCP / optional BE UDP)
        │
        ▼
┌───────────────────────────────────────┐
│  YaP Link (native JVM) :25565         │
│  yap-link.jar · link.properties       │
│  LinkPluginManager · FloodgateForwarder│
│  BedrockUdpForwarder (per-backend)    │
└───────────────┬───────────────────────┘
                │ optional velocity:player_info
                ▼
┌───────────────────────────────────────┐
│  YaPcore Via edge :25566              │
└───────────────┬───────────────────────┘
                ▼
┌───────────────────────────────────────┐
│  YaP-Folia game :25567                │
│  (folia-jar-source=build)             │
└───────────────────────────────────────┘
```

Shared wire code: [`yap-first-party/link/protocol/`](../../yap-first-party/link/protocol/)
(`McCodec`, `McFrameCodec` inbound, `McOutboundPacketEncoder` outbound zlib+frame,
`McCompressionCodec` inbound decompress + `wrapOutbound`, modern forwarding, Floodgate).

**Outbound rule:** one handler for zlib+length (`McOutboundPacketEncoder`). Stacking
compress `MessageToMessageEncoder` + frame encoder caused vanilla
`CorruptedFrameException: Frame length cannot be zero` after Set Compression.

**Login Success:** if the backend never sends `velocity:player_info` (Folia velocity
disabled), Link still bridges — forwarding is optional unless the backend requests it.

**Jar the GUI starts:** repo-root `yap-link.jar` (preferred) or
`yap-first-party/link/native/build/libs/yap-link.jar`. Always republish/copy after
protocol fixes so operators are not on a stale root jar.

---

## Bedrock / first-party Geyser port

Bedrock UDP at Link (`BedrockUdpForwarder`) is **on** for first-party join:
`bedrock-mode=first-party` (default), `bedrock-enabled=true`, binds `:25565` + `:19132` → chassis `:25566`.

Join owner: chassis **`YapGeyserSession`** (`com.yapcore.crossplay.bedrock.geyserport`) —
a native port of [GeyserMC/Geyser](https://github.com/GeyserMC/Geyser) join path
(reference tree: `vendor/geyser-ref/`). **No GeyserMC jar on yap-folia.**

**Phone / Bedrock:** LAN `10.0.0.215:19132` or public `host:19132` (UDP) → YaP Link → chassis.
**Java Edition:** YaP Link `:25565` TCP → chassis → yap-folia `:25567` (modern forwarding).

### Pre-start Bedrock path toggle (GUI / dashboard)

On the **Link** tab (or `./scripts/set-bedrock-mode.sh`):

| Mode | Who owns `:19132` |
|------|-------------------|
| **First-party (YaP)** (default) | Link `BedrockUdpForwarder` → chassis `YapGeyserSession` |
| **Geyser backup** | `Geyser-Standalone` in `link-data/geyser/` → remote Link `:25565` |

Stop servers → set mode → Save Bedrock path → start. Mode change is rejected while the stack is running.

Geyser-Velocity jars do **not** load in `link-data/plugins/` (LinkPlugin API only). Backup uses Standalone.
Stage `link-data/geyser/Geyser-Standalone.jar` before selecting backup. Folia keeps **`yap-floodgate.jar`**.

See also [CROSSPLAY.md](CROSSPLAY.md) · [CROSSPLAY.md](../network/CROSSPLAY.md).

---

## Agent handoff (Phases 0–2 ↔ 3–5)

**For the 0–2 agent:**

| Rule | Detail |
|------|--------|
| **Plugins default OFF in code** | `LinkConfig.applyDefaults()` → `plugins-enabled=false`. Unit tests and bare `LinkConfig.load()` stay plugin-free until config opts in. |
| **First run / release turns plugins ON** | `./scripts/start-yap-link.sh` seeds `link.properties` with `plugins-enabled=true`, builds `yap-link-plugin-*` jars into `link-data/plugins/`. `assembleRelease` should mirror that seed. |
| **Bedrock ctor** | **`new BedrockUdpForwarder(LinkConfig)`** only — not the old 4-arg host/port stub. |
| **Plain chat** | Built-in **`ChatRelay`** (Phase 2) — serverbound play chat + console `say`. |
| **Backend `yap:chat`** | **`yap-link-plugin-chat-bridge`** when backends send on channel `yap:chat` and `plugins-enabled=true` (`PluginMessageEvent`). Do not duplicate in `ClientSession`. |

**`LinkServer` owns:** `LinkPluginManager`, `FloodgateForwarder`, `BedrockUdpForwarder(config)`, plus Phase 0–2 `BackendMonitor` / `ChatRelay` / `PlayerHub`.

Full play-phase plugin-message wire sniffing (all channels) is **optional future work** — not required for Phase 0–2 or for `yap:chat` when chat-bridge is loaded.

**Boundary:** Phase 0–2 stops at `ChatRelay`, `BackendMonitor`, `PlayChat`, console `reload`. Do not grow `ClientSession` into a second plugin platform.

---

## Related

- [NETWORKING.md](NETWORKING.md) · [NETWORKING.md](NETWORKING.md) — rate limits, Prometheus, public edge harden
- [YAP_LINK.md](YAP_LINK.md) — operator entry
- [YAP_LINK.md](YAP_LINK.md) — Folia backend forwarding
- [YAPCORE_WHITEPAPER.md](../whitepaper/YAPCORE_WHITEPAPER.md) — chassis vs Link process boundaries
