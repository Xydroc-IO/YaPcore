#!/usr/bin/env bash
# Export identity + whitepaper Markdown docs to docs/pdf/*.pdf
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/docs/pdf"
mkdir -p "$OUT"

python3 << PY
from pathlib import Path
import markdown

ROOT = Path("$ROOT")
OUT = Path("$OUT")

files = [
    ROOT / "docs" / "PLAIN_ENGLISH.md",
    ROOT / "docs" / "WHAT_WE_ARE.md",
    ROOT / "docs" / "FULL_RUNDOWN.md",
    ROOT / "docs" / "COMPARE_ECOSYSTEM.md",
    ROOT / "docs" / "COMPARISON_BRIEF.md",
    ROOT / "docs" / "YAP_LINK.md",
    ROOT / "docs" / "YAP_LINK_NATIVE.md",
    ROOT / "docs" / "VIA_GEYSER_PARITY.md",
    ROOT / "docs" / "VEHICLES.md",
    ROOT / "docs" / "STACKER.md",
    ROOT / "docs" / "WEB_DASHBOARD.md",
    ROOT / "docs" / "whitepaper" / "YAPCORE_WHITEPAPER.md",
    ROOT / "docs" / "whitepaper" / "YAPCORE_WHITEPAPER_PLAIN_ENGLISH.md",
]

css = """
@page { margin: 18mm 16mm; size: letter; }
html { font-size: 11pt; }
body {
  font-family: "DejaVu Sans", "Liberation Sans", "Noto Sans", Arial, sans-serif;
  color: #1a1a1a;
  line-height: 1.45;
  max-width: 100%;
}
h1 { font-size: 1.7rem; margin: 0 0 0.6em; border-bottom: 2px solid #222; padding-bottom: 0.3em; }
h2 { font-size: 1.25rem; margin: 1.4em 0 0.5em; border-bottom: 1px solid #ccc; padding-bottom: 0.2em; }
h3 { font-size: 1.05rem; margin: 1.1em 0 0.4em; }
blockquote {
  margin: 0.8em 0;
  padding: 0.5em 0.9em;
  border-left: 4px solid #555;
  background: #f6f6f6;
  color: #333;
}
code, pre {
  font-family: "DejaVu Sans Mono", "Liberation Mono", "Noto Sans Mono", monospace;
  font-size: 0.88em;
}
code { background: #f0f0f0; padding: 0.1em 0.3em; border-radius: 3px; }
pre {
  background: #f4f4f4;
  padding: 0.75em 1em;
  overflow-x: auto;
  border: 1px solid #ddd;
  border-radius: 4px;
  white-space: pre-wrap;
  word-break: break-word;
}
table { border-collapse: collapse; width: 100%; margin: 0.8em 0 1.1em; font-size: 0.92em; }
th, td { border: 1px solid #bbb; padding: 0.35em 0.55em; text-align: left; vertical-align: top; }
th { background: #eee; }
hr { border: none; border-top: 1px solid #ccc; margin: 1.4em 0; }
a { color: #0b57d0; text-decoration: none; }
"""

for md_path in files:
    body = markdown.markdown(
        md_path.read_text(encoding="utf-8"),
        extensions=["tables", "fenced_code", "sane_lists"],
    )
    title = md_path.stem.replace("_", " ")
    html = f"""<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8"/><title>{title}</title>
<style>{css}</style></head><body>{body}</body></html>
"""
    (OUT / f"{md_path.stem}.html").write_text(html, encoding="utf-8")
    print(f"html  {md_path.stem}")
PY

CHROME=""
for c in chromium chromium-browser google-chrome google-chrome-stable; do
  if command -v "$c" >/dev/null 2>&1; then CHROME="$c"; break; fi
done
if [[ -z "$CHROME" ]]; then
  echo "Need chromium or google-chrome to print PDFs" >&2
  exit 1
fi

cd "$OUT"
for f in PLAIN_ENGLISH WHAT_WE_ARE FULL_RUNDOWN COMPARE_ECOSYSTEM COMPARISON_BRIEF YAP_LINK YAP_LINK_NATIVE VIA_GEYSER_PARITY VEHICLES STACKER WEB_DASHBOARD YAPCORE_WHITEPAPER YAPCORE_WHITEPAPER_PLAIN_ENGLISH; do
  "$CHROME" --headless --disable-gpu --no-pdf-header-footer \
    --print-to-pdf="${f}.pdf" "file://${OUT}/${f}.html" >/dev/null 2>&1
  rm -f "${f}.html"
  echo "pdf   ${f}.pdf"
done
echo "done → $OUT"
