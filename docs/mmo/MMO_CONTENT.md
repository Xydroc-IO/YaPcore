# YaP MMO content baseline

Shipped YAML packs for a themed survival / MMO server on **YaP-Folia**.

| Content | Bare minimum | Baseline pack |
|---------|----------------|---------------|
| Quests | 20–100 | **100** (7 compendium files) |
| Bosses | 20 | **20** (4 boss packs) |
| Recipes | 75–200 | **151** (smithing, cooking, crafting, herblore + extended packs) |

## Locations

| Type | Path |
|------|------|
| Quests | `plugins/YaPMmoContent/quests/*.yml` (also read by YaPNpcs) |
| Bosses | `plugins/YaPMmoContent/bosses/*.yml` |
| Recipes | `plugins/YaPCrafting/recipes/*.yml` |
| Areas | `plugins/YaPMmoContent/areas.yml` (`mining_guild`, `fishing_spot`, `mines`) |

## Quest compendium (100)

Full design + operator setup: **[MMO_QUESTS.md](MMO_QUESTS.md)**.

| File | Tier |
|------|------|
| `compendium_tier_01.yml` | Adventurer's Beginning (15) |
| `compendium_tier_02.yml` | Rising Hero (15) |
| `compendium_tier_03.yml` | Elite Guardian (15) |
| `compendium_tier_04.yml` | Master Craftsman (15) |
| `compendium_tier_05.yml` | Legendary Hero (15) |
| `compendium_tier_06.yml` | Ascended One (15) |
| `compendium_ultimate.yml` | Completionist (10) |

### Intro flow

1. **First Steps** — talk to Mayor NPC → unlock `/kit adventurer`
2. **First Steps in Mining** — Mining 5 → unlock `mines` teleport
3. **Goblin Menace** — kill 20 scouts (zombies) outside spawn → gold + gear

```bash
python3 scripts/content/generate-mmo-quest-compendium.py
./gradle installGameplayDefaults
```

## Regenerate recipes / bosses

```bash
python3 scripts/content/generate-mmo-baseline-pack.py   # recipes + bosses (does not replace quests)
./gradle installGameplayDefaults
gradle :crafting-plugin:test :mmo-content-plugin:shadowJar
```

> Quests are owned by `generate-mmo-quest-compendium.py`. Do not reintroduce the old 35-quest chain files.

## Bosses (20)

Split across `bosses/pack_01.yml` … `pack_04.yml`. Adjust coordinates in YAML for your world or use `/yapmmo` admin tools after placing arenas.

## Recipes (151)

| File | Theme |
|------|-------|
| `smithing.yml` / `smithing_extended.yml` | Bars, armor, tools |
| `cooking.yml` / `cooking_extended.yml` | Food, potions-lite |
| `crafting.yml` / `crafting_extended.yml` | Utility blocks, gear |
| `herblore.yml` | Herblore-style mixes |
