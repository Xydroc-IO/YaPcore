# Anti-cheat vs region protection

YaPcore splits these into **different plugins** on purpose.

| Plugin | Role | WorldGuard / Matrix analog |
|--------|------|----------------------------|
| **YaPRegions** + **YaPPlayerData claims** | Cuboids, flags (pvp, build, entry, …) | **WorldGuard** |
| **YaPProtect** | Block/container logging, rollback | **CoreProtect** |
| **YaPGuard** | Lightweight movement/combat heuristics | **Not** WorldGuard — basic native AC |

## YaPGuard (native, shipped)

- Fly/glide without permission, speed/timer, reach, scaffold, no-fall heuristics
- Violation buffer → warn/kick; `yapguard.bypass` for staff
- **Not** Matrix / Vulcan / Grim parity — intentionally small and Folia-safe

Use YaPGuard when you want **zero extra jars** and basic SMP protection.

## Third-party “gold standard” on Folia

For serious PvP networks, **do not rebuild Matrix inside YaPGuard**. Fetch **Grim**
like Tebex — optional jar, GPLv3, not in git:

```bash
./scripts/fetch-grim.sh    # → plugins/grim.jar
# or: gradle fetchGrim
```

Full setup: **[GRIM.md](GRIM.md)** · notices in `third-party/grim/`.

| Option | Notes |
|--------|--------|
| **[Grim Anticheat](https://modrinth.com/plugin/grimac)** | **Recommended** heavy AC; `fetch-grim.sh` + [GRIM.md](GRIM.md) |
| **Vulcan** (if your build supports Folia) | Verify version matrix before deploying |
| **YaPGuard only** | Fine for casual SMP / creative / low cheat pressure |

Product stance:

1. **Regions / flags** → extend YaP natives (claims + `yap-regions`)
2. **Heavy AC** → optional third-party jar + `scripts/check-plugin-layout.sh` hint
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
