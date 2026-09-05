# Anti-cheat vs region protection

**Competitive / PvP networks: use Grim.**  
`./scripts/grim-ac.sh enable` then restart YaP-Folia. YaPGuard alone is **not** gold-standard AC.

YaPcore splits these into **different plugins** on purpose.

| Plugin | Role | WorldGuard / Matrix analog |
|--------|------|----------------------------|
| **YaPRegions** + **YaPPlayerData claims** | Cuboids, flags (pvp, build, entry, …) | **WorldGuard** |
| **YaPProtect** | Block/container logging, rollback | **CoreProtect** |
| **YaPGuard** | Lightweight movement/combat heuristics | **Not** WorldGuard — casual SMP default only |

## Competitive rule

| Network type | AC path |
|--------------|---------|
| Casual SMP / creative / low cheat pressure | YaPGuard (shipped, on by default) |
| **PvP / competitive / public grief risk** | **Grim** — [GRIM.md](GRIM.md) · `./scripts/grim-ac.sh enable` |

Do **not** market YaPGuard as a Grim/Matrix/Vulcan replacement.

## YaPGuard (native, shipped)

- Fly/glide without permission, speed/timer, reach, scaffold, no-fall heuristics
- Violation buffer → warn/kick; `yapguard.bypass` for staff
- **Not** Matrix / Vulcan / Grim parity — intentionally small and Folia-safe

Use YaPGuard when you want **zero extra jars** and basic SMP protection.

## Third-party “gold standard” on Folia

For serious PvP networks, **do not rebuild Matrix inside YaPGuard**. **Grim** is
fetched on first setup (disabled until enabled):

```bash
./scripts/seed-defaults.sh         # downloads grim.jar.disabled
./scripts/grim-ac.sh enable        # activate + disable YaPGuard movement checks
# restart YaP-Folia
```

Manual fetch: `./scripts/fetch-grim.sh --disabled` or `gradle fetchGrim` — see **[GRIM.md](GRIM.md)**.

| Option | Notes |
|--------|--------|
| **[Grim Anticheat](https://modrinth.com/plugin/grimac)** | **Required** for competitive claims; `fetch-grim.sh` + [GRIM.md](GRIM.md) |
| **Vulcan** (if your build supports Folia) | Verify version matrix before deploying |
| **YaPGuard only** | Fine for casual SMP / creative / low cheat pressure |

Product stance:

1. **Regions / flags** → extend YaP natives (claims + `yap-regions`)
2. **Heavy AC** → **Grim** for competitive; verify Folia support before deploy
3. **YaPGuard** → stays maintained as lightweight default, not a Matrix clone

## Permissions

| Node | Default |
|------|---------|
| `yapguard.admin` | op |
| `yapguard.bypass` | op |
| `yapguard.alerts` | op |
| `yapregions.admin` | op — admin cuboids |
| `yapdata.claims.admin` | op — player claims |

See [REGIONS.md](../gameplay/REGIONS.md) · [PERMISSIONS.md](PERMISSIONS.md).
