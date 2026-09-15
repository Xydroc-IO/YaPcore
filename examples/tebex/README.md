# Tebex / store command packages (Hub)

Install store delivery on your **Hub/lobby** Folia backend — not on YaP Link / Velocity.

## Two delivery paths

| Path | Install | Notes |
|------|---------|--------|
| **Poll / GUI** | `./scripts/fetch-tebex.sh` → `plugins/tebex.jar` | GPLv3 Folia plugin; `/buy`, GUI, `tebex forcecheck` |
| **Webhook push** | `gradle :tebex-webhook-plugin:installIntoPlugins` → `yap-tebex.jar` | First-party; `POST /tebex/webhook` → package ID → console |

```bash
./scripts/fetch-tebex.sh    # GPLv3 Folia jar → plugins/tebex.jar
# Dashboard: Tebex store → paste game-server secret → Save secret
# Or Hub console: tebex secret <your-key>

# Optional webhook push (do not double-map the same package commands):
# gradle :tebex-webhook-plugin:installIntoPlugins
# creator.tebex.io → Developers → Webhooks → Endpoints
# Reverse-proxy https://<public>/tebex/webhook → 127.0.0.1:8766
# Map packages in plugins/YaPTebex/config.yml
```

If a package still has commands in creator.tebex.io **and** a webhook recipe, delivery **doubles**.
Clear package console commands for webhook-mapped IDs (or leave plugin-only packages alone).

Full guide: [docs/ops/TEBEX.md](../../docs/ops/TEBEX.md) → [INTEGRATIONS.md](../../docs/ops/INTEGRATIONS.md#tebex)
· Editable recipes: `config/tebex-recipes.yml` (defaults in `config/defaults/`)
· License notices: [third-party/tebex/](../../third-party/tebex/)

## Placeholder

Tebex uses `{username}`. Other stores may use `{player}` / `{name}` — adjust to match.

Webhook also supports `{transaction}` and `{packageId}`.

**Bedrock / Floodgate checkout names:** use the **dot prefix** (`.Name`), not `*`.

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
