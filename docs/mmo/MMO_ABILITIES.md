# YaP MMO — Ability engine (M6)

**Status:** Shipped (M6)  
**Jars:** `yap-abilities-api.jar`, `yap-abilities.jar`  
**Depends on:** `yap-skills`, `yap-combat` (optional bridge), `yap-sched`

## Overview

M6 adds a **config-driven ability catalog** separate from RS progression skills:

| Concept | Count (v1) | Notes |
|---------|------------|-------|
| RS progression skills | 13 | Unchanged (`yap-skills`) |
| **Combat abilities** | **233** | YAML packs in `plugins/YaPAbilities/abilities/` |
| Status effects | 14 | Buffs/debuffs/DoTs in `effects/` |

Abilities support:

- **Custom icons** on dedicated `CLAY_BALL` tokens (CustomModelData) — not swords/rods/tools
- **Cast VFX + sounds** (particles, volume/pitch)
- **Projectiles** (entity type, speed, trail particles, hit detection)
- **On-hit pipeline** (damage, knockback, buff/debuff apply, XP)
- **Costs** (prayer, runes, staff)
- **Cooldowns** and level gates per skill
- **Self / raycast** targeting + filters (`undead`, `player`, `mob`)

Folia-safe: all entity/world mutations via `YapSched`.

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/ability list [page] [category]` | `yapabilities.use` | Paginated ability list |
| `/ability cast <id>` | `yapabilities.use` | Cast ability |
| `/ability info <id>` | `yapabilities.use` | Show requirements |
| `/ability bar` | `yapabilities.use` | List hotbar bindings + current page |
| `/ability book [category] [page]` | `yapabilities.use` | Open ability book GUI |
| `/abilities` | `yapabilities.use` | Alias — open ability book |
| `/ability tome` | `yapabilities.use` | Receive an Ability Tome item |
| `/ability mode [build\|combat]` | `yapabilities.use` | Swap or set build/combat hotbar page |
| `/ability bind <1-6> [id]` | `yapabilities.bar` | Bind ability to bar slot (clears if id omitted) |
| `/ability clear` | `yapabilities.bar` | Clear all bar bindings |
| `/cast <id>` | combat | Delegates to ability engine when loaded |
| `/yapabilities reload` | `yapabilities.admin` | Reload config + ability/effect YAML packs (hot) |

## Ability hotbar (dual page)

MMO **two-page hotbar** in **`yap-abilities.jar`**:

| Page | Hotbar | Behavior |
|------|--------|----------|
| **Build** (default) | **1–9** | Normal Minecraft — blocks, tools, full row |
| **Combat** | **1–3** weapons · **4–9** cast abilities | Press **4–9** to cast; weapon keys work normally |

### Swap build ↔ combat

| Input | Config key |
|-------|------------|
| **Middle mouse** (pick block) | `PICK_BLOCK` |
| **Swap hands** (`F` — rebind to middle mouse in Minecraft Controls) | `SWAP_HANDS` |
| **Sneak + Q** (drop) | `SNEAK_DROP` |
| **Command** | `/ability mode` · `/ability mode build` · `/ability mode combat` |

Config: `plugins/YaPAbilities/config.yml` → `ability-bar.*`  
Bindings: `/ability bind 1 wind_strike` … `/ability bar`  
Persistence: `plugins/YaPAbilities/bars.yml` (ability ids; weapon rows stay in-session)

RS **skills** stay passive — only **abilities** bind to combat keys 4–9.

## Ability book (spellbook GUI)

Browse **unlocked** abilities (gated by RS skill levels in each ability’s `min-level`) and bind them to combat keys **4–9** without memorizing ids.

| Input | Action |
|-------|--------|
| **`/abilities`** or **`/ability book`** | Open the book |
| **Ability Tome** (right-click) | Open the book — auto-given on first join when enabled |
| **`/ability tome`** | Get a replacement tome |
| **Bedrock** | Same commands open a **form UI** (category → ability → slot) |

### Java GUI

- **Category tabs** — All · Magic · Ranged · Melee · Prayer · Utility  
- **Ability grid** — paginated; locked abilities shown gray when `show-locked: true`  
- **Bottom row** — combat bar slots (keys 4–9)  
- **Drag** an ability onto a slot, **shift-click** an ability for the first empty slot, **right-click** a slot to clear  
- **Clear all** button removes every binding  

Config: `plugins/YaPAbilities/config.yml` → `ability-book.*`  
Persistence: same `bars.yml` as `/ability bind` — book and commands stay in sync.

### Web dashboard

**Gameplay → MMO** tab (`GET /api/mmo`) shows ability catalog count, dual hotbar/book flags, and online combat bar bindings. Admins can hot-reload packs with **Reload abilities** (`POST {"action":"reload-abilities"}`). See [WEB_DASHBOARD.md](../ops/WEB_DASHBOARD.md).

## YAML schema (ability)

```yaml
abilities:
  wind_strike:
    name: Wind Strike
    category: magic        # magic|ranged|melee|prayer|utility
    min-level:
      magic: 1
    costs:
      prayer: 1
      runes:
        FEATHER: 1
        LAPIS_LAZULI: 1
    cooldown: 0
    range: 20
    target: raycast          # raycast|self|none
    target-filter: undead    # optional
    cast:
      - type: vfx
        particle: CLOUD
        count: 10
      - type: sound
        sound: ENTITY_BREEZE_WIND_CHARGE_BURST
      - type: xp
        skill: magic
        amount: 12
        on-cast: true
    projectile:
      entity: SNOWBALL
      speed: 1.2
      max-ticks: 45
      trail:
        particle: CLOUD
        count: 3
        interval: 2
    on-hit:
      - type: damage
        style: magic         # magic|ranged|melee
        max-hit: 4
      - type: knockback
        power: 0.25
      - type: debuff
        id: poison
        stacks: 1
