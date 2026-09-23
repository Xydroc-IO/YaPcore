# Plugin how-to guides

**Audience:** players, helpers, and operators who need to *use* YaP plugins — not plugin authors.

Each guide answers: what it does, who uses it, step-by-step workflows, commands with examples, config locations, and common failures.

| Need | Where |
|------|--------|
| This folder (usage) | you are here |
| Full command tables | [COMMANDS.md](../../ops/COMMANDS.md) |
| Permission nodes | [PERMISSIONS.md](../../ops/PERMISSIONS.md) |
| API / architecture | [PLUGINS.md](../../plugins/PLUGINS.md) |
| Jar install tiers | [plugins/README.md](../../../plugins/README.md) |

## Doc quality bar

Every guide aims for:

1. **What & who** — one-paragraph purpose + player vs staff vs ops
2. **Prerequisites** — jars, DB, Link, pack, economy flags
3. **Quick start** — shortest path to a working feature
4. **Workflows** — numbered recipes (create X, wire Y, test Z)
5. **Commands** — tables with examples (not only names)
6. **Config** — file paths + important knobs
7. **Troubleshoot** — symptoms → fixes
8. **Related** — deeper reference links

## CORE + NETWORK

| Plugin | Jar | Guide |
|--------|-----|-------|
| YaPDB | `yap-db.jar` | [yapdb.md](yapdb.md) |
| YaPPerms | `yap-perms.jar` | [yapperms.md](yapperms.md) |
| YaPPlayerData | `yap-playerdata.jar` | [yapplayerdata.md](yapplayerdata.md) |
| YaPClaims | `yap-claims.jar` | [yapclaims.md](yapclaims.md) |
| YaPModeration | `yap-moderation.jar` | [yapmoderation.md](yapmoderation.md) |
| YaPEssentials | `yap-essentials.jar` | [yapessentials.md](yapessentials.md) |
| YaPAdmin | `yap-admin.jar` | [yapadmin.md](yapadmin.md) |
| YaPChat | `yap-chat.jar` | [yapchat.md](yapchat.md) |
| YaPCommands | `yap-commands.jar` | [yapcommands.md](yapcommands.md) |
| YaPTab | `yap-tab.jar` | [yaptab.md](yaptab.md) |
| YaPNpcs | `yap-npcs.jar` | [yapnpcs.md](yapnpcs.md) |
| YaPPortals | `yap-portals.jar` | [yapportals.md](yapportals.md) |
| YaPRegions | `yap-regions.jar` | [yapregions.md](yapregions.md) |
| YaPProtect | `yap-protect.jar` | [yapprotect.md](yapprotect.md) |
| YaPWorld | `yap-world.jar` | [yapworld.md](yapworld.md) |
| WorldEdit shim | `WorldEdit.jar` | [worldedit.md](worldedit.md) |
| YaPHolo | `yap-holo.jar` | [yapholo.md](yapholo.md) |
| YaPLib | `yap-lib.jar` | [yaplib.md](yaplib.md) |
| YaPMap | `yap-map.jar` | [yapmap.md](yapmap.md) |
| YaPPregen | `yap-pregen.jar` | [yappregen.md](yappregen.md) |
| YaPPacks | `yap-packs.jar` | [yappacks.md](yappacks.md) |
| YaPGuard | `yap-guard.jar` | [yapguard.md](yapguard.md) |
| YaPLagGuard | `yap-lagguard.jar` | [yaplagguard.md](yaplagguard.md) |
| YaPFactions | `yap-factions.jar` | [yapfactions.md](yapfactions.md) |
| YaPItems | `yap-items.jar` | [yapitems.md](yapitems.md) |
| YaPDiscord | `yap-discord.jar` | [yapdiscord.md](yapdiscord.md) |
| YaPTebex | `yap-tebex.jar` / `tebex.jar` | [yaptebex.md](yaptebex.md) |
| YaPFoliaBridge | `yap-folia-bridge.jar` | [yapfoliabridge.md](yapfoliabridge.md) |
| PlaceholderAPI | `yap-placeholderapi.jar` | [placeholderapi.md](placeholderapi.md) |
| Floodgate | `yap-floodgate.jar` | [yapfloodgate.md](yapfloodgate.md) |
| YaPBedrockUI | `yap-bedrock-ui.jar` | [yapbedrockui.md](yapbedrockui.md) |
| YaPTailor | `yap-tailor.jar` | [yaptailor.md](yaptailor.md) |
| YaPBedrockBlocks | `yap-bedrock-blocks.jar` | [yapbedrockblocks.md](yapbedrockblocks.md) |

## GAMEPLAY (opt-in)

| Plugin | Jar | Guide |
|--------|-----|-------|
| YaP420 | `yap-420.jar` | [yap420.md](yap420.md) |
| YaPSkills | `yap-skills.jar` | [yapskills.md](yapskills.md) |
| YaPDungeons | `yap-dungeons.jar` | [yapdungeons.md](yapdungeons.md) |
| YaPMobs | stacker + leveled | [yapmobs.md](yapmobs.md) |
| YaPDisasters | `yap-disasters.jar` | [yapdisasters.md](yapdisasters.md) |
| YaPGameplayKnobs | `yap-gameplay-knobs.jar` | [yapgameplayknobs.md](yapgameplayknobs.md) |
| YaPQoL | timber / excavator | [yapqol.md](yapqol.md) |

## Suggested paths

**New fleet from zero**

1. [yapdb.md](yapdb.md) → [yapplayerdata.md](yapplayerdata.md) → [yapperms.md](yapperms.md)
2. [yapessentials.md](yapessentials.md) (setspawn, RTP) → [yapportals.md](yapportals.md) → [yapnpcs.md](yapnpcs.md)
3. [yapchat.md](yapchat.md) · [yapmoderation.md](yapmoderation.md) · [yapadmin.md](yapadmin.md)

**Hub shop / cannabis**

1. [yap420.md](yap420.md) · [yapitems.md](yapitems.md) · [yapnpcs.md](yapnpcs.md) · [yappacks.md](yappacks.md)

**Survival land / claims**

1. [yapclaims.md](yapclaims.md) · [yapregions.md](yapregions.md) · [yapfactions.md](yapfactions.md)
