# Tebex / web store → YaPcore

Tebex’s **plugin** is third-party (**GPLv3**). We can legally ship/redistribute it as a
separate jar; we do **not** vendor it in git. Fetch the Folia build:

```bash
./scripts/fetch-tebex.sh          # → plugins/tebex.jar
# or: gradle fetchTebex
```

Notices: `third-party/tebex/`.

## Dashboard setup (recommended)

Web admin → **Tebex store** (`GET/POST /api/tebex`):

1. Confirm `tebex.jar` is installed (fetch script above if missing).
2. Open [creator.tebex.io](https://creator.tebex.io/) → add a **Minecraft (Java / Folia) game server**.
3. Paste the **secret key** into the dashboard → **Save secret** (runs `tebex secret <key>` + writes `plugins/Tebex/config.yml`).
4. Optional: toggle `/buy` command name, proxy mode, verbose → **Save settings**.
5. Copy package console commands from the tab into Tebex packages (`{username}` placeholder).
6. Use **Store info** / **Force check** to verify delivery.

Also available under **Plugin editors** → Tebex (raw YAML).

## Console setup (same result)

```text
tebex secret <key>
tebex info
tebex forcecheck
```

## Where to install

| Place | Do this? |
|-------|----------|
| **Hub / lobby Folia backend** | **Yes** — `plugins/tebex.jar` |
| Survival / other backends | Optional |
| YaP Link / Velocity proxy | **No** for Bukkit console cmds (use Folia Hub) |

Prefer **tebex-folia** ≥ 2.3.3 (Folia duplicate-command fix). `fetch-tebex.sh` pulls latest.

## Rank packages (VIP)

YaPPerms uses shared MariaDB — one console command updates the whole network:

```text
yapperm user {username} parent set vip
```

| Package | Console command |
|---------|-----------------|
| Set VIP (primary) | `yapperm user {username} parent set vip` |
| Add VIP (keep other groups) | `yapperm user {username} parent add vip` |
| Remove VIP | `yapperm user {username} parent remove vip` |

VIP starter pack already grants `yapdata.kit.*` (all kits). Run `yapperm applypack` once on first boot.

## Kit packages (playerdata — not Essentials)

Kits live in **`yap-playerdata`** (`plugins/YaPPlayerData/kits.yml`). Copy the **same** `kits.yml` to Hub **and** every survival backend.

| Goal | Console command |
|------|-----------------|
| Unlock kit permanently | `yapperm user {username} permission set yapdata.kit.adventurer true` |
| Unlock VIP kit node | `yapperm user {username} permission set yapdata.kit.vip true` |
| Queue kit items (offline OK) | `kit grant {username} vip` |
| Give now (player online on Hub) | `kit give {username} vip` |

`kit grant` writes to shared MariaDB; the next backend the player joins that has that kit in `kits.yml` delivers the items.

## Example Tebex package: “VIP Rank”

Commands (Execute as console):

```text
yapperm user {username} parent set vip
kit grant {username} vip
```

## Example package: “Adventurer Kit Unlock”

```text
yapperm user {username} permission set yapdata.kit.adventurer true
kit grant {username} adventurer
```

## Checklist

1. Hub has CORE+NETWORK jars + `tebex.jar` (`./scripts/fetch-tebex.sh`).
2. Shared MariaDB (`use-shared-yapdb: true`).
3. Identical `plugins/YaPPlayerData/kits.yml` on Hub + survival.
4. Secret set via dashboard **Tebex store** or `tebex secret <key>` on Hub.
5. Packages use `{username}` — [examples/tebex/](../../examples/tebex/).

## Related

[WEB_DASHBOARD.md](WEB_DASHBOARD.md) · [PERMISSIONS.md](PERMISSIONS.md) · [COMMANDS.md](COMMANDS.md) · [LICENSING.md](../start/LICENSING.md) · [PLAYERDATA.md](../data/PLAYERDATA.md) · [YAP_LINK.md](../network/YAP_LINK.md)
