# Quick Start

Get a YaPcore server running in **under 10 minutes**. No nginx, no public domain, no
plugin hunting — everything you need ships in the box.

**Game jar:** product default is **YaP-Folia** (`lib/yap-folia-*.jar`, `folia-jar-source=build`) — our Folia 26.2 fork, not stock Fill Folia. See [FOLIA_FORK.md](FOLIA_FORK.md).

## Requirements

| Requirement | Notes |
|-------------|--------|
| **Java 25+** | YaP-Folia 26.2 needs JDK 25 at runtime |
| **8 GB RAM** recommended | Default heap is 2 GB; raise `ram-mb` for production |
| **Docker** (optional) | Easiest path for MariaDB — [MARIADB.md](MARIADB.md) |
| **Git** (source path only) | Or download a release zip — [RELEASES.md](RELEASES.md) |

---

## Path A — Download a release (recommended)

Pre-built jars, plugins, packs, and launch scripts — no Gradle required.

If the release includes `lib/yap-folia-*.jar`, you’re on the product path. If missing:

```bash
./scripts/build-yap-folia.sh    # JDK 25+, Git, network (source tree)
# config/server.properties:
folia-jar-source=build
```

Stock Folia fallback (benches only): `folia-jar-source=fetch` + `./scripts/fetch-folia.sh`.

1. Download **`yapcore-release-linux.zip`** or **`yapcore-release-windows.zip`**
   built locally with `gradle publishReleasesFolder` (gitignored under `releases/`)
   or `gradle assembleRelease` → `build/dist/yapcore-release/`.

2. Extract and enter the folder:

```bash
unzip yapcore-release-linux.zip
cd yapcore-release/linux
```

3. Accept the Minecraft EULA (first run only):

```bash
echo "eula=true" > eula.txt
```

4. Seed shippable defaults (optional — `start.sh` also does this):

```bash
./scripts/seed-defaults.sh
```

5. Start MariaDB and wire JDBC (one command):

```bash
./configure-db.sh --server-id lobby
```

6. Start the server:

```bash
./start.sh --fg
# or GUI: ./gui.sh
```

7. Open the web dashboard: **http://127.0.0.1:8080/** — paste the token printed
   in the console on first boot.

8. In console or dashboard, grant yourself OP and apply ranks:

```
op YourName
ranks apply
/yapperm user YourName parent set admin
```

9. Join from Minecraft:

| Edition | Address |
|---------|---------|
| **Java** | `127.0.0.1:25566` (or port shown in boot banner) |
| **Bedrock** | same IP, same port (dual-stack UDP+TCP) |

---

## Path B — Build from source (developers)

```bash
git clone https://github.com/Xydroc-IO/YaPcore.git
cd YaPcore
chmod +x scripts/*.sh

./scripts/build-yap-folia.sh      # YaP-Folia → lib/yap-folia-26.2.jar
gradle installProductDefaults     # first-party plugins → plugins/
gradle shadowJar                  # yapcore.jar

./scripts/db/ensure-db.sh --server-id lobby
./scripts/start.sh --fg
```

Release tree (for distribution):

```bash
gradle assembleRelease
cd build/dist/yapcore-release/linux && ./start.sh --fg
```

Add gameplay plugins (vehicles, stacker, MMO):

```bash
gradle assembleRelease -PyapGameplay=true
```

---

## What works out of the box

After `installProductDefaults` / a release zip, you get:

| Jar | What it replaces |
|-----|------------------|
| `yap-perms.jar` | LuckPerms — groups, tracks, prefixes |
| `yap-chat.jar` | Chat plugins — channels, PM, filter, staff chat |
| `yap-moderation.jar` | LiteBans / BanManager — ban, mute, warn, history |
| `yap-essentials.jar` | Essentials — spawn, tpa, fly, vanish, … |
| `yap-playerdata.jar` | Cross-server data, offline `/login`, claims |
| `yap-db.jar` | Shared MariaDB pool for all SQL plugins |
| `yap-packs.jar` | Multi-active resource packs |
| `yap-floodgate.jar` | Bedrock identity without Floodgate jar |
| `yap-placeholderapi.jar` | PlaceholderAPI (built-in) |
| `yap-lagguard.jar` | Per-chunk lag-machine governor |

Default rank ladder: `default` → `vip` → `mod` → `admin` (track **`yap`**).
See [PERMISSIONS.md](PERMISSIONS.md).

Default resource pack: **Faithful 64x** (+ vehicles overlay when gameplay tier enabled).

---

## First-run checklist

| Step | Command / setting |
|------|-------------------|
| YaP-Folia jar | `lib/yap-folia-*.jar` present; `folia-jar-source=build` |
| Database | `./scripts/db/ensure-db.sh --server-id lobby` |
| Ranks | `ranks apply` (or set `yap-ranks-auto-apply=true`) |
| Your OP | `ops=YourName` in `config/server.properties` **or** `op YourName` |
| Dashboard token | Copy from console log → http://127.0.0.1:8080/ |
| Production auth | Set `online-mode=true` before going public |

---

## Configuration profiles

Copy `config/server.properties.example` → `config/server.properties` and pick a profile.

### Local / LAN (default)

```properties
internet-exposed=false
online-mode=false
allow-localhost=true
web-dashboard-localhost-only=true
game-authority=folia
folia-jar-source=build
```

### Public production

```properties
internet-exposed=true
online-mode=true
public-host=play.example.com
public-port=25565
server-domain=play.example.com
web-dashboard-localhost-only=true
folia-jar-source=build
```

See [NETWORKING.md](NETWORKING.md) · [CLOUDFLARE_AND_NGINX.md](CLOUDFLARE_AND_NGINX.md) · [EDGE_HARDEN.md](EDGE_HARDEN.md).

---

## Docker (MariaDB only)

YaPcore runs on the host JVM; Docker is provided for the **database**:

```bash
./scripts/db/start-mariadb.sh
./scripts/db/ensure-db.sh --server-id lobby
```

Compose: [`deploy/mariadb/docker-compose.yml`](../deploy/mariadb/docker-compose.yml).

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| “Java 25 required” | Install JDK 25+; `java -version` |
| YaP-Folia won't start | Run `./scripts/build-yap-folia.sh`; check `lib/yap-folia-*.jar` and `folia-kernel/` |
| Stock Folia only | You set `folia-jar-source=fetch` — switch to `build` for product path |
| No DB connection | Run `ensure-db.sh`; check Docker is running |
| Can't join Java | Check port in boot banner; firewall; `online-mode` vs client auth |
| Can't join Bedrock | `bedrock-enabled=true`; same port as Java when `shared-listen-port=true` |
| Dashboard 401 | Paste token from console log into login screen |
| No commands in-game | You need OP: `op YourName` or `ops=YourName` in config |

More: [TESTING.md](TESTING.md) · [PLUGIN_COMPAT.md](PLUGIN_COMPAT.md) · [FOLIA_FORK.md](FOLIA_FORK.md).

---

## Next steps

| Topic | Doc |
|-------|-----|
| YaP-Folia fork | [FOLIA_FORK.md](FOLIA_FORK.md) |
| All commands | [COMMANDS.md](COMMANDS.md) |
| Permissions & ranks | [PERMISSIONS.md](PERMISSIONS.md) |
| Web dashboard | [WEB_DASHBOARD.md](WEB_DASHBOARD.md) |
| Multi-server proxy | [YAP_LINK.md](YAP_LINK.md) |
| Full doc index | [WIKI.md](WIKI.md) |
