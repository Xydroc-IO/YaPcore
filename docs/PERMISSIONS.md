# Permissions & ranks

YaP first-party plugins gate commands with **Bukkit permission nodes**.
Groups / ranks / chat prefixes use **[LuckPerms](https://luckperms.net/)** — YaP
ships a starter pack, install script, console command, and dashboard tab.

## Quick start

```bash
./scripts/install-luckperms.sh          # downloads LuckPerms into plugins/
./scripts/start.sh --fg
# console or dashboard:
ranks apply
lp user Steve parent set vip
```

Optional auto-apply on boot (once):

```properties
yap-ranks-auto-apply=true
```

in `config/server.properties` (requires LuckPerms jar present; waits ~8s after start).

| Surface | How |
|---------|-----|
| Console / stdin | `ranks status` · `ranks apply` · `ranks apply force` |
| Web dashboard | **Ranks** tab → Apply pack |
| Paste file | [`examples/luckperms/apply-yap-ranks.txt`](../examples/luckperms/apply-yap-ranks.txt) |

OP still receives every node with `default: op` without LuckPerms.

## Rank ladder (pack)

| Rank | Weight | Prefix | Inherits | Role |
|------|--------|--------|----------|------|
| `default` | 0 | *(none)* | — | All players |
| `vip` | 10 | `[VIP]` | default | Donors / trusted |
| `mod` | 50 | `[Mod]` | vip | Moderators |
| `admin` | 100 | `[Admin]` | mod | Admins (not necessarily OP) |

Track name: **`yap`** (`lp track yap`). Promote with:

```
lp user Steve promote yap
lp user Steve demote yap
```

Chat plugins that read LP prefix/suffix meta will show the pack prefixes.

## Playerdata command nodes

| Node | Default | Command / feature |
|------|---------|-------------------|
| `yapdata.menu` | true | `/menu` |
| `yapdata.balance` | true | `/bal` (self) |
| `yapdata.balance.others` | op | `/bal <other>` |
| `yapdata.pay` | true | `/pay` |
| `yapdata.home` | true | `/home` `/sethome` `/delhome` `/homes` |
| `yapdata.warp` | true | `/warp` `/warps` |
| `yapdata.warp.admin` | op | `/setwarp` `/delwarp` |
| `yapdata.kit` | true | `/kit` `/kits` GUI |
| `yapdata.kit.starter` | true | Starter kit (default config) |
| `yapdata.kit.*` | op | All kits |
| `yapdata.kit.<id>` | — | Per-kit (add nodes when you add kits) |
| `yapdata.mail` | true | `/mail` |
| `yapdata.shop` | true | `/shop` + chest shop buy |
| `yapdata.jobs` | true | `/jobs` GUI |
| `yapdata.job.miner` | true | Join miner |
| `yapdata.job.lumberjack` | true | Join lumberjack |
| `yapdata.job.*` | op | Any job |
| `yapdata.job.<id>` | — | Per-job |
| `yapdata.ah` | true | `/ah` |
| `yapdata.claim` | true | `/claim` |
| `yapdata.claims.admin` | op | Bypass claims |
| `yapdata.claims.wilderness` | false | Build in wilderness when required |
| `yapdata.admin` | op | `/yapdata`, override shops/AH/claims |
| `yapdata.trader.admin` | op | `/trader` |

Auth (`/register` `/login` …) stays ungated so offline login always works.

### Pack grants by rank

| Rank | Extra beyond inheritance |
|------|--------------------------|
| default | Playerdata player nodes + `kit.starter` + `job.miner` / `job.lumberjack` + `yapvehicles.drive` |
| vip | Vehicles spawn/command, wilderness build, `kit.*`, `job.*` |
| mod | Warp admin, balance others, claim bypass, stacker tools, compat/papi parse |
| admin | All YaP `*.admin` / pregen / knobs / db / packs |

## Other first-party nodes

| Node | Default | Plugin |
|------|---------|--------|
| `yapvehicles.drive` | true | Vehicles |
| `yapvehicles.command` / `.spawn` / `.destroy` | op | Vehicles |
| `yapstacker.admin` / `.gui` / `.give` / `.wand` / `.tool` / `.aura` | op | Stacker |
| `yappregen.admin` | op | Pregen |
| `yapknobs.reload` | op | Knobs |
| `yapdb.admin` | op | YaPDB |
| `yappacks.admin` | op | Packs |
| `yapcompat.status` | op | Compat |
| `placeholderapi.admin` / `.parse` | op | PAPI |

## Custom kits / jobs

When you add a kit id `vip` in `plugins/YaPPlayerData/config.yml`:

```
lp group vip permission set yapdata.kit.vip true
```

(Or grant `yapdata.kit.*`.) Same pattern for `yapdata.job.<id>`.

## See also

- [examples/luckperms/](../examples/luckperms/) — pack + README
- [COMMANDS.md](COMMANDS.md) · [WEB_DASHBOARD.md](WEB_DASHBOARD.md) · [PLUGIN_COMPAT.md](PLUGIN_COMPAT.md)
