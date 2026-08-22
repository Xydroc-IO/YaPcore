# YaP Factions

Factions overlay on **YaPPlayerdata claims** without altering the `claims` table schema.
Claim linkage lives in `yap_faction_claims` (claim_id → faction_id + power cost).

## Install

```bash
gradle :factions-plugin:installIntoPlugins
# or full core network:
gradle installProductDefaults
```

Requires `yap-db.jar`, `yap-playerdata.jar` (soft), and shared YaPDB.

## Commands

### Player (`/f`)

| Command | Description |
|---------|-------------|
| `/f create <name> <tag>` | Create faction |
| `/f disband` | Leader disbands |
| `/f join <faction>` | Join (respects join mode) |
| `/f leave` / `/f kick <player>` | Membership |
| `/f invite <player>` / `/f accept\|deny <faction>` | Invite flow |
| `/f promote\|demote\|leader <player>` | Role management |
| `/f desc <text>` / `/f motd [text]` | Description & MOTD |
| `/f open\|closed\|inviteonly` | Join mode (leader) |
| `/f home\|sethome\|delhome` | Faction home |
| `/f chat [msg\|off]` / `/f allychat [msg\|off]` | Faction & ally chat |
| `/f ally\|enemy\|neutral <faction>` | Relations |
| `/f claim\|unclaim\|claimall` | Link playerdata claim overlay |
| `/f members\|claims\|top [page]\|map` | Info views |
| `/f deposit\|withdraw\|bank` | Faction bank (YaPPlayerdata economy) |
| `/f info\|list\|power` | Status |

### Admin (`/yapfactions`)

| Command | Description |
|---------|-------------|
| `/yapfactions reload` | Reload config + service |
| `/yapfactions snapshot json` | Dashboard live snapshot |
| `/yapfactions setpower <faction> <power> [max]` | Set power |
| `/yapfactions setjoin <faction> <open\|invite\|closed>` | Set join mode |
| `/yapfactions disband <faction>` | Force disband |

## Power

- `max_power = base-max + members × per-member`
- Claim overlay cost = `ceil(claim_area / claim-blocks-per-power)`
- Available power = max − sum(overlay costs)
- Death power loss and periodic regen (configurable)
- Shield activates when power is depleted (blocks enemy build/PvP on territory)

## Roles

`LEADER` → `OFFICER` → `MEMBER` → `RECRUIT`

## Join modes

- **OPEN** — anyone may `/f join`
- **INVITE** — requires `/f invite` + `/f accept`
- **CLOSED** — no public joins

## Claim integration

When a claim has a faction overlay:

- **Build:** faction members (+ allies if configured) may build; claim owner still uses normal trust rules
- **PvP:** same-faction blocked; allies blocked; enemies allowed on enemy-linked territory (configurable)
- **Shield:** enemies blocked from building/PvP on shielded territory

## Placeholders (PlaceholderAPI)

| Placeholder | Value |
|-------------|-------|
| `%yapfaction_name%` | Faction name |
| `%yapfaction_tag%` | Faction tag |
| `%yapfaction_power%` | Available power |
| `%yapfaction_max_power%` | Max power |
| `%yapfaction_role%` | Player role |
| `%yapfaction_bank%` | Bank balance |
| `%yapfaction_shielded%` | Shield active |

## Dashboard

`GET /api/factions` — read-only JSON (counts, preview, optional live snapshot).

## Smoke

```bash
./scripts/smoke-factions-m6.sh
SKIP_LIVE=1 ./scripts/smoke-factions-m6.sh
```
