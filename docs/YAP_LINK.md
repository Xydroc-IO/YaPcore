# YaP Link

**YaP Link** is YapLabs’ **complete Velocity-class proxy**: a first-party fork of
[PaperMC Velocity](https://github.com/PaperMC/Velocity) living in [`yap-link/`](../yap-link/).

It is **not** a partial shim. You get the full Velocity feature set (modern forwarding,
online-mode, compression, `/server`, plugin API, ping passthrough, forced hosts, etc.),
branded as YaP Link. Packages stay `com.velocitypowered.*` so Velocity plugins load.

GPL-3.0 (upstream). See `yap-link/LICENSE` and `yap-link/NOTICE`.

## Architecture

```text
Players → YaP Link (yap-link.jar) → Folia / Paper backends
              ↑ full Velocity fork     ↑ velocity-enabled=true
```

| Path | Role |
|------|------|
| [`yap-link/`](../yap-link/) | **Product proxy** (full fork) |
| [`yap-link-lite/`](../yap-link-lite/) | Archived experimental thin proxy — do not use for production |

## Folia backend

```properties
velocity-enabled=true
velocity-secret-file=forwarding.secret
velocity-online-mode=false   # match Link online-mode
velocity-bind-localhost=true
online-mode=false
game-authority=folia
```

Same `forwarding.secret` next to Link’s `velocity.toml`.

## Run

```bash
./scripts/setup-velocity-forwarding.sh --enable
./scripts/start.sh              # Folia game
./scripts/start-yap-link.sh     # full YaP Link
# players → :25565
```

Manual build:

```bash
cd yap-link && ./gradlew :velocity-proxy:shadowJar
java -jar proxy/build/libs/yap-link.jar
```

Config file remains **`velocity.toml`** (Velocity-compatible). Example seed is written on first
`start-yap-link.sh` into `link-data/`.

## Bedrock / Geyser

Put **Geyser + Floodgate on YaP Link** (as Velocity plugins), same as a normal Velocity network.
Backend uses `yap-floodgate` / modern forwarding — see [VELOCITY.md](VELOCITY.md).
