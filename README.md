# YaPcore

<p align="center">
  <img src="branding/yapcore-banner.png" alt="YaPcore" width="100%"/>
</p>

<p align="center">
  <strong>Production Folia network stack</strong> — Folia region ticks; a packed spawn splits into two ticking shards without a second clock,<br/>
  dual-stack Java + Bedrock, first-party plugins, native proxy, Bedrock-feel parity, and operator tooling.
</p>

<p align="center">
  <a href="https://github.com/Xydroc-IO/YaPcore/releases/tag/0.0.0.2"><img alt="Prerelease" src="https://img.shields.io/github/v/release/Xydroc-IO/YaPcore?include_prereleases&label=prerelease&color=e3b341"/></a>
  <a href="LICENSE"><img alt="License" src="https://img.shields.io/badge/license-GPLv3-blue.svg"/></a>
  <img alt="Minecraft" src="https://img.shields.io/badge/Minecraft-26.2-brightgreen"/>
  <img alt="Java" src="https://img.shields.io/badge/Java-25%2B-orange"/>
  <a href="docs/whitepaper/YAPCORE_WHITEPAPER.md"><img alt="Docs" src="https://img.shields.io/badge/docs-whitepaper-0A66C2"/></a>
  <a href="docs/start/AI_TRANSPARENCY.md"><img alt="AI transparency" src="https://img.shields.io/badge/AI-disclosed-6e40c9"/></a>
  <a href="https://discord.gg/BXbyQk88Da"><img alt="Discord" src="https://img.shields.io/badge/Discord-YaPcore-5865F2"/></a>
  <a href="https://ko-fi.com/xydroc"><img alt="Ko-fi" src="https://img.shields.io/badge/Ko--fi-support-ff5e5b"/></a>
</p>

