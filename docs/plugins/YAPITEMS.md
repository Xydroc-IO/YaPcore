# YaPItems

Folia-safe **custom items** for YaPcore: YAML registry, CustomModelData looks (resource pack), multi-ability keybinds, furniture (`ItemDisplay`), recipes, combat gear tags, and create wizards in **YaPAdmin** / **yap-staff**.

## Install

```bash
gradle :items-plugin:installIntoPlugins
# or
gradle installGameplayDefaults
```

Jar: `plugins/yap-items.jar`. Soft API: `com.yapcore.items.api.ItemService` (`yap-items-api`).

Rebuild the default pack so clients see custom models:

```bash
./scripts/build-default-resourcepack.sh
```

Overlay source: [`resourcepacks/yap-items/`](../../resourcepacks/yap-items/) (merged into `yapcore-default`).

Defaults seed: [`config/defaults/plugins/YaPItems/`](../../config/defaults/plugins/YaPItems/).

## Commands

| Command | Permission | Notes |
|---------|------------|-------|
| `/yapitems give <id> [amount] [player]` | `yapitems.give` | Rainbow pack ids: `rainbow_heart`, `rainbow_love`, `rainbow_blade`, `rainbow_axe`, `rainbow_bow`, `rainbow_wand`, `rainbow_star`, `rainbow_prism`, `rainbow_shield`, `rainbow_pick` |
| `/yapitems take <id> [amount] [player]` | `yapitems.take` | |
| `/yapitems list [filter]` | `yapitems.use` | `--ids` for compact id list (staff sync) |
| `/yapitems info <id>` | `yapitems.use` | |
| `/yapitems gui [player]` | `yapitems.gui` | Chest browser |
| `/yapitems reload` | `yapitems.admin` | |
| `/yapitems create <id> --name …` | `yapitems.create` | Writes `items/custom/<id>.yml` |
| `/yapitems delete <id>` | `yapitems.admin` | Custom folder only (not builtins) |
| `/yapitems cooldown <id> <duration>` | `yapitems.admin` | e.g. `8s`, `0s` |
| `/yapitems furniture remove` | `yapitems.admin` | Look-at display |

Alias: `/yitems`.

### Create flags

| Flag | Purpose |
|------|---------|
| `--template <id>` | Base look: sword, axe, spear, mace, bow, pickaxe, gem bases, `prop_*`, … |
| `--name` / `--nameb64` | Display name (`&` colors). Spaces / odd chars → `--nameb64` (staff uses this automatically) |
| `--abilities a:trig,b:trig` | Compact multi-ability + keybind (preferred on staff) |
| `--ability` + `--trigger` | Repeated ability blocks (legacy / admin) |
| `--damage` | Ability damage (`-1` / `kill` = instakill) |
| `--range` / `--radius` / `--amount` | Reach, AoE / break blast, heal HP or max break count |
| `--effect` / `--duration` / `--amplifier` | Potion type(s) + ticks + level. Multiple: `--effect SPEED,STRENGTH,WATER_BREATHING` (max 6; shared duration/amplifier). Duration `-1` = unlimited. Amplifier `0`–`99` (level 1–100). **SPEED / JUMP_BOOST / LEVITATION / DOLPHINS_GRACE are soft-capped at amplifier 1 (level 2)** so movement stays playable. Underwater: `WATER_BREATHING`, `CONDUIT_POWER` |
| `--sound` / `--particle` / `--count` | Override ability FX (Sound / Particle enum names; particle count). Particle `RAINBOW` = multi-color dust burst |
| `--fx` / `--no-fx` | Enable or disable sound+particle burst |
| `--projectile` | `snowball` / `arrow` / `egg` / `ender_pearl` / `fireball` |
| `--cooldown` / `--cd` | Shared ability cooldown |
| `--gear-attack` / `--gear-strength` | Melee gear bonus tags |
| `--glow` / `--no-glow` | Enchantment glint |
| `--rainbow` / `--no-rainbow` | Animated rainbow display name |
| `--unbreakable` / `--no-unbreakable` | Never loses durability |
| `--enchants sharpness:5,unbreaking:3` | Real enchantments (id:level list) |
| `--enchant sharpness:5` | Same, repeatable |
| `--no-enchants` / `--clear-enchants` | Clear enchants (with `--replace`) |
| `--furniture` | Placeable prop |
| `--replace` / `--force` | Overwrite existing **custom** item |

## Authoring

Place YAML under `plugins/YaPItems/items/*.yml` (shipped examples) or `items/custom/` (wizard).

