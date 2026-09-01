# YaPcore config hub

Edit **here** for day-to-day tuning.

| Path | What |
|------|------|
| `server.properties` | YaP product (ports, dual-stack, packs, Folia) |
| `server.properties.example` | Canonical product profile (copy if missing) |
| `defaults/` | **Shipped first-boot pack** — see [docs/start/DEFAULTS.md](../docs/start/DEFAULTS.md) |
| `paper/` | Paper globals / world defaults (**high-pop tuned**) |
| `spigot.yml` / `bukkit.yml` | Classic Spigot/Bukkit (**high-pop tuned; EAR uncapped for fair vs Leaf**) |
| `templates/highpop/` | Canonical Paper/Spigot/Bukkit templates (EAR=0) |
| `templates/highpop-ear/` | Optional tight EAR — not the product default |

`./scripts/seed-defaults.sh` (also run from `start.sh`) copies `defaults/**` into
`config/`, `plugins/`, and `link-data/` **only when those files are missing**.

Gameplay encyclopedia: `plugins/YaPGameplayKnobs/knobs.yml` (jar in `plugins/`).
See `docs/ops/TUNE.md`.

**Fair vs Leaf:** product `spigot.yml` keeps **EAR uncapped (`0`)**. Other high-pop
knobs (redstone, hoppers, spawn limits, view/sim) remain. Opt into tight EAR via
`templates/highpop-ear/` only for production tradeoffs — not for scoreboards.

Fair MSPT benches: `scripts/bench/*.sh` also force uncapped EAR so vs-Paper/Leaf
stays apples-to-apples.
