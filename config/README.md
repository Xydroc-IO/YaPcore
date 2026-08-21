# YaPcore config hub

Edit **here** for day-to-day tuning.

| Path | What |
|------|------|
| `server.properties` | YaP product (ports, dual-stack, Phase 3, packs) |
| `paper/` | Paper globals / world defaults (**high-pop tuned**) |
| `spigot.yml` / `bukkit.yml` | Classic Spigot/Bukkit (**high-pop tuned; EAR uncapped for fair vs Leaf**) |
| `templates/highpop/` | Canonical Paper/Spigot/Bukkit templates (EAR=0) |
| `templates/highpop-ear/` | Optional tight EAR — not the product default |

Gameplay encyclopedia: `plugins/YaPGameplayKnobs/knobs.yml` (jar in `plugins/`).
See `docs/TUNE.md`.

**Fair vs Leaf:** product `spigot.yml` keeps **EAR uncapped (`0`)**. Other high-pop
knobs (redstone, hoppers, spawn limits, view/sim) remain. Opt into tight EAR via
`templates/highpop-ear/` only for production tradeoffs — not for scoreboards.

Fair MSPT benches: `scripts/bench/*.sh` also force uncapped EAR so vs-Paper/Leaf
stays apples-to-apples.
