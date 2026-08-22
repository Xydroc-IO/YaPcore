# YaPcore printable PDFs

Generated from the Markdown identity, ops, and whitepaper docs.
**PDFs are gitignored** — regenerate locally; commit Markdown sources only.

| PDF | Source |
|-----|--------|
| `PLAIN_ENGLISH.pdf` | [../PLAIN_ENGLISH.md](../PLAIN_ENGLISH.md) |
| `WHAT_WE_ARE.pdf` | [../WHAT_WE_ARE.md](../WHAT_WE_ARE.md) |
| `FULL_RUNDOWN.pdf` | [../FULL_RUNDOWN.md](../FULL_RUNDOWN.md) |
| `COMPARE_ECOSYSTEM.pdf` | [../COMPARE_ECOSYSTEM.md](../COMPARE_ECOSYSTEM.md) |
| `COMPARISON_BRIEF.pdf` | [../COMPARISON_BRIEF.md](../COMPARISON_BRIEF.md) |
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