```yaml
my_blade:
  base: NETHERITE_SWORD
  name: "&bMy Blade"
  lore: ["&7Right-click abilities"]
  custom-model-data: 12020
  glow: true
  unbreakable: true
  gear: { attack: 4, strength: 1 }
  abilities:
    - trigger: RIGHT_CLICK
      type: lightning_dash
      cooldown: 8s
      params: { range: 8, damage: 40 }
    - trigger: RIGHT_CLICK
      type: effect
      cooldown: 12s
      params: { effects: [STRENGTH, SPEED], duration: 100, amplifier: 1 }
    - trigger: ATTACK
      type: smite_target
      cooldown: 3s
      params: { range: 12, damage: 8 }
```

Legacy singular `ability:` still works. New creates write `abilities:`.

**CMD range:** `12000–12999` (config `cmd-min` / `cmd-max`). Stacks store PDC `yap_item_id` + revision.

### Ability types

`message`, `effect`, `heal`, `feed`, `launch`, `dash`, `blink`, `lightning`, `lightning_dash`, `smite_target`, `explode`, `fireball`, `projectile`, `pull`, `push`, `ground_slam`, `absorb`, `cleanse`, `command_player`, `command_console`, `sound`, `particle`, `give_item`, `break_block`, `repair`, `area_effect`.

Create UIs group these by **Combat · Movement · Self · Gathering · Utility** and filter by item base (weapon / tool / gem / prop). Toggle “show all” to override.

**`break_block`:** look **reach** (`range`), **blast** cube (`radius`, `0` = single block), **max blocks** (`amount`).

**`effect` vs `area_effect`:** self potion vs enemy AoE — each has effect / duration / amplifier; AoE also uses radius.

**Triggers / keybinds** (per ability): `together` (same key as primary), `right_click`, `sneak_right_click`, `left_click`, `sneak_left_click`, `attack`, `drop` (Q), `swap_hands` (F), `consume`.

### Furniture

```yaml
furniture:
  enabled: true
  scale: 1.0
  break-drops: true
  place-sound: BLOCK_STONE_PLACE
```

Sneak + right-click a block face to place an `ItemDisplay`. **Left-click** the display to break (also sneak-right-click, or `/yapitems furniture remove`). Positions persist in `furniture.yml`.

### Recipes

Optional `recipe:` shaped/shapeless block on a definition; registered on enable/reload.

### Kits

In `kits.yml` item rows:

```yaml
- yap-item: stormblade
  amount: 1
```

Requires YaPItems enabled (resolved via `ItemService`).

### Combat

When `register-combat-service: true`, YaPItems registers `CombatService` and `gearBonusFor` reads definition `gear:` tags.

Real enchantments live under YAML `enchants:` and can be set from create UIs (admin **Enchants…**, staff enchant grid) or CLI `--enchants` / `--enchant`. Use `glow` for shine without a real enchant.

## Superadmin UI

| Surface | Path |
|---------|------|
| **YaPAdmin** chest | Hub → **Custom items** → create (category → base → build) · browse/manage · give · cooldown · reload |
| **yap-staff** (1.0.25+) | Hub → **Custom items…** → create/edit (categorized abilities, glow/unbreakable, enchant picker, break volume) · browse · CD |
| **CLI** | `/yapitems create …` as above |

Staff create sends an unsigned `chat_command` with compact `--abilities` and `--nameb64` when needed (avoids kick on long/fancy names). After installing plugin jars, **fully restart Folia** (hot-swap of nested classes is unsafe).

## Pack art

Original 64×64 pixel art (Tynker-style vibe only — do not rebundle third-party galleries).

Create-wizard templates use pack CMD slots for concrete bases (swords, tools, gems, props). Showcase examples keep unique CMDs (12001–12007, 12100).

## Permissions

**Use rule (vanilla-like):** if the player has the custom item, they can use it. No per-item use permission is required.

| Node | Default | Role |
|------|---------|------|
| `yapitems.use` | true | list/info |
| `yapitems.give` / `take` / `gui` / `create` | op | staff tools |
| `yapitems.craft` | true | craft registered recipes |
| `yapitems.admin` | op | reload/delete/furniture/cooldown (+ children above) |

Advanced (optional): a definition may set `permission:` / `ability.permission:` to rank-gate a specific item.

## Effects & VFX

Abilities always play baked-in FX (sounds, particle bursts, trails, beams, expanding rings). YAML `sound` / `particle` / `count` tune the look; `fx: false` mutes visuals. This is vanilla particle VFX — not client shaders or custom swing animations.

## Folia

Inventory/world mutates use `YapSched.entity` / `YapSched.region`. No global scheduler for those paths.

## See also

[COMMANDS.md](../ops/COMMANDS.md) · [ADMIN_MENU.md](../ops/ADMIN_MENU.md) · [PLUGIN_COMPAT_MATRIX.md](PLUGIN_COMPAT_MATRIX.md) · [plugins/README.md](../../plugins/README.md) · [CLIENTS_AND_PACKS.md](../network/CLIENTS_AND_PACKS.md)
