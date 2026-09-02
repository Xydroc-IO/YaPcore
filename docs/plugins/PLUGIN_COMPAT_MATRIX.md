# Plugin compatibility matrix (Phase 16)

Curated classification for **common third-party jars** on the YaPcore **Folia product path**.
YaPcore does **not** promise to run arbitrary Paper plugins on Folia — this matrix gives
operators clarity and dashboard warnings.

**Machine-readable source:** `src/main/resources/plugin-compat-matrix.json` (loaded by
dashboard Plugins tab).

## Status values

| Status | Meaning |
|--------|---------|
| `native` | First-party YaP jar — use this |
| `works` | Known to work with Folia build or as API bridge |
| `folia-build` | Requires a Folia-specific fork — test on copy |
| `broken` | Do not install — use native alternative |
| `unknown` | Not classified — test on a copy |

## Native replacements (quick reference)

| Common plugin | YaP native |
|---------------|------------|
| LuckPerms | `yap-perms.jar` |
| EssentialsX | `yap-essentials.jar` |
| CoreProtect | `yap-protect.jar` |
| WorldEdit / FAWE | `yap-world.jar` + `WorldEdit.jar` shim (FAWE-class first-party; do not install stock WE/FAWE on Folia) |
| WorldGuard | `yap-playerdata.jar` (claims + flags) |
| Via\* / Geyser / Floodgate | Built-in Phase 4 stack |
| Velocity | `yap-link.jar` |
| DiscordSRV | `yap-discord.jar` |
| Dynmap / BlueMap | `yap-map.jar` |
| TAB / NametagEdit | `yap-tab.jar` |
| Citizens | `yap-playerdata.jar` (NPC/quests) |
| LiteBans / AdvancedBan | `yap-moderation.jar` |
| ChatControl / VentureChat | `yap-chat.jar` |
| Matrix / Vulcan AC | `yap-guard.jar` |

## Dashboard

Plugins tab shows **compat status** and **native alternative** badge per installed jar.
Warnings count appears on **Status → Network health**.

## Matrix size

The JSON ships **≥50** classified plugins/patterns. Extend by adding entries to
`plugin-compat-matrix.json` — no code change required for new rows.

See also: [PLUGIN_COMPAT.md](PLUGIN_COMPAT.md), [PLUGIN_BACKCOMPAT.md](PLUGIN_BACKCOMPAT.md).