| | |
|--|--|
| **Install** | [Quick Start](docs/start/QUICK_START.md) · [0.0.0.2 prerelease](https://github.com/Xydroc-IO/YaPcore/releases/tag/0.0.0.2) |
| **Operators** | [Wiki](docs/README.md) · [Defaults](docs/start/DEFAULTS.md) · [Secrets](docs/start/SECRETS.md) |
| **Architecture** | [Whitepaper](docs/whitepaper/YAPCORE_WHITEPAPER.md) |
| **Crossplay** | [Crossplay](docs/network/CROSSPLAY.md) · [Bedrock-feel](docs/product/BEDROCK_FEEL_PARITY.md) · [YaP Link](docs/network/YAP_LINK.md) |
| **Performance** | [YaP-Folia](docs/folia/YAP_FOLIA_PATCHES.md) |
| **Help / contribute** | [Community & support](#community--support) · [Discord](https://discord.gg/BXbyQk88Da) · [CONTRIBUTING](CONTRIBUTING.md) |
| **Legal** | [GPLv3](LICENSE) · [Licensing](docs/start/LICENSING.md) · [AI transparency](docs/start/AI_TRANSPARENCY.md) · [Privacy](docs/start/PRIVACY_POLICY.md) · [Terms](docs/start/TERMS_OF_USE.md) |

> Independent project. Not affiliated with Mojang Studios, Microsoft, PaperMC, ViaVersion, or GeyserMC.

---

## Product

YaPcore is a **shippable Minecraft network product**, not a plugin mashup. Game authority runs on **YaP-Folia** (managed Folia 26.2 fork). The edge, dual-stack protocol path, web dashboard, and operator GUI sit on **YapEngine**. Multi-backend routing uses **YaP Link** with a **first-party Bedrock join path** (no Geyser / Via\* jars on the product path). CORE + NETWORK plugins are first-party — perms, chat, moderation, essentials, playerdata, packs, claims, Tailor presence, and more — so operators are not assembling a third-party jar list for a normal SMP or network.

| Capability | What you get |
|------------|----------------|
| **Regionized ticks** | YaP-Folia regions. A packed spawn becomes two independent shards that both keep ticking entities — no YaP phase clock. Fullcite `20260919T165559Z` held 500 at 37.24 ms against stock `20260919T171015Z` ending at 119. Partition, carve, and the cut ship **on** — [YAP_FOLIA_PATCHES.md](docs/folia/YAP_FOLIA_PATCHES.md) |
| **Mob AI budgets / µs chassis** | MSPT-gated **AI time-slice** + entity/hopper budgets on Folia; YapEngine orders bridge/plugin work in **µs** (`SequenceToken`) |
| **Crossplay** | Java (1.20.2+) + Bedrock on one product story — Link-native Bedrock join by default; Bedrock form hubs for `/menu`, kits, ranks, admin |
| **Bedrock-feel parity** | Convert-verified skins / emotes / movement / catalog blocks — [BEDROCK_FEEL_PARITY.md](docs/product/BEDROCK_FEEL_PARITY.md) |
| **Network** | YaP Link native proxy (`0.6.0-phase6`), Floodgate-class identity, dual-stack gateway |
| **Plugin suite** | First-party CORE + NETWORK; **Items + QoL + Skills + Dungeons** on by default; opt-in GAMEPLAY (stacker / knobs / disasters); opt-in Factions + Conquest |
| **Ops** | Web dashboard (`:8080`), Swing GUI, seed defaults, MariaDB / Postgres / SQLite paths, shared messages + reload UX |
| **Packs** | `yapcore-default.zip` / `.mcpack` from the **0.0.0.2** GitHub prerelease (`/releases/download/0.0.0.2/…`) |
| **Clients (optional)** | Fabric: visuals, bag, staff, ultrawide, **yap-presence**, **yap-blocks** — vanilla/Bedrock still join when parity mode is off |

**Docs:** [QUICK_START.md](docs/start/QUICK_START.md) · [RELEASE_NOTES.md](docs/start/RELEASE_NOTES.md).

Version line: **0.0.0.2** · YaP Link **0.6.0-phase6** · YaP-Folia **26.2** — see [RELEASE_NOTES.md](docs/start/RELEASE_NOTES.md).

### AI assistance (disclosure)

YaPcore is developed with the assistance of **AI coding tools**. Human maintainers remain accountable for review, security, and release quality. Full statement: **[AI_TRANSPARENCY.md](docs/start/AI_TRANSPARENCY.md)**.

### Screenshots

Live captures from a local fleet (lobby + survival + YaP Link). In-game / Bedrock shots welcome as PRs under [`docs/images/readme/`](docs/images/readme/).

<p align="center">
  <img src="docs/images/readme/swing-fleet-home.png" alt="YaPcore Swing GUI — fleet home with Link, lobby, and survival" width="90%"/>
  <br/><em>Desktop Control Panel — fleet rail, per-server plugins, Start / Stop / Restart</em>
</p>

<p align="center">
  <img src="docs/images/readme/web-dashboard.png" alt="YaPcore web dashboard — live health and Bedrock-feel controls" width="90%"/>
  <br/><em>Web dashboard (:8080) — health cards, Bedrock-feel parity, network status</em>
</p>

<p align="center">
  <img src="docs/images/readme/web-fleet.png" alt="YaPcore web Fleet page — Link edge and multi-server controls" width="90%"/>
  <br/><em>Fleet page — YaP Link join edge + multi-backend controls</em>
</p>

### Capacity (YaP-Folia)

Honest product bars — not “unlimited players.” Scale is **regionized + multi-backend**, not one mega thread.

| Bar | Players | Meaning |
|-----|---------|---------|
| **Citeable packed spawn** | **500 held** | `20260919T165559Z`: 500 at both ends, busiest region 37.24 ms, against stock `20260919T171015Z` ending at 119 with the busiest region at 65.03 ms |
| **Join verified** | **100 / 200** bots | Connection / routing checked; not a free MSPT blank check |
| **Network scale** | Multi-backend via **YaP Link** | Split worlds/lobbies across YaP-Folia jars; proxy fronts the fleet |
| **250 keepalive** | **Hold only** | Not a citeable ship claim — capacity testing, not marketing |

Practical SMP on one backend: tens to ~100 concurrent actives with ship knobs, LagGuard, and sane farms. Past that, add backends or tighten density knobs — [YAP_FOLIA_PATCHES.md](docs/folia/YAP_FOLIA_PATCHES.md) · [TUNE.md](docs/ops/TUNE.md).

### Parallel ticks (why it’s not “stock Folia”)

Classic Paper/Purpur keep one main world tick. Upstream Folia already regionizes: **one tick thread owns one region**. Stock Folia will not split a packed spawn and keep both sides entity-ticking. YaP-Folia carves an empty corridor, then force-partitions along that cut (`0061`–`0079`). Both shards stay on the Folia regionizer — no second clock.

That split is the product jar. Fullcite `20260919T165559Z` on `76aeaf3fefcf34bc9e80f441d0419df7` held 500 players at both ends, TNT 2400, hoppers 770, 32 villagers, fuse drop 808.5, and logged `YaP force-partition region #0 into 2 shards`. The busiest region was 37.24 ms. Stock on the same scene (`20260919T171015Z`) started at 500 and ended at 119, busiest region 65.03 ms. Those 119 left because the encoder ran out of direct memory, not because a watchdog fired. `0073`–`0079` keep spawn search off the cut, including chunk X=−1, and finish login without a configuration keepalive after the client is in play. Aligned microticks (`0026`–`0030`) stay optional phase tagging. Lab contiguous-strip check: `./scripts/folia/smoke-contiguous-bar.sh`.

| Knob (defaults) | Role |
|-----------------|------|
| `folia-subregion-partition=true` | Split hot regions into **parallel Folia shards** when an empty-buffer cut exists |
| `folia-subregion-carve=true` | Unload an empty corridor before the cut (`0018`) |
| `folia-regionizer-cut=true` | Native cut + ticket clamp so a packed spawn hole holds (`0041`) |
| `folia-grid-exponent=3` | 8-chunk sections — a VD=10 spawn has enough atoms to cut |
| `folia-aligned-microticks=true` | Optional micro/sub-tick phases + per-world waves — not the regionizer clock |
| `folia-micro-phases=4` | Phase count when aligned microticks on |
| `folia-tick-wave-max-wait-ms=2` | Soft per-world barrier max wait (must be &gt; 0) |
| `folia-physics-substeps=true` | N-step travel/move inside one tick (combat/feel; plugin tick stays 20 TPS) |
| `folia-microtick-budget-ms=8` | Soft **Mob AI time-slice** on hot regions (MSPT-gated; not a finer clock) |
| `folia-entity-tick-budget=400` | Cap Mob AI ticks per region when hot (≥12 ms MSPT) — never players / TNT / vehicles / bosses |
| `folia-hopper-tick-budget=64` | Soft-defer excess hopper transfers |

**Capacity in one hot area** comes from **real Folia shards after a legal cut** + budgets — not from spinning a faster world clock. Aligned phases are optional coherence; physics sub-steps improve movement/combat stability.

**YapEngine** (edge/chassis) sequences bridge and plugin work with **µs-resolution** `SequenceToken`s so I/O and menus stay ordered without owning the world heartbeat.

Citeable MSPT vs stock Folia / Canvas (ship knobs disclosed): [YAP_FOLIA_PATCHES.md](docs/folia/YAP_FOLIA_PATCHES.md) · soak profile: [YAP_FOLIA_PATCHES.md](docs/folia/YAP_FOLIA_PATCHES.md) · patch inventory: [YAP_FOLIA_PATCHES.md](docs/folia/YAP_FOLIA_PATCHES.md). We do **not** claim single-thread Paper MSPT victory. Re-verify after tick changes: `./scripts/lifecycle/yapctl cite-fullcite`.

---

## Get started

### Operators — download a release

1. Take **linux** or **windows** from the [0.0.0.2 prerelease](https://github.com/Xydroc-IO/YaPcore/releases/tag/0.0.0.2). Do not use GitHub **Latest**: **1.0.0.0** was deleted, so `/releases/latest` stays empty until a non-prerelease exists.
2. Unzip → `yapcore-release/linux` (or `windows`).
3. Configure secrets ([SECRETS.md](docs/start/SECRETS.md)), then launch:

```bash
# Linux
./start.sh --fg

# Windows
start.cmd -Fg
```

Join `127.0.0.1:25566` (Java + Bedrock). Dashboard: `http://127.0.0.1:8080/`

Also on the release: network / gameplay suites, `yapcore-default.zip` / `.mcpack`, and optional Fabric `client_mods.zip` (visuals, bag, staff, ultrawide, **presence**, **blocks**). Layout and rebuild: [RELEASES.md](docs/start/RELEASES.md).

### Developers — build from source

Requires **Java 25+**.

```bash
git clone https://github.com/Xydroc-IO/YaPcore.git && cd YaPcore
find scripts -type f \( -name '*.sh' -o -name yapctl \) -exec chmod +x {} +
./scripts/folia/build-yap-folia.sh          # → lib/yap-folia-26.2.jar
gradle installProductDefaults shadowJar
./scripts/setup/seed-defaults.sh
./scripts/db/ensure-db.sh --server-id lobby
./scripts/lifecycle/start.sh --fg
```

Local release trees (gitignored):

```bash
./scripts/folia/build-yap-folia.sh
./scripts/packs/build-yap-client-render.sh
gradle publishReleasesFolder -PyapGameplay=true
# → releases/0.0.0.2/yapcore-release-{linux,windows}.zip
```

Slim CORE+NETWORK is the **default** (`yapGameplay=false`). Opt in to GAMEPLAY
(skills / stacker / items / knobs / disasters / dungeons): `gradle assembleRelease -PyapGameplay=true`.

Domain line gate: `gradle checkDomainLineLimits` (≤500 lines per first-party domain `.java`).

---

## Architecture

```text
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│  YaP Link       │────▶│  YapEngine       │────▶│  YaP-Folia      │
│  multi-backend  │     │  edge · dual-stack│     │  regionized tick│
│  + Bedrock join │     │  dashboard · GUI  │     │  game authority │
└─────────────────┘     └──────────────────┘     └─────────────────┘
         │                        │
         │                        ▼
         │              first-party plugins/
         │              (CORE · NETWORK · GAMEPLAY)
         ▼
   yap-link-bedrock (native session / downstream)
```

| Layer | Role |
|-------|------|
| **YaP-Folia** | Game tick — build with `./scripts/folia/build-yap-folia.sh` |
| **YapEngine** | Edge networking, dual-stack, I/O, dashboard, Swing GUI |
| **YaP Link** | Multi-backend proxy + **Link-native Bedrock** — [YAP_LINK.md](docs/network/YAP_LINK.md) · [YAP_LINK.md](docs/network/YAP_LINK.md) |
| **Plugins** | First-party stack under [`yap-first-party/`](yap-first-party/README.md) |

Default product path: `game-authority=folia`, `folia-jar-source=build`, Link **`bedrock-mode=native`**.

Deep dive: [YAPCORE_WHITEPAPER.md](docs/whitepaper/YAPCORE_WHITEPAPER.md) · join port notes: [CROSSPLAY.md](docs/network/CROSSPLAY.md).

---

## Shipped plugins (CORE + NETWORK)

| Jar | Role |
|-----|------|
| `yap-perms.jar` | Ranks, tracks, prefixes |
| `yap-chat.jar` | Channels, PM, filter, staff chat |
| `yap-moderation.jar` | Ban / mute / warn / kick + history |
| `yap-essentials.jar` | Spawn, tpa, fly, vanish, bag/economy cmds |
| `yap-playerdata.jar` | Cross-server data, economy, backpacks |
| `yap-claims.jar` | Player land claims (`/claim`) |
| `yap-db.jar` | Shared SQL pool (MariaDB / Postgres / SQLite) |
| `yap-packs.jar` | Multi-active resource packs |
| `yap-floodgate.jar` | Bedrock identity |
| `yap-bedrock-ui.jar` | Bedrock form hubs |
| `yap-tailor.jar` | Skins / wardrobe / emotes / presence channel |
| `yap-bedrock-blocks.jar` | Catalog Bedrock port-blocks for JE |
| `yap-folia-bridge.jar` | Folia scheduling bridge |

Full inventory: [PLUGINS.md](docs/plugins/PLUGINS.md) · [plugins/README.md](plugins/README.md).  
Optional Fabric clients: [`client/`](client/) — presence + blocks required only when `parity.bedrock-feel=true`.

---

## Documentation

Operator and engineering docs live under [`docs/`](docs/) (**Markdown is the source of truth**). Start from the [Wiki](docs/README.md). Optional local PDF prints: `./scripts/docs/export-docs-pdf.sh` (gitignored under `docs/pdf/`).

| Audience | Start here |
|----------|------------|
| **Operators** | [QUICK_START](docs/start/QUICK_START.md) → [WIKI](docs/README.md) · [DEFAULTS](docs/start/DEFAULTS.md) |
| **Commands / perms** | [COMMANDS](docs/ops/COMMANDS.md) · [PERMISSIONS](docs/ops/PERMISSIONS.md) · [Dashboard](docs/ops/WEB_DASHBOARD.md) |
| **Network / packs** | [CROSSPLAY](docs/network/CROSSPLAY.md) · [CLIENTS_AND_PACKS](docs/network/CLIENTS_AND_PACKS.md) · [Bedrock-feel](docs/product/BEDROCK_FEEL_PARITY.md) |
| **Folia / capacity** | [YaP-Folia](docs/folia/YAP_FOLIA_PATCHES.md) |
| **Public edge** | [EDGE_HARDEN](docs/network/NETWORKING.md) · [SECRETS](docs/start/SECRETS.md) |
| **Architecture** | [Whitepaper](docs/whitepaper/YAPCORE_WHITEPAPER.md) |
| **Legal / AI** | [LICENSING](docs/start/LICENSING.md) · [AI transparency](docs/start/AI_TRANSPARENCY.md) |
| **Contributors** | [CONTRIBUTING](CONTRIBUTING.md) · [scripts/README](scripts/README.md) |

---

## Community & support

Bugs, operator help, and contributions are welcome. Prefer public GitHub issues for
non-security bugs so others can find the trail; use Discord for live help.

| Channel | Contact |
|---------|---------|
| **Email** | [xydroc@yaplabs.us](mailto:xydroc@yaplabs.us) |
| **Discord** | Username **xydroc** · server invite: [discord.gg/BXbyQk88Da](https://discord.gg/BXbyQk88Da) |
| **GitHub** | Issues / PRs on [Xydroc-IO/YaPcore](https://github.com/Xydroc-IO/YaPcore) · [CONTRIBUTING.md](CONTRIBUTING.md) |
| **Security** | Private only — [SECURITY.md](SECURITY.md) (email maintainers; do not file public issues for RCE/auth) |
| **Support the project** | [ko-fi.com/xydroc](https://ko-fi.com/xydroc) |


---

## Security

Report vulnerabilities privately — do **not** open a public issue for RCE, auth bypass, or pack-HTTP exposure. See [SECURITY.md](SECURITY.md).

Operators: keep secrets out of git ([SECRETS.md](docs/start/SECRETS.md)); put game and pack ports behind intentional firewall / nginx edges ([NETWORKING.md](docs/network/NETWORKING.md)).

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). One logical change per PR; keep build outputs, worlds, logs, and secrets out of the tree. Domain files under `src/main/java` and `yap-first-party/` must stay **≤500 lines**. AI-assisted patches are welcome when reviewed — [AI_TRANSPARENCY.md](docs/start/AI_TRANSPARENCY.md).

Questions before a PR: Discord ([invite](https://discord.gg/BXbyQk88Da), user **xydroc**) or email [xydroc@yaplabs.us](mailto:xydroc@yaplabs.us).

---

## License

**YaPcore** first-party source is **[GNU GPLv3](LICENSE)** (same family as Paper / Folia).

Minecraft (Mojang EULA), Faithful, Sodium (PolyForm Shield), YaP Iris (LGPL), and other bundled assets have separate terms — [LICENSING.md](docs/start/LICENSING.md). Branding marks: [branding/README.md](branding/README.md). AI use: [AI_TRANSPARENCY.md](docs/start/AI_TRANSPARENCY.md).
