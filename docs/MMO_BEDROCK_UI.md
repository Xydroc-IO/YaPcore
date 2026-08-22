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
- Skill XP action bar mirrored via `SkillFeedbackBridge`
- Combat sidebar refreshed on join, level-up, and timer (`sidebar.refresh-ticks`)

## Dependencies

`YaPSkills`, `YaPCombat`, `YaPCrafting`, `YaPMmoContent` (soft), `YaPBedrockUI`, `YaPFloodgate` (soft).

Native YaPcore Bedrock sessions use chassis `FormService` + `BedrockUiBridge.pushActionBar` /
`pushSidebar`. Link/Floodgate-only players use Paper action bar + scoreboard fallback.

## Smoke

```bash
SKIP_LIVE=1 ./scripts/smoke-mmo-m5.sh
./scripts/smoke-mmo-m5.sh
```
