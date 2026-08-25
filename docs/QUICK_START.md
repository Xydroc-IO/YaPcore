# Quick Start

Get a YaPcore server running in **under 10 minutes**. No nginx, no public domain, no
plugin hunting — everything you need ships in the box.

## Requirements

| Requirement | Notes |
|-------------|--------|
| **Java 25+** | Folia 26.2 needs JDK 25 at runtime |
| **8 GB RAM** recommended | Default heap is 2 GB; raise `ram-mb` for production |
| **Docker** (optional) | Easiest path for MariaDB — [MARIADB.md](MARIADB.md) |
| **Git** (source path only) | Or download a release zip — [RELEASES.md](RELEASES.md) |

---

## Path A — Download a release (recommended)

Pre-built jars, plugins, packs, and launch scripts — no Gradle required.

**Game jar:** releases may include `lib/yap-folia-*.jar` when the builder ran
`./scripts/build-yap-folia.sh`. If missing, either:

```bash
./scripts/build-yap-folia.sh    # requires JDK 25+, Git, network (source tree)
# config/server.properties:
folia-jar-source=build
```

or keep the stock path (`folia-jar-source=fetch` + `./scripts/fetch-folia.sh`).

**Recommended product path = YaP-Folia (`build`)** after soak-compat is green —
today the shipped default is still `fetch` until that gate; see
[YAP_FOLIA_SOAK.md](YAP_FOLIA_SOAK.md) · [FOLIA_FORK.md](FOLIA_FORK.md).

1. Download **`yapcore-release-linux.zip`** or **`yapcore-release-windows.zip`**
   from [GitHub Releases](https://github.com/yaplabs/YaPcore/releases) (or run
   `gradle assembleRelease` locally — output in `build/dist/yapcore-release/`).

2. Extract and enter the folder:

```bash
unzip yapcore-release-linux.zip
cd yapcore-release/linux
```

3. Accept the Minecraft EULA (first run only):

```bash
echo "eula=true" > eula.txt
```

4. Start MariaDB and wire JDBC (one command):

```bash
./configure-db.sh --server-id lobby
```

5. Start the server:

```bash
./start.sh --fg
# or GUI: ./gui.sh
```

6. Open the web dashboard: **http://127.0.0.1:8080/** — paste the token printed
   in the console on first boot.

7. In console or dashboard, grant yourself OP and apply ranks:

```
op YourName
ranks apply
/yapperm user YourName parent set admin
```

8. Join from Minecraft:

| Edition | Address |
|---------|---------|
| **Java** | `127.0.0.1:25566` (or port shown in boot banner) |
| **Bedrock** | same IP, same port (dual-stack UDP+TCP) |

---

## Path B — Build from source (developers)

```bash
git clone https://github.com/yaplabs/YaPcore.git
cd YaPcore
chmod +x scripts/*.sh

./scripts/fetch-folia.sh          # download Folia 26.2
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

Add gameplay plugins (vehicles, stacker):

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

Default rank ladder: `default` → `vip` → `mod` → `admin` (track **`yap`**).
See [PERMISSIONS.md](PERMISSIONS.md).

Default resource pack: **Faithful 64x** (+ vehicles overlay when gameplay tier enabled).

---

## First-run checklist

| Step | Command / setting |
|------|-------------------|
| Database | `./scripts/db/ensure-db.sh --server-id lobby` |
| Ranks | `ranks apply` (or set `yap-ranks-auto-apply=true`) |
| Your OP | `ops=YourName` in `config/server.properties` **or** `op YourName` |
| Dashboard token | Copy from console log → http://127.0.0.1:8080/ |
| Production auth | Set `online-mode=true` before going public |

---

## Configuration profiles

Copy `config/server.properties.example` → `config/server.properties` and pick a profile.

### Local / LAN (default)

Works immediately on the same machine or LAN. No domain, no nginx.

```properties
internet-exposed=false
online-mode=false          # offline UUIDs — fine for LAN/dev
allow-localhost=true
web-dashboard-localhost-only=true
```

### Public production

```properties
internet-exposed=true
online-mode=true           # Mojang auth required
public-host=play.example.com
public-port=25565
server-domain=play.example.com
web-dashboard-localhost-only=true   # put nginx + TLS in front if exposing dashboard
```

See [NETWORKING.md](NETWORKING.md) · [CLOUDFLARE_AND_NGINX.md](CLOUDFLARE_AND_NGINX.md).

---

## Docker (MariaDB only)

YaPcore runs on the host JVM; Docker is provided for the **database**:

```bash
./scripts/db/start-mariadb.sh
./scripts/db/ensure-db.sh --server-id lobby
```

Compose file: [`deploy/mariadb/docker-compose.yml`](../deploy/mariadb/docker-compose.yml).

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| “Java 25 required” | Install JDK 25+; `java -version` |
| Folia won't start | Run `./scripts/fetch-folia.sh`; check `folia-kernel/` |
| No DB connection | Run `ensure-db.sh`; check Docker is running |
| Can't join Java | Check port in boot banner; firewall; `online-mode` vs client auth |
| Can't join Bedrock | `bedrock-enabled=true`; same port as Java when `shared-listen-port=true` |
| Dashboard 401 | Paste token from console log into login screen |
| No commands in-game | You need OP: `op YourName` or `ops=YourName` in config |

More: [TESTING.md](TESTING.md) · [PLUGIN_COMPAT.md](PLUGIN_COMPAT.md).

---

## Next steps

| Topic | Doc |
|-------|-----|
| All commands | [COMMANDS.md](COMMANDS.md) |
| Permissions & ranks | [PERMISSIONS.md](PERMISSIONS.md) |
| Web dashboard | [WEB_DASHBOARD.md](WEB_DASHBOARD.md) |
| Multi-server proxy | [YAP_LINK.md](YAP_LINK.md) |
| Full doc index | [WIKI.md](WIKI.md) |
