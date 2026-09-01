# YaPcore printable PDFs

Generated from the Markdown identity, ops, and whitepaper docs.
**PDFs are gitignored** — regenerate locally; commit Markdown sources only.

All sources below are written for the **current product path**: **YaP-Folia**
(`folia-jar-source=build`), not stock PaperMC Folia. See [../folia/FOLIA_FORK.md](../folia/FOLIA_FORK.md).

| PDF | Source |
|-----|--------|
| `PLAIN_ENGLISH.pdf` | [../overview/PLAIN_ENGLISH.md](../overview/PLAIN_ENGLISH.md) |
| `WHAT_WE_ARE.pdf` | [../overview/WHAT_WE_ARE.md](../overview/WHAT_WE_ARE.md) |
| `FULL_RUNDOWN.pdf` | [../overview/FULL_RUNDOWN.md](../overview/FULL_RUNDOWN.md) |
| `COMPARE_ECOSYSTEM.pdf` | [../overview/COMPARE_ECOSYSTEM.md](../overview/COMPARE_ECOSYSTEM.md) |
| `FOLIA_FORKS_COMPARE.pdf` | [../folia/FOLIA_FORKS_COMPARE.md](../folia/FOLIA_FORKS_COMPARE.md) |
| `COMPARISON_BRIEF.pdf` | [../overview/COMPARISON_BRIEF.md](../overview/COMPARISON_BRIEF.md) |
| `QUICK_START.pdf` | [../start/QUICK_START.md](../start/QUICK_START.md) |
| `DEFAULTS.pdf` | [../start/DEFAULTS.md](../start/DEFAULTS.md) |
| `SECRETS.pdf` | [../start/SECRETS.md](../start/SECRETS.md) |
| `PRIVACY_POLICY.pdf` | [../start/PRIVACY_POLICY.md](../start/PRIVACY_POLICY.md) |
| `TERMS_OF_USE.pdf` | [../start/TERMS_OF_USE.md](../start/TERMS_OF_USE.md) |
| `TESTING.pdf` | [../start/TESTING.md](../start/TESTING.md) |
| `RELEASES.pdf` | [../start/RELEASES.md](../start/RELEASES.md) |
| `RELEASE_NOTES.pdf` | [../start/RELEASE_NOTES.md](../start/RELEASE_NOTES.md) |
| `PROJECT_STATUS.pdf` | [../overview/PROJECT_STATUS.md](../overview/PROJECT_STATUS.md) |
| `COMPLETION_BACKLOG.pdf` | [../overview/COMPLETION_BACKLOG.md](../overview/COMPLETION_BACKLOG.md) |
| `PRIVACY_POLICY.pdf` | [../start/PRIVACY_POLICY.md](../start/PRIVACY_POLICY.md) |
| `TERMS_OF_USE.pdf` | [../start/TERMS_OF_USE.md](../start/TERMS_OF_USE.md) |
| `PLUGINS.pdf` | [../plugins/PLUGINS.md](../plugins/PLUGINS.md) |
| `YAPDB.pdf` | [../data/YAPDB.md](../data/YAPDB.md) |
| `MARIADB.pdf` | [../data/MARIADB.md](../data/MARIADB.md) |
| `PLAYERDATA.pdf` | [../data/PLAYERDATA.md](../data/PLAYERDATA.md) |
| `EDGE_HARDEN.pdf` | [../network/EDGE_HARDEN.md](../network/EDGE_HARDEN.md) |
| `LAGGUARD.pdf` | [../plugins/LAGGUARD.md](../plugins/LAGGUARD.md) |
| `FOLIA_FORK.pdf` | [../folia/FOLIA_FORK.md](../folia/FOLIA_FORK.md) |
| `YAP_LINK.pdf` | [../network/YAP_LINK.md](../network/YAP_LINK.md) |
| `YAP_LINK_NATIVE.pdf` | [../network/YAP_LINK_NATIVE.md](../network/YAP_LINK_NATIVE.md) |
| `VIA_GEYSER_PARITY.pdf` | [../protocol/VIA_GEYSER_PARITY.md](../protocol/VIA_GEYSER_PARITY.md) |
| `VEHICLES.pdf` | [../plugins/VEHICLES.md](../plugins/VEHICLES.md) |
| `STACKER.pdf` | [../plugins/STACKER.md](../plugins/STACKER.md) |
| `WEB_DASHBOARD.pdf` | [../ops/WEB_DASHBOARD.md](../ops/WEB_DASHBOARD.md) |
| `MMO_SKILLS.pdf` | [../mmo/MMO_SKILLS.md](../mmo/MMO_SKILLS.md) |
| `MMO_COMBAT.pdf` | [../mmo/MMO_COMBAT.md](../mmo/MMO_COMBAT.md) |
| `MMO_ABILITIES.pdf` | [../mmo/MMO_ABILITIES.md](../mmo/MMO_ABILITIES.md) |
| `MMO_PHASES.pdf` | [../mmo/MMO_PHASES.md](../mmo/MMO_PHASES.md) |
| `YAPCORE_WHITEPAPER.pdf` | [../whitepaper/YAPCORE_WHITEPAPER.md](../whitepaper/YAPCORE_WHITEPAPER.md) |
| `YAPCORE_WHITEPAPER_PLAIN_ENGLISH.pdf` | [../whitepaper/YAPCORE_WHITEPAPER_PLAIN_ENGLISH.md](../whitepaper/YAPCORE_WHITEPAPER_PLAIN_ENGLISH.md) |

Regenerate from repo root:

```bash
chmod +x scripts/export-docs-pdf.sh
./scripts/export-docs-pdf.sh
```

Requires `python3` + the `markdown` package, and `chromium` (or Chrome).
