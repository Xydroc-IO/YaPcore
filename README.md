# YaPcore

<p align="center">
  <img src="branding/yapcore-banner.png" alt="YaPcore banner" width="100%"/>
</p>

**Folia-first Minecraft server product** — game tick on **YaP-Folia** (our Folia 26.2 fork),
edge on **YapEngine**, network on **YaP Link**, with **first-party plugins** that replace
what most operators used to install separately (perms, chat, moderation, essentials,
playerdata, packs).

| | |
|--|--|
| **Quick Start** | [**docs/start/QUICK_START.md**](docs/start/QUICK_START.md) |
| **Wiki** | [**docs/WIKI.md**](docs/WIKI.md) |
| **Whitepaper** | [**docs/whitepaper/YAPCORE_WHITEPAPER.md**](docs/whitepaper/YAPCORE_WHITEPAPER.md) |
| **Releases** | [docs/start/RELEASES.md](docs/start/RELEASES.md) · build locally with `gradle publishReleasesFolder` |
| **Scripts** | [scripts/README.md](scripts/README.md) |
| **License** | [GPLv3](LICENSE) · [LICENSING.md](docs/start/LICENSING.md) · [Privacy](docs/start/PRIVACY_POLICY.md) · [Terms](docs/start/TERMS_OF_USE.md) |

> Not affiliated with Mojang / Microsoft.

---

## Quick start

### Build from source (recommended)

```bash
git clone https://github.com/Xydroc-IO/YaPcore.git && cd YaPcore
chmod +x scripts/*.sh scripts/db/*.sh scripts/yapctl
./scripts/build-yap-folia.sh          # → lib/yap-folia-26.2.jar
gradle installProductDefaults shadowJar
./scripts/seed-defaults.sh
./scripts/db/ensure-db.sh --server-id lobby
./scripts/start.sh --fg
```

Join `127.0.0.1:25566` (Java + Bedrock). Dashboard: http://127.0.0.1:8080/

Requires **Java 25+**. Full walkthrough: [docs/start/QUICK_START.md](docs/start/QUICK_START.md).

### Release packages (local)

Binaries under `releases/` are **gitignored**. Rebuild anytime:

```bash
./scripts/build-yap-folia.sh
gradle publishReleasesFolder
# → releases/<version>/yapcore-release-{linux,windows}.zip
```

Also: `gradle assembleRelease` → `build/dist/yapcore-release/`.

Details: [docs/start/RELEASES.md](docs/start/RELEASES.md).

---

## Shipped plugins (CORE + NETWORK)

| Jar | Role |
|-----|------|
| `yap-perms.jar` | Ranks, tracks, prefixes |
| `yap-chat.jar` | Channels, PM, filter, staff chat |
| `yap-moderation.jar` | Ban/mute/warn/kick + history |
| `yap-essentials.jar` | Spawn, tpa, fly, vanish |
| `yap-playerdata.jar` | Cross-server data, shops, auctions |
| `yap-db.jar` | Shared MariaDB pool |
| `yap-packs.jar` | Multi-active resource packs |
| `yap-floodgate.jar` | Bedrock identity |
| `yap-folia-bridge.jar` | Folia scheduling bridge |

**Gameplay opt-in:** `gradle installGameplayDefaults` — vehicles, stacker, MMO, knobs.

[plugins/README.md](plugins/README.md) · [docs/plugins/PLUGINS.md](docs/plugins/PLUGINS.md)

---

## Architecture

| Layer | Role |
|-------|------|
| **YaP-Folia** | Game tick (build with `./scripts/build-yap-folia.sh`) |
| **YapEngine** | Edge, dual-stack, I/O, GUI/dashboard |
| **YaP Link** | Multi-backend proxy — [YAP_LINK.md](docs/network/YAP_LINK.md) |

Default: `game-authority=folia`, `folia-jar-source=build`, dual-stack Java+Bedrock.

Deep dive: [whitepaper](docs/whitepaper/YAPCORE_WHITEPAPER.md).

---

## Documentation

| Audience | Start here |
|----------|------------|
| **Admins** | [QUICK_START.md](docs/start/QUICK_START.md) → [WIKI.md](docs/WIKI.md) · [DEFAULTS.md](docs/start/DEFAULTS.md) |
| **Commands / perms** | [COMMANDS.md](docs/ops/COMMANDS.md) · [PERMISSIONS.md](docs/ops/PERMISSIONS.md) |
| **Plugins / modules** | [PLUGINS.md](docs/plugins/PLUGINS.md) · [MODULES_AND_API.md](docs/plugins/MODULES_AND_API.md) |
| **Architecture** | [YAPCORE_WHITEPAPER.md](docs/whitepaper/YAPCORE_WHITEPAPER.md) |
| **Contributors** | [CONTRIBUTING.md](CONTRIBUTING.md) · [scripts/README.md](scripts/README.md) |

---

## License

**YaPcore** first-party source is **[GPLv3](LICENSE)** (same family as Paper/Folia).

Minecraft (Mojang EULA) and bundled packs have separate terms —
see **[docs/start/LICENSING.md](docs/start/LICENSING.md)**.
