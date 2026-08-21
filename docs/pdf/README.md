# YaPcore printable PDFs

Generated from the Markdown identity and whitepaper docs.
**PDFs are gitignored** — regenerate locally; commit Markdown sources only.

| PDF | Source |
|-----|--------|
| `PLAIN_ENGLISH.pdf` | [../PLAIN_ENGLISH.md](../PLAIN_ENGLISH.md) |
| `WHAT_WE_ARE.pdf` | [../WHAT_WE_ARE.md](../WHAT_WE_ARE.md) |
| `FULL_RUNDOWN.pdf` | [../FULL_RUNDOWN.md](../FULL_RUNDOWN.md) |
| `YAPCORE_WHITEPAPER.pdf` | [../whitepaper/YAPCORE_WHITEPAPER.md](../whitepaper/YAPCORE_WHITEPAPER.md) |
| `YAPCORE_WHITEPAPER_PLAIN_ENGLISH.pdf` | [../whitepaper/YAPCORE_WHITEPAPER_PLAIN_ENGLISH.md](../whitepaper/YAPCORE_WHITEPAPER_PLAIN_ENGLISH.md) |

Regenerate from repo root:

```bash
chmod +x scripts/export-docs-pdf.sh
./scripts/export-docs-pdf.sh
```

Requires `python3` + the `markdown` package, and `chromium` (or Chrome).
