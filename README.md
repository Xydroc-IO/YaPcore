# YaPcore

<p align="center">
  <img src="branding/yapcore-banner.png" alt="YaPcore" width="100%"/>
</p>

<p align="center">
  <strong>Production Folia network stack</strong> — regionized game tick, dual-stack Java + Bedrock,<br/>
  first-party plugins, native proxy, and operator tooling in one product line.
</p>

<p align="center">
  <a href="https://github.com/Xydroc-IO/YaPcore/releases/latest"><img alt="Release" src="https://img.shields.io/github/v/release/Xydroc-IO/YaPcore?label=release&color=2ea44f"/></a>
  <a href="LICENSE"><img alt="License" src="https://img.shields.io/badge/license-GPLv3-blue.svg"/></a>
  <img alt="Minecraft" src="https://img.shields.io/badge/Minecraft-26.2-brightgreen"/>
  <img alt="Java" src="https://img.shields.io/badge/Java-25%2B-orange"/>
  <a href="docs/whitepaper/YAPCORE_WHITEPAPER.md"><img alt="Docs" src="https://img.shields.io/badge/docs-whitepaper-0A66C2"/></a>
</p>

| | |
|--|--|
| **Install** | [Quick Start](docs/start/QUICK_START.md) · [Release packages](https://github.com/Xydroc-IO/YaPcore/releases/latest) |
| **Operators** | [Wiki](docs/WIKI.md) · [Defaults](docs/start/DEFAULTS.md) · [Secrets](docs/start/SECRETS.md) |
| **Architecture** | [Whitepaper](docs/whitepaper/YAPCORE_WHITEPAPER.md) · [Plain English](docs/whitepaper/YAPCORE_WHITEPAPER_PLAIN_ENGLISH.md) |
| **Performance** | [REAL_GAINS](docs/folia/REAL_GAINS.md) · [YAP_FOLIA_SOAK](docs/folia/YAP_FOLIA_SOAK.md) · [Canvas parity](docs/folia/CANVAS_PARITY.md) |
| **Network** | [Crossplay](docs/network/CROSSPLAY.md) · [YaP Link](docs/network/YAP_LINK.md) · [Clients & packs](docs/network/CLIENTS_AND_PACKS.md) |
| **Legal** | [GPLv3](LICENSE) · [Licensing](docs/start/LICENSING.md) · [Privacy](docs/start/PRIVACY_POLICY.md) · [Terms](docs/start/TERMS_OF_USE.md) |

> Independent project. Not affiliated with Mojang Studios, Microsoft, PaperMC, ViaVersion, or GeyserMC.

---

## Product

YaPcore is a **shippable Minecraft network product**, not a plugin mashup. Game authority runs on **YaP-Folia** (managed Folia 26.2 fork). The edge, dual-stack protocol path, web dashboard, and operator GUI sit on **YapEngine**. Multi-backend routing uses **YaP Link**. CORE + NETWORK plugins are first-party — perms, chat, moderation, essentials, playerdata, packs, claims, and more — so operators are not assembling a third-party jar list for a normal SMP or network.

| Capability | What you get |
|------------|----------------|
| **Regionized + parallel ticks** | YaP-Folia regions + **subregion partition** (parallel shards when hot) — ship knobs on by default |
| **Microtick / µs chassis** | MSPT-gated **microtick** Mob AI budgets on Folia; YapEngine orders bridge/plugin work in **µs** (`SequenceToken`) — not a single-thread MSPT claim |
| **Crossplay** | Java (1.20.2+) + Bedrock on one listen path — no Via\* / Geyser jars required |
| **Network** | YaP Link native proxy, Floodgate-class identity, dual-stack gateway |
| **Plugin suite** | First-party CORE + NETWORK; full box GAMEPLAY = skills / stacker / knobs / disasters |
| **Ops** | Web dashboard (`:8080`), Swing GUI, seed defaults, MariaDB / Postgres / SQLite paths |
| **Packs** | `yapcore-default.zip` via GitHub Releases CDN (`/releases/latest/download/…`) |
| **Clients (optional)** | Fabric mods: visuals (Sodium + Iris + shaders), bag UI, ultrawide — vanilla/Bedrock still join |

Version line: **1.0.0.0** · YaP Link **0.6.0-phase6** · YaP-Folia **26.2** — see [RELEASE_NOTES.md](docs/start/RELEASE_NOTES.md).

### Parallel ticks & microtick (why it’s not “stock Folia”)

Classic Paper/Purpur keep one main world tick. **YaP-Folia** runs **regionized parallel ticks**, and the product ship profile adds:

| Knob (defaults) | Role |
|-----------------|------|
| `folia-subregion-partition=true` | Split hot regions into **parallel subregion shards** when geometry allows |
| `folia-microtick-budget-ms=8` | Soft deadline for Mob AI on hot regions (MSPT-gated with entity budget) |
| `folia-entity-tick-budget=400` | Cap Mob AI ticks per region when hot (≥12 ms MSPT) — never players / TNT / vehicles / bosses |
| `folia-hopper-tick-budget=64` | Soft-defer excess hopper transfers |

**YapEngine** (edge/chassis) sequences bridge and plugin work with **µs-resolution** `SequenceToken`s so I/O and menus stay ordered without owning the world heartbeat.

Citeable MSPT vs stock Folia / Canvas (ship knobs disclosed): [REAL_GAINS.md](docs/folia/REAL_GAINS.md) · soak profile: [YAP_FOLIA_SOAK.md](docs/folia/YAP_FOLIA_SOAK.md). We do **not** claim single-thread Paper MSPT victory — [PAPER_PURPUR_SCALE.md](docs/folia/PAPER_PURPUR_SCALE.md).

---

## Get started

### Operators — download a release

1. Take **linux** or **windows** from [GitHub Releases](https://github.com/Xydroc-IO/YaPcore/releases/latest).
2. Unzip → `yapcore-release/linux` (or `windows`).
3. Configure secrets ([SECRETS.md](docs/start/SECRETS.md)), then launch:

```bash
# Linux
./start.sh --fg

# Windows
start.cmd -Fg
```

Join `127.0.0.1:25566` (Java + Bedrock). Dashboard: `http://127.0.0.1:8080/`

Also on the release: network / gameplay / addons suites, `yapcore-default.zip`, and optional Fabric client jars. Layout and rebuild: [RELEASES.md](docs/start/RELEASES.md).

### Developers — build from source

Requires **Java 25+**.

```bash
git clone https://github.com/Xydroc-IO/YaPcore.git && cd YaPcore
chmod +x scripts/*.sh scripts/db/*.sh scripts/yapctl
./scripts/build-yap-folia.sh          # → lib/yap-folia-26.2.jar
gradle installProductDefaults shadowJar
./scripts/seed-defaults.sh
./scripts/db/ensure-db.sh --server-id lobby
./scripts/start.sh --fg
```

Local release trees (gitignored):

```bash
./scripts/build-yap-folia.sh
gradle publishReleasesFolder
# → releases/1.0.0.0/yapcore-release-{linux,windows}.zip
```

Slim CORE+NETWORK is the **default** (`yapGameplay=false`). Opt in to GAMEPLAY
(skills / stacker / knobs / disasters): `gradle assembleRelease -PyapGameplay=true`.

---

## Architecture

```text
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│  YaP Link       │────▶│  YapEngine       │────▶│  YaP-Folia      │
│  multi-backend  │     │  edge · dual-stack│     │  regionized tick│
│  proxy          │     │  dashboard · GUI  │     │  game authority │
└─────────────────┘     └──────────────────┘     └─────────────────┘
                                  │
                                  ▼
                        first-party plugins/
                        (CORE · NETWORK · GAMEPLAY)
```

| Layer | Role |
|-------|------|
| **YaP-Folia** | Game tick — build with `./scripts/build-yap-folia.sh` |
| **YapEngine** | Edge networking, dual-stack, I/O, dashboard, Swing GUI |
| **YaP Link** | Multi-backend proxy — [YAP_LINK.md](docs/network/YAP_LINK.md) |
| **Plugins** | First-party stack under [`yap-first-party/`](yap-first-party/README.md) |

Default product path: `game-authority=folia`, `folia-jar-source=build`, dual-stack Java + Bedrock.

Deep dive: [YAPCORE_WHITEPAPER.md](docs/whitepaper/YAPCORE_WHITEPAPER.md).

---

## Shipped plugins (CORE + NETWORK)

| Jar | Role |
|-----|------|
| `yap-perms.jar` | Ranks, tracks, prefixes |
| `yap-chat.jar` | Channels, PM, filter, staff chat |
| `yap-moderation.jar` | Ban / mute / warn / kick + history |
| `yap-essentials.jar` | Spawn, tpa, fly, vanish |
| `yap-playerdata.jar` | Cross-server data, economy, backpacks |
| `yap-db.jar` | Shared SQL pool (MariaDB / Postgres / SQLite) |
| `yap-packs.jar` | Multi-active resource packs |
| `yap-floodgate.jar` | Bedrock identity |
| `yap-folia-bridge.jar` | Folia scheduling bridge |

Full inventory: [PLUGINS.md](docs/plugins/PLUGINS.md) · [plugins/README.md](plugins/README.md).  
Optional Fabric clients: [`client/`](client/).

---

## Documentation

Documentation is **Markdown in-repo** (`docs/`). Generated PDFs and office dumps are gitignored — do not commit them.

| Audience | Start here |
|----------|------------|
| **Operators** | [QUICK_START](docs/start/QUICK_START.md) → [WIKI](docs/WIKI.md) · [DEFAULTS](docs/start/DEFAULTS.md) |
| **Commands / perms** | [COMMANDS](docs/ops/COMMANDS.md) · [PERMISSIONS](docs/ops/PERMISSIONS.md) · [Dashboard](docs/ops/WEB_DASHBOARD.md) |
| **Network / packs** | [CROSSPLAY](docs/network/CROSSPLAY.md) · [CLIENTS_AND_PACKS](docs/network/CLIENTS_AND_PACKS.md) |
| **Folia / cite** | [YAP_FOLIA_SOAK](docs/folia/YAP_FOLIA_SOAK.md) · [REAL_GAINS](docs/folia/REAL_GAINS.md) · [CANVAS_PARITY](docs/folia/CANVAS_PARITY.md) |
| **Public edge** | [EDGE_HARDEN](docs/network/EDGE_HARDEN.md) · [SECRETS](docs/start/SECRETS.md) |
| **Architecture** | [Whitepaper](docs/whitepaper/YAPCORE_WHITEPAPER.md) |
| **Contributors** | [CONTRIBUTING](CONTRIBUTING.md) · [scripts/README](scripts/README.md) |

---

## Security

Report vulnerabilities privately — do **not** open a public issue for RCE, auth bypass, or pack-HTTP exposure. See [SECURITY.md](SECURITY.md).

Operators: keep secrets out of git ([SECRETS.md](docs/start/SECRETS.md)); put game and pack ports behind intentional firewall / nginx edges ([EDGE_HARDEN.md](docs/network/EDGE_HARDEN.md)).

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). One logical change per PR; keep build outputs, worlds, logs, secrets, and generated PDFs out of the tree.

---

## License

**YaPcore** first-party source is **[GNU GPLv3](LICENSE)** (same family as Paper / Folia).

Minecraft (Mojang EULA), Faithful, and other bundled assets have separate terms — [LICENSING.md](docs/start/LICENSING.md). Branding marks: [branding/README.md](branding/README.md).
