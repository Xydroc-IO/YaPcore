#!/usr/bin/env bash
# Export key Markdown docs to docs/pdf/*.pdf (chromium headless).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/docs/pdf"
mkdir -p "$OUT"

DOC_LIST=(
  "YAPCORE_WHITEPAPER|docs/whitepaper/YAPCORE_WHITEPAPER.md"
  "YAPCORE_WHITEPAPER_PLAIN_ENGLISH|docs/whitepaper/YAPCORE_WHITEPAPER_PLAIN_ENGLISH.md"
  "YAP_FOLIA_SOAK|docs/folia/YAP_FOLIA_SOAK.md"
  "CANVAS_PARITY|docs/folia/CANVAS_PARITY.md"
  "REAL_GAINS|docs/folia/REAL_GAINS.md"
  "QUICK_START|docs/start/QUICK_START.md"
  "WIKI|docs/WIKI.md"
  "DOCS_README|docs/README.md"
)

# Also export any other tracked markdown under docs/ (skip if already listed).
while IFS= read -r -d '' f; do
  rel="${f#"$ROOT/"}"
  stem="$(basename "$f" .md)"
  case "$stem" in
    YAPCORE_WHITEPAPER|YAPCORE_WHITEPAPER_PLAIN_ENGLISH|YAP_FOLIA_SOAK|CANVAS_PARITY|REAL_GAINS|QUICK_START|WIKI|README) continue ;;
  esac
  DOC_LIST+=("${stem}|${rel}")
done < <(find "$ROOT/docs" -type f -name '*.md' -print0 | sort -z)

export ROOT OUT
DOC_LIST_EXPORT="$(printf '%s\n' "${DOC_LIST[@]}")"
export DOC_LIST_EXPORT

python3 <<'PY'
from pathlib import Path
import markdown
import os

ROOT = Path(os.environ["ROOT"])
OUT = Path(os.environ["OUT"])

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

stems = []
seen = set()
for line in os.environ["DOC_LIST_EXPORT"].strip().splitlines():
    stem, rel = line.split("|", 1)
    if stem in seen:
        continue
    seen.add(stem)
    md_path = ROOT / rel
    if not md_path.is_file():
        print(f"skip missing {md_path}")
        continue
    body = markdown.markdown(
        md_path.read_text(encoding="utf-8"),
        extensions=["tables", "fenced_code", "sane_lists"],
    )
    title = stem.replace("_", " ")
    html = f"""<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8"/><title>{title}</title>
<style>{css}</style></head><body>{body}</body></html>
"""
    (OUT / f"{stem}.html").write_text(html, encoding="utf-8")
    stems.append(stem)
    print(f"html  {stem}")

(OUT / ".pdf-stems").write_text("\n".join(stems) + "\n", encoding="utf-8")
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
mapfile -t STEMS < .pdf-stems
for f in "${STEMS[@]}"; do
  echo "pdf   $f"
  "$CHROME" --headless --disable-gpu --no-pdf-header-footer \
    --print-to-pdf="${f}.pdf" "file://${OUT}/${f}.html" >/dev/null 2>&1
done

# Drop intermediate HTML from the tree (keep PDFs).
rm -f .pdf-stems *.html
echo "Wrote $(ls -1 "$OUT"/*.pdf | wc -l) PDFs under $OUT"
