# Grim Anticheat (optional third-party)

**Competitive / PvP: enable Grim.** YaPGuard is a lightweight heuristics default only —
it is **not** sufficient for competitive anti-cheat claims. See [ANTICHEAT.md](ANTICHEAT.md).

**Grim AC** is the recommended **heavy** anti-cheat for PvP / competitive networks.
YaPcore ships **YaPGuard** as a lightweight native default (always on). **Grim is
downloaded automatically on first setup** but **not loaded** until you enable it.

```bash
./scripts/grim-ac.sh enable   # → grim.jar + turns off YaPGuard movement checks
# restart YaP-Folia (required)
```

## First install (automatic)

`./scripts/seed-defaults.sh` (also run from `start.sh`) downloads the latest
Folia-capable Grim build from Modrinth as:

```text
plugins/grim.jar.disabled
```

Folia ignores `*.jar.disabled` — Grim does **not** run until you enable it.

Skip the download (offline / CI): `YAP_SKIP_OPTIONAL_FETCH=1 ./scripts/seed-defaults.sh`

## Enable Grim (operator action)

```bash
./scripts/grim-ac.sh enable    # → grim.jar + turns off YaPGuard movement checks
# restart YaP-Folia
```

Disable again:

```bash
./scripts/grim-ac.sh disable   # → grim.jar.disabled
# restart YaP-Folia
```

Status:

```bash
./scripts/grim-ac.sh status
```

Manual fetch (same as setup, still disabled):

```bash
./scripts/fetch-grim.sh --disabled
# or active jar for dev: ./scripts/fetch-grim.sh
# or: gradle fetchGrim
```

Notices: `third-party/grim/`. License: **GPLv3** (same family as YaPcore).

| Place | Install? |
|-------|----------|
| **Every Folia backend** that needs AC | **Yes** — enable Grim per backend |
| Hub only | Common for lobby + PvP backends separately |
| YaP Link / proxy | **No** — Grim runs on the game server |

Restart Folia after enable/disable. Config appears under `plugins/GrimAC/`.

## YaPGuard vs Grim

| | YaPGuard (native) | Grim (optional) |
|--|-------------------|-----------------|
| Jars | `yap-guard.jar` (product default, **on**) | `grim.jar.disabled` → `grim.jar` after enable |
| Depth | Fly/speed/reach/scaffold heuristics | Full movement simulation |
| Best for | Casual SMP, low cheat pressure | PvP, minigames, competitive |

**Do not run both at full sensitivity.** Typical setups:

- **Grim only** — `./scripts/grim-ac.sh enable` (disables YaPGuard movement checks)
- **YaPGuard only** — leave Grim disabled (default after setup)
- **Grim + YaPGuard alerts** — Grim punishes; YaPGuard checks off, alerts optional

See [ANTICHEAT.md](ANTICHEAT.md) for the locked product split (regions ≠ AC).

## YaP stack notes

| Topic | Guidance |
|-------|----------|
| **Bedrock / Geyser-class** | Grim exempts Bedrock players; keep `yap-floodgate` on the **backend** (not only Link) so UUID/linking matches |
| **Via-class protocol** | First-party edge on YaPcore chassis — no Via jars on backend; Grim sees normalized Paper movement |
| **Folia** | Use Modrinth builds with `folia` loader tag (`fetch-grim.sh` filters this) |
| **Moderation** | Grim alerts → wire Discord webhook in Grim config or mirror to `yap-moderation` via console |
| **Staff** | Grant Grim bypass + `yapguard.bypass` if YaPGuard stays loaded |

## Release zips

Release trees ship `grim.jar.disabled` when a Grim jar was present at build time
(or after `seed-defaults.sh` on the installed copy). Operators still run
`grim-ac.sh enable` before Grim loads.

## Related

- [ANTICHEAT.md](ANTICHEAT.md) — YaPGuard vs regions vs third-party AC
- [TEBEX.md](TEBEX.md) — same optional-jar fetch pattern
- [LICENSING.md](../start/LICENSING.md) — GPLv3 third-party redistribution
