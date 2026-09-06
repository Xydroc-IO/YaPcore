# YaP Factions

Factions/guilds overlay on **YaPPlayerdata claims** without altering the `claims` table schema.
Claim linkage lives in `yap_faction_claims` (claim_id → faction_id + power cost).

**Opt-in:** jar ships with CORE+NETWORK but `enabled: false` by default. Not every server wants factions.
Set `enabled: true` in `plugins/YaPFactions/config.yml`, then `/yapfactions reload` (or restart).

Hardcore conquest land (chunk grid / overclaim / warzone) is **YaPConquest** — a separate plugin, also off by default. See [CONQUEST.md](CONQUEST.md).

## Install

```bash
gradle :factions-plugin:installIntoPlugins
# or full core network:
gradle installProductDefaults
./scripts/seed-defaults.sh   # copies config/defaults/plugins/YaPFactions/ if missing
```

Requires `yap-db.jar`, `yap-playerdata.jar` (soft), and shared YaPDB.

## Branding

| Config | Default | Notes |
|--------|---------|-------|
| `labels.singular` | Faction | Shown in messages |
| `labels.plural` | Factions | |
| `labels.command` | f | Help text root |

Commands: `/f`, `/faction`, `/guild`, `/g`, `/clan` — same handler.

PAPI: `%yapfaction_*%` and `%yapguild_*%` (aliases).

## Commands

### Player (`/f` or `/guild`)

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
| `/f setwarp\|delwarp\|warp\|warps` | Shared warps |
| `/f chat [msg\|off]` / `/f allychat [msg\|off]` | Faction & ally chat (toggle routes public chat) |
| `/f ally\|enemy\|neutral <faction>` | Relations |
| `/f claim\|unclaim\|claimall` | Link playerdata claim overlay |
| `/f members\|claims\|top [page]\|map\|info\|upkeep` | Info views |
| `/f deposit\|withdraw\|bank` | Faction bank (YaPPlayerdata economy) |
| `/f power` | Status |

### Admin (`/yapfactions`)

| Command | Description |
|---------|-------------|
| `/yapfactions reload` | Reload config; can enable features without full restart |
| `/yapfactions snapshot json` | Dashboard live snapshot |
| `/yapfactions upkeep [faction\|all]` | Force upkeep collect (freezes immediately if bank short) |
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
- **Chests:** same membership/ally rules via `territory.members-can-open-chests` / `allies-can-open-chests`
- **PvP:** same-faction blocked; allies blocked; enemies allowed on enemy-linked territory (configurable)
- **Shield:** enemies blocked from building/PvP on shielded territory

## Warps & upkeep

- Warps stored in `yap_faction_warps`; optional `warps.require-in-territory`
- Upkeep (`upkeep.enabled`, default **false**): debits bank per linked claim each period
- On unpaid: starts grace timer (`upkeep.grace-hours`); after grace, freezes linked claims (`tax_frozen`)
- `/yapfactions upkeep [faction|all]` force-collects and freezes immediately if bank is short

## Soft perks

- `perks.tab-prefix`: sets YaPPerms user meta `[TAG]` on join via Perms API (cleared on leave)
- `perks.chat-channel-toggle`: `/f chat` / `/f allychat` without args routes public chat
- `discord.role-sync` + `discord.roles`: MC→Discord role grant/revoke on join/leave **and** when the player completes Discord link / `/yapdiscord resync`

## Placeholders (PlaceholderAPI)

| Placeholder | Value |
|-------------|-------|
| `%yapfaction_name%` / `%yapguild_name%` | Faction name |
| `%yapfaction_tag%` | Faction tag |
| `%yapfaction_power%` | Available power |
| `%yapfaction_max_power%` | Max power |
| `%yapfaction_role%` | Player role |
| `%yapfaction_bank%` | Bank balance |
| `%yapfaction_shielded%` | Shield active |
| `%yapfaction_members%` | Member count |
| `%yapfaction_claims%` | Linked claim count |

## Web map tint

For faction-colored claim polygons on YaPMap, enable in `plugins/YaPMap/config.yml`:

```yaml
markers:
  claims: true
  faction-colors: true
```

## Dashboard

`GET /api/factions` — read-only JSON (counts, preview, optional live snapshot).
