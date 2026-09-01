# YaP MMO Bedrock UI (M5)

Bedrock parity for the MMO stack via **FormService** forms and **BedrockUiBridge**
action bar / scoreboard mirroring.

## Plugins

| Jar | Role |
|-----|------|
| `yap-bedrock-ui.jar` | Registers `BedrockUiService` (chassis + Floodgate fallback) |
| `yap-mmo-bedrock.jar` | Skills panel, recipe/hiscore forms, XP + combat sidebar |

## Bedrock player UX

- `/skills` → Bedrock form hub (when `intercept-skills-command: true`)
- `/mmoui [skills|recipes|hiscores] [skill] [page]`
- Hub panels: **Skills** (per-skill detail), **Quests** (active objectives), **Abilities** spellbook, **Recipes** / **Hiscores** (skill picker → paginated list)
- Skill XP action bar mirrored via `SkillFeedbackBridge`
- Combat sidebar refreshed on join, level-up, and timer (`sidebar.refresh-ticks`)
- Cast feedback on Bedrock: action bar pulse from `AnimationSync` when abilities fire

## Dependencies

`YaPSkills`, `YaPCombat`, `YaPCrafting`, `YaPMmoContent` (soft), `YaPNpcs` (soft, quests panel), `YaPAbilities` (soft), `YaPBedrockUI`, `YaPFloodgate` (soft).

Native YaPcore Bedrock sessions use chassis `FormService` + `BedrockUiBridge.pushActionBar` /
`pushSidebar`. Link/Floodgate-only players use Paper action bar + scoreboard fallback.

## Smoke

```bash
./scripts/validate-mmo-content.sh
# Bedrock UI: ./scripts/smoke-bedrock-play.sh
```
