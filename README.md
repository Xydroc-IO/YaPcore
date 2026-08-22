# YaPcore

<p align="center">
  <img src="branding/yapcore-banner.png" alt="YaPcore banner" width="100%"/>
</p>

**Folia-first Minecraft server product** — game tick on **Folia**, edge on **YapEngine**,
network on **YaP Link**, with **first-party plugins** that replace what most operators
used to install separately (perms, chat, moderation, essentials, playerdata, packs).

| | |
|--|--|
| **Quick Start** | [**docs/QUICK_START.md**](docs/QUICK_START.md) — running in ~10 minutes |
| **Wiki** | [**docs/WIKI.md**](docs/WIKI.md) — full operator documentation |
| **Commands & perms** | [COMMANDS.md](docs/COMMANDS.md) · [PERMISSIONS.md](docs/PERMISSIONS.md) |
| **Download** | [GitHub Releases](https://github.com/yaplabs/YaPcore/releases) · [RELEASES.md](docs/RELEASES.md) |
| **License** | [MIT](LICENSE) · [LICENSING.md](docs/LICENSING.md) (third-party notes) |

> Not affiliated with Mojang / Microsoft.

---

## What makes YaPcore different

| Feature | YaPcore | Typical Paper stack |
|---------|---------|---------------------|
| **Game authority** | **Folia** (regionized tick) by default | Single main thread |
| **Shipped plugins** | Perms, chat, mod, essentials, DB, packs, floodgate | Install 10+ jars yourself |
| **Dual-stack** | Java + Bedrock on one port (built-in) | Geyser + Floodgate + config |
| **Multi-version JE** | Built-in protocol bands | ViaVersion + ViaBackwards + … |
| **Ops UI** | Swing GUI + web dashboard (`:8080`) | Console only or third-party panels |
| **Network proxy** | **YaP Link** (native Velocity-class) | Stock Velocity + DIY plugins |
| **MariaDB** | Docker one-liner + shared `yap-db` pool | Bring your own |

One `gradle installProductDefaults` (or a release zip) gives you a network-ready
server — not an empty Paper jar and a shopping list.

---

## Quick start

### Download (easiest)

1. Get **`yapcore-release-linux.zip`** or **`-windows.zip`** from
   [Releases](https://github.com/yaplabs/YaPcore/releases).
2. Extract → `echo eula=true > eula.txt`
3. `./configure-db.sh --server-id lobby && ./start.sh --fg`
4. Console: `op YourName` · `ranks apply`
5. Join: `127.0.0.1:25566` (Java + Bedrock)

Full walkthrough: [**docs/QUICK_START.md**](docs/QUICK_START.md).

### Build from source

```bash
git clone https://github.com/yaplabs/YaPcore.git && cd YaPcore
chmod +x scripts/*.sh
./scripts/fetch-folia.sh
gradle installProductDefaults shadowJar
./scripts/db/ensure-db.sh --server-id lobby
./scripts/start.sh --fg
```

**Release package** (pre-built tree for distribution):

```bash
gradle assembleRelease
# → build/dist/yapcore-release/linux/  and  .../windows/
```

Requires **Java 25+** for Folia 26.2. See [docs/QUICK_START.md](docs/QUICK_START.md).

---

## Shipped plugins (CORE + NETWORK)

Installed by `gradle installProductDefaults` or included in release zips:

| Jar | Role |
|-----|------|
| `yap-perms.jar` | Native ranks — groups, tracks, prefixes (`/yapperm`, `/promote`) |
| `yap-chat.jar` | Channels, PM, filter, staff chat, mute integration |
| `yap-moderation.jar` | Ban/mute/warn/kick, history, alts (`/ban`, `/modhistory`) |
| `yap-essentials.jar` | Spawn, tpa, fly, vanish, staff tools |
| `yap-playerdata.jar` | Cross-server data, offline `/login`, claims |
| `yap-db.jar` | Shared MariaDB Hikari pool |
| `yap-packs.jar` | Multi-active resource packs |
| `yap-floodgate.jar` | Bedrock identity (no Floodgate jar) |
| `yap-placeholderapi.jar` | Built-in PlaceholderAPI |
| `yap-pregen.jar` | Chunk pre-generator |
| `yap-plugin-compat.jar` | 1.20–1.21 jar back-compat |

**Gameplay opt-in:** `gradle installGameplayDefaults` — vehicles, stacker, knobs.

Details: [plugins/README.md](plugins/README.md) · [docs/PLUGINS.md](docs/PLUGINS.md).

---

## Architecture

| Layer | Role |
|-------|------|
| **YaP Link** | Multi-backend proxy — [YAP_LINK.md](docs/YAP_LINK.md) |
| **YapEngine** | Edge, dual-stack, I/O, GUI/dashboard — [YAPENGINE_16THREAD.md](docs/YAPENGINE_16THREAD.md) |
| **Folia** | Game tick (embedded JVM) |

Default: `game-authority=folia`, dual-stack Java+Bedrock, built-in resource pack HTTP.

---

## Configuration

| File | Purpose |
|------|---------|
| `config/server.properties` | Server identity, ports, RAM, dashboard, ranks auto-apply |
| `plugins/YaPPerms/config.yml` | Rank ladder, starter grants |
| `plugins/YaP*/config.yml` | Per-plugin tuning |
| `config/server.properties.example` | Template — copy and edit |

Sensible defaults work for **local/LAN** out of the box. For public production, set
`online-mode=true` and `internet-exposed=true` — see profiles in
[QUICK_START.md](docs/QUICK_START.md).

**Web dashboard:** http://127.0.0.1:8080/ — [WEB_DASHBOARD.md](docs/WEB_DASHBOARD.md)

**MariaDB (Docker):** `./scripts/db/ensure-db.sh` — [MARIADB.md](docs/MARIADB.md)

---

## Documentation

| Audience | Start here |
|----------|------------|
| **Server admins** | [QUICK_START.md](docs/QUICK_START.md) → [WIKI.md](docs/WIKI.md) |
| **Commands & permissions** | [COMMANDS.md](docs/COMMANDS.md) · [PERMISSIONS.md](docs/PERMISSIONS.md) |
| **Architecture / whitepaper** | [FULL_RUNDOWN.md](docs/FULL_RUNDOWN.md) · [whitepaper](docs/whitepaper/YAPCORE_WHITEPAPER.md) |
| **Releases & zips** | [RELEASES.md](docs/RELEASES.md) |
| **Contributors** | [CONTRIBUTING.md](CONTRIBUTING.md) · [SECURITY.md](SECURITY.md) |

---

## Scripts

| Script | Purpose |
|--------|---------|
| `scripts/start.sh` / `stop.sh` | Server lifecycle |
| `scripts/gui.sh` | Swing control panel |
| `scripts/db/ensure-db.sh` | Docker MariaDB + JDBC wiring |
| `scripts/fetch-folia.sh` | Download Folia 26.2 |
| `gradle assembleRelease` | Full release zip (linux + windows) |
| `gradle assemblePluginDist` | All plugin jars → `build/dist/yap-plugins/` |

Full list: [docs/TESTING.md](docs/TESTING.md).

---

## License

**YaPcore** first-party source is **[MIT](LICENSE)**.

Folia/Paper (GPLv3), Minecraft (Mojang EULA), and bundled resource packs have
separate terms — see **[docs/LICENSING.md](docs/LICENSING.md)**.

---

## Citation

```bibtex
@techreport{yapcore2026chassis,
  title  = {YaPcore: Folia Game Authority with a Slim Edge Chassis for Minecraft-Class Servers},
  author = {{YapLabs}},
  year   = {2026},
  number = {YAP-WP-16T-001}
}
```
