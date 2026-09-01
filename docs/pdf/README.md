# YaPcore printable PDFs

Generated from the Markdown identity, ops, and whitepaper docs.
**PDFs are gitignored** — regenerate locally; commit Markdown sources only.

All sources below are written for the **current product path**: **YaP-Folia**
(`folia-jar-source=build`), not stock PaperMC Folia. See [../FOLIA_FORK.md](../FOLIA_FORK.md).

| PDF | Source |
|-----|--------|
| `PLAIN_ENGLISH.pdf` | [../PLAIN_ENGLISH.md](../PLAIN_ENGLISH.md) |
| `WHAT_WE_ARE.pdf` | [../WHAT_WE_ARE.md](../WHAT_WE_ARE.md) |
| `FULL_RUNDOWN.pdf` | [../FULL_RUNDOWN.md](../FULL_RUNDOWN.md) |
| `COMPARE_ECOSYSTEM.pdf` | [../COMPARE_ECOSYSTEM.md](../COMPARE_ECOSYSTEM.md) |
| `FOLIA_FORKS_COMPARE.pdf` | [../FOLIA_FORKS_COMPARE.md](../FOLIA_FORKS_COMPARE.md) |
| `COMPARISON_BRIEF.pdf` | [../COMPARISON_BRIEF.md](../COMPARISON_BRIEF.md) |
| `QUICK_START.pdf` | [../QUICK_START.md](../QUICK_START.md) |
| `DEFAULTS.pdf` | [../DEFAULTS.md](../DEFAULTS.md) |
| `RELEASES.pdf` | [../RELEASES.md](../RELEASES.md) |
| `PLUGINS.pdf` | [../PLUGINS.md](../PLUGINS.md) |
| `YAPDB.pdf` | [../YAPDB.md](../YAPDB.md) |
| `MARIADB.pdf` | [../MARIADB.md](../MARIADB.md) |
| `PLAYERDATA.pdf` | [../PLAYERDATA.md](../PLAYERDATA.md) |
| `EDGE_HARDEN.pdf` | [../EDGE_HARDEN.md](../EDGE_HARDEN.md) |
| `LAGGUARD.pdf` | [../LAGGUARD.md](../LAGGUARD.md) |
| `FOLIA_FORK.pdf` | [../FOLIA_FORK.md](../FOLIA_FORK.md) |
| `YAP_LINK.pdf` | [../YAP_LINK.md](../YAP_LINK.md) |
| `YAP_LINK_NATIVE.pdf` | [../YAP_LINK_NATIVE.md](../YAP_LINK_NATIVE.md) |
| `VIA_GEYSER_PARITY.pdf` | [../VIA_GEYSER_PARITY.md](../VIA_GEYSER_PARITY.md) |
| `VEHICLES.pdf` | [../VEHICLES.md](../VEHICLES.md) |
| `STACKER.pdf` | [../STACKER.md](../STACKER.md) |
| `WEB_DASHBOARD.pdf` | [../WEB_DASHBOARD.md](../WEB_DASHBOARD.md) |
| `YAPCORE_WHITEPAPER.pdf` | [../whitepaper/YAPCORE_WHITEPAPER.md](../whitepaper/YAPCORE_WHITEPAPER.md) |
| `YAPCORE_WHITEPAPER_PLAIN_ENGLISH.pdf` | [../whitepaper/YAPCORE_WHITEPAPER_PLAIN_ENGLISH.md](../whitepaper/YAPCORE_WHITEPAPER_PLAIN_ENGLISH.md) |

Regenerate from repo root:

```bash
chmod +x scripts/export-docs-pdf.sh
./scripts/export-docs-pdf.sh
```

Requires `python3` + the `markdown` package, and `chromium` (or Chrome).
