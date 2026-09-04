# PDF exports (local only)

Operator documentation lives as **Markdown** under [`docs/`](../).  
This folder holds optional local PDF prints — **gitignored**, never committed.

```bash
./scripts/export-docs-pdf.sh
# → docs/pdf/*.pdf (chromium headless)
```

Publish docs via the GitHub repo / wiki links, not binary PDFs in git.
