# PDF exports (local only)

Operator and engineering documentation lives as **Markdown** under [`docs/`](../).  
This folder holds optional local PDF prints — **gitignored**, never committed.

```bash
./scripts/export-docs-pdf.sh
# → docs/pdf/*.pdf (chromium / google-chrome headless)
```

Priority exports include the whitepapers, [AI transparency](../start/AI_TRANSPARENCY.md),
licensing, Bedrock-feel product docs, crossplay / Link, releases, and soak cites.
The script also walks remaining tracked `docs/**/*.md`.

**Publish docs via the GitHub repo / wiki links, not binary PDFs in git.**
Redistributors who need offline binders may generate this folder locally after clone.
