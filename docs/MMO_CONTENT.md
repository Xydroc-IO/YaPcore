# YaP MMO content baseline

Shipped YAML packs meet **bare-minimum framework content** targets for a themed survival server.

| Content | Bare minimum | Baseline pack |
|---------|----------------|---------------|
| Quests | 20–100 | **35** (7 chain files) |
| Bosses | 20 | **20** (4 boss packs) |
| Recipes | 75–200 | **151** (smithing, cooking, crafting, herblore + extended packs) |

## Locations

| Type | Path |
|------|------|
| Quests | `plugins/YaPMmoContent/quests/*.yml` (also read by YaPNpcs) |
| Bosses | `plugins/YaPMmoContent/bosses/*.yml` |
| Recipes | `plugins/YaPCrafting/recipes/*.yml` |

## Regenerate baseline

```bash
python3 scripts/content/generate-mmo-baseline-pack.py
./scripts/validate-mmo-content.sh
gradle :crafting-plugin:test :mmo-content-plugin:shadowJar
```

The generator overwrites `smithing.yml`, `cooking.yml`, and `crafting.yml` plus all quest/boss chain files. Customize by editing YAML or forking the generator.

## Quest chains (35)

1. `starter_chain.yml` — tutorial (5)
2. `chain_mining.yml` — mining depth (5)
3. `chain_woodcutting.yml` — woodland (5)
4. `chain_fishing_cooking.yml` — coast + cooking level (5)
5. `chain_smithing.yml` — forge progression (5)
6. `chain_boss_hunter.yml` — mid-tier bounties (5)
7. `chain_combat.yml` — mob purge (5)

Add more packs by dropping new `quests/your_theme.yml` files — YaPNpcs loads every YAML in the MMO quests folder.

## Bosses (20)

Split across `bosses/pack_01.yml` … `pack_04.yml`. Adjust coordinates in YAML for your world or use `/yapmmo` admin tools after placing arenas.

## Recipes (151)

| File | Theme |
|------|-------|
| `smithing.yml` / `smithing_extended.yml` | Bars, armor, tools |
| `cooking.yml` / `cooking_extended.yml` | Food, potions-lite |
| `crafting.yml` / `crafting_extended.yml` | Utility blocks, gear |
| `herblore.yml` | Herblore-style mixes |

## Expanding toward 100 quests / 200 recipes

- Duplicate chain pattern in the generator or hand-author YAML
- Tie quests to faction ranks, game modes, or new skill areas
- Do **not** require code changes for new gather/kill/craft/boss quests