```

### Effect types

| Type | Purpose |
|------|---------|
| `damage` | Combat roll via `AbilityCombatBridge` (yap-combat) |
| `heal` | Restore HP |
| `vfx` / `sound` | Cosmetics |
| `buff` / `debuff` | Apply status effect by id |
| `knockback` | Velocity push |
| `xp` | Grant skill XP (`on-cast: true` for cast phase) |
| `teleport` | Forward blink (`distance`) |
| `velocity` | Direct velocity set |

## Status effects

Defined in `effects/*.yml`:

```yaml
effects:
  poison:
    name: Poison
    kind: debuff
    duration-ticks: 100
    tick-interval: 20
    tick:
      - type: damage
        style: magic
        max-hit: 2
    modifiers:
      speed: 0.95
```

Prayer buff abilities reference matching effect ids (e.g. `protect_melee`).

## Regenerating packs

```bash
python3 scripts/generate-ability-pack.py
```

Writes 233 abilities into `abilities-plugin/src/main/resources/abilities/`.

## API

```java
AbilityServices.find().ifPresent(s -> s.cast(player, "wind_strike"));
StatusEffectServices.find().ifPresent(s -> s.apply(target, "poison", sourceId, 1));
AbilityCombatServices.find(); // registered by yap-combat
```

## Smoke

```bash
./scripts/validate-mmo-content.sh
# optional live: ./scripts/smoke-folia-plugins.sh
```

---

## M7 — Advanced mechanics + client graphics

**Status:** Shipped (M7)

### Combat mechanics

| Feature | YAML | Description |
|---------|------|-------------|
| **AoE** | `on-hit: type: aoe` or `target: area` | Radius damage to filtered targets |
| **Homing** | `projectile.homing: true` | Steers toward locked/nearest target |
| **Splash** | `projectile.splash-radius: N` | AoE on projectile hit/expire |
| **Chain** | `on-hit: type: chain` | Jumps damage across nearby mobs |
| **Conditions** | `conditions:` block | Cast gates (HP%, status, mainhand, air/ground) |
| **Animation** | `cast: type: animation` | `swing` / `cast` / `channel` / `slam` + optional pose |
| **Delay** | `type: delay` + `ticks` | Sequence cast/hit effects across ticks |

Example showcase abilities: `showcase_m7.yml` (`fireball_splash`, `homing_arc`, `chain_lightning`, …).

### Client graphics (cool cast FX)

| Feature | How |
|---------|-----|
| **Cast icon pulse** | ItemDisplay + CustomModelData at caster eye (scales/floats ~14 ticks) |
| **Spell projectile body** | ItemDisplay rides the projectile; vanilla entity hidden (`projectile.hide: true`) |
| **Particle shapes** | `shape: burst\|ring\|helix\|beam\|nova` on `type: vfx` |
| **Dust / block data** | `color: R,G,B` (DUST) · `block: DIRT` (BLOCK) |
| **Sustained aura** | `ticks` + `interval` on VFX |
| **Impact burst** | Auto nova + sound when projectile lands |
| **Hand sparkles** | Crit/end-rod at hands on animation |

Resource pack overlay: `resourcepacks/yap-abilities/` (merged into `yapcore-default.zip` with `-PyapGameplay=true`).

**Icon base item:** `CLAY_BALL` (UI-only token — does not override swords, blaze rods, paper/vehicles, or rune items).

| Range | Use |
|-------|-----|
| 79000 | Combat level (skills GUI) |
| 79001–79013 | RS skills |
| 78010–78015 | Showcase abilities |
| 78200+ | Generated abilities |

Regenerate:

```bash
python3 scripts/generate-ability-pack.py
python3 scripts/generate-mmo-icons.py
```

```yaml
cast:
  - type: animation
    style: cast          # swing|offhand|both|cast|channel|slam
    pulses: 2
    pose: glow           # glow|slow|levitate
  - type: vfx
    particle: FLAME
    shape: helix
    count: 22
  - type: delay
    ticks: 4
projectile:
  entity: SNOWBALL
  hide: true
  scale: 0.95
  trail:
    particle: FLAME
    count: 6
    interval: 1
```

### Bedrock spellbook

- MMO hub adds **Abilities** panel (paginated, tap-to-cast)
- `/mmoui abilities [category] [page]`

## Roadmap (post-M7)

- Full per-spell resource pack art (all 233 CMD models)
- Bedrock animation controller bridge
- AoE shapes beyond circle
- Unify with combat-plugin `StatusEffectService` (single buff registry)
