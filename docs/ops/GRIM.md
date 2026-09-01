# Grim Anticheat (optional third-party)

**Grim AC** is the recommended **heavy** anti-cheat for PvP / competitive networks.
YaPcore ships **YaPGuard** as a lightweight native default; Grim is optional and
fetched like Tebex — not vendored in git.

## Fetch & install

```bash
./scripts/fetch-grim.sh          # → plugins/grim.jar
# or: gradle fetchGrim
```

Notices: `third-party/grim/`. License: **GPLv3** (same family as YaPcore).

| Place | Install? |
|-------|----------|
| **Every Folia backend** that needs AC | **Yes** — `plugins/grim.jar` |
| Hub only | Common for lobby + PvP backends separately |
| YaP Link / proxy | **No** — Grim runs on the game server |

Restart Folia after install. Config appears under `plugins/GrimAC/`.

## YaPGuard vs Grim

| | YaPGuard (native) | Grim (optional) |
|--|-------------------|-----------------|
| Jars | `yap-guard.jar` (product default) | `grim.jar` (fetch) |
| Depth | Fly/speed/reach/scaffold heuristics | Full movement simulation |
| Best for | Casual SMP, low cheat pressure | PvP, minigames, competitive |

**Do not run both at full sensitivity.** Typical setups:

- **Grim only** — disable YaPGuard checks in `plugins/YaPGuard/config.yml`, or remove `yap-guard.jar`
- **YaPGuard only** — no Grim fetch (default product path)
- **Grim + YaPGuard alerts** — Grim punishes; YaPGuard off or bypass-only for staff

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

If `plugins/grim.jar` exists before `gradle assembleRelease`, it is copied into
the zip with `grim-NOTICE.txt` / `grim-LICENSE-GPLv3.txt` (same pattern as Tebex).

## Related

- [ANTICHEAT.md](ANTICHEAT.md) — YaPGuard vs regions vs third-party AC
- [TEBEX.md](TEBEX.md) — same optional-jar fetch pattern
- [LICENSING.md](../start/LICENSING.md) — GPLv3 third-party redistribution
