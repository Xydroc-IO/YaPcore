# YaP Combat (M2)

Custom PvE (and optional PvP) combat for the YaP MMO stack. Ships as
`yap-combat.jar` with `CombatService` on Bukkit `ServicesManager`.

## Custom HP model

**Choice: ratio-scaled vanilla bar** (not a separate boss bar).

| Layer | Behaviour |
|-------|-----------|
| Internal | Integer custom HP in `yap_combat_state.current_hp` |
| Max HP | `base-hp + hitpoints_level × hp-per-hitpoints-level` |
| Display | Vanilla hearts always max 20; current = `(currentHp / maxHp) × 20` |

The config key `custom-hp.hearts-display: 10` documents RS-style granularity
(10 custom HP ≈ one logical “heart” in tuning docs). Display still uses the
ratio model so high HP levels (99 HP skill) fit the vanilla hotbar.

Alternative not used: separate boss-bar HP overlay.

## Damage pipeline

1. `EntityDamageByEntityEvent` — vanilla damage **cancelled**
2. Hit roll on entity thread via `YapSched.entity`
3. `maxHit = floor((strength + gear + pot) × level-factor)`
4. Hit if `attackRoll > defenceRoll` (RS-lite)
5. Damage `0…maxHit`, minimum `formula.min-damage-on-hit` on successful hit
6. Optional **critical hits** (`formula.crit-chance`, `formula.crit-multiplier`)
7. **Knockback physics** on successful hits (`physics.*`)
8. **Combo meter** scales consecutive hit damage (`combo.*`)
9. **Status effects** — DOT/HOT/debuffs/CC from `status-effects.yml` and on-hit procs
10. **Projectile physics** — custom velocity, pierce, drop-off, headshots (`projectiles.*`)

Hit rolls run on the **async scheduler** (`CombatHitResolver`); damage application stays on entity threads (Folia-safe).

Mob defence/strength derive from mob max health (`pve.mob-defence-health-divisor`,
default 20). Each combat damage point removes `pve.health-per-combat-point` vanilla
HP (default 2 ≈ one heart) so low-level “Hit 1” still kills chickens/zombies.

Player stats come from `SkillService` (attack, strength, defence, hitpoints) plus gear from
`items.yml`.

## Gear

- `plugins/YaPCombat/items.yml` — `Material → bonuses`
- Optional PDC `yapcombat:yap_gear_tier` on items for tier rows under `tiers:`

## Food & potions

- **Food:** right-click items in `food.yml` → heal custom HP; cooldown
  `food.cooldown-ticks`
- **Potions:** three types in `config.yml` (`attack`, `strength`, `defence`) —
  material match, boost + duration + per-type cooldown; visible in `/combat stats`

## Combat XP

When `yap-skills` is loaded, XP is granted via `SkillService.addXp`:

| Skill | Source |
|-------|--------|
| Attack | damage dealt / kill share |
| Strength | damage dealt / kill share |
| Defence | damage taken |
| Hitpoints | share of all combat XP |

Shares configurable under `xp.*` in `config.yml`.

## PvP

- **`pvp: false`** by default in `config.yml`
- **YaPGames override:** active minigame matches allow PvP via `GameServices.allowPvp()` even when global PvP is off
- When enabled, respects YaPPlayerdata claim flag **`pvp`** (`RegionFlag.PVP`)
- **Attack cadence:** `combat.attack-cooldown-ticks` / `combat.ranged-cooldown-ticks` prevent click-spam
- **Skill cache:** async skill-level snapshot refreshed on join and `SkillLevelUpEvent` (`combat.skill-cache-ttl-ms`)

## Status effects

Definitions in `plugins/YaPCombat/status-effects.yml`:

| Effect | Kind | Notes |
|--------|------|-------|
| bleed / poison / burn | DOT | stacking damage over time |
| freeze / stun | CC | movement slow; stun blocks attacks |
| weakness / vulnerable | debuff | lowers stats or raises damage taken |
| enraged / regen | buff | strength boost or heal over time |

On-hit procs in `config.yml` (`on-hit-procs`) and spell `applies-effect` in `spells.yml`.
View active effects via `/combat stats`.

## Combo meter

Chain hits within `combo.window-ms` for +`combo.bonus-per-stack` damage per stack (up to `combo.max`).
Shown in action bar (`8x COMBO +21%`). Resets on miss when `combo.reset-on-miss: true`.

## Projectiles

Managed arrows/bolts get boosted velocity, optional pierce (scales with ranged level),
distance drop-off, and headshot multiplier. Routed through the same hit pipeline as melee.

## Commands

| Command | Permission |
|---------|------------|
| `/combat` or `/combat stats` | `yapcombat.use` |
| `/combat reload` | `yapcombat.admin` |
| `/yapcombat admin sethp <player> <hp>` | `yapcombat.admin` |

## Death

- `death.keep-inventory` — RS-lite keep items flag
- Respawn restores custom HP when `death.restore-hp-on-respawn: true`

## See also

- [MMO_PHASES.md](MMO_PHASES.md) — M2 acceptance checklist
- [PERMISSIONS.md](../ops/PERMISSIONS.md) — `yapcombat.*` nodes
