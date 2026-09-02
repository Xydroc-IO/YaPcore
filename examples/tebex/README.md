# Tebex / store command packages (Hub)

Install the Tebex (or any store) **game-server** plugin on your **Hub/lobby** Folia backend — not on YaP Link / Velocity.

```bash
./scripts/fetch-tebex.sh    # GPLv3 Folia jar → plugins/tebex.jar
# Dashboard: Tebex store → paste secret → Save secret
# Or Hub console: tebex secret <your-key>
```

Full guide: [docs/ops/TEBEX.md](../../docs/ops/TEBEX.md) · License notices: [third-party/tebex/](../../third-party/tebex/)

## Placeholder

Tebex uses `{username}`. Other stores may use `{player}` / `{name}` — adjust to match.

## Packages

### VIP rank

```text
yapperm user {username} parent set vip
```

Optional one-shot VIP kit delivery (queued if offline):

```text
kit grant {username} vip
```

### Adventurer kit unlock + delivery

```text
yapperm user {username} permission set yapdata.kit.adventurer true
kit grant {username} adventurer
```

### VIP kit unlock only (player claims with /kit vip)

```text
yapperm user {username} permission set yapdata.kit.vip true
```

(VIP rank already includes `yapdata.kit.*` after `yapperm applypack`.)

## Kits file

Keep the same `kits.yml` on every backend:

```text
plugins/YaPPlayerData/kits.yml
```

Shipped defaults: starter, adventurer, vip. Source template:
`yap-first-party/core-network/playerdata-plugin/src/main/resources/kits.yml`
