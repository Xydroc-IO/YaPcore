#!/usr/bin/env bash
# Fail when scalar values differ between config/defaults seeds and jar resources
# for the plugins tuned in the typical-SMP defaults pass. Comment-only and
# jar-only keys are allowed.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

python3 - "$ROOT" <<'PY'
import re
import sys
from pathlib import Path

root = Path(sys.argv[1])

# seed relative path under config/defaults/plugins → jar resource path
PAIRS = [
    ("YaPDisasters/config.yml", "yap-first-party/gameplay/disasters-plugin/src/main/resources/config.yml"),
    ("YaPChat/config.yml", "yap-first-party/core-network/chat-plugin/src/main/resources/config.yml"),
    ("YaPPlayerData/config.yml", "yap-first-party/core-network/playerdata-plugin/src/main/resources/config.yml"),
    ("YaPMap/config.yml", "yap-first-party/core-network/map-plugin/src/main/resources/config.yml"),
    ("YaPCommands/config.yml", "yap-first-party/core-network/commands-plugin/src/main/resources/config.yml"),
    ("YaPCommands/commands.yml", "yap-first-party/core-network/commands-plugin/src/main/resources/commands.yml"),
    ("YaPDiscord/config.yml", "yap-first-party/core-network/discord-plugin/src/main/resources/config.yml"),
    ("YaPTab/config.yml", "yap-first-party/core-network/tab-plugin/src/main/resources/config.yml"),
]


def strip_comment(line: str) -> str:
    in_sq = in_dq = False
    out = []
    for i, ch in enumerate(line):
        if ch == "'" and not in_dq:
            in_sq = not in_sq
        elif ch == '"' and not in_sq:
            in_dq = not in_dq
        elif ch == "#" and not in_sq and not in_dq:
            break
        out.append(ch)
    return "".join(out).rstrip()


def leaves(text: str) -> dict[str, str]:
    """Map dotted paths to scalar / inline-list string values (best-effort)."""
    stack: list[tuple[int, str]] = []
    out: dict[str, str] = {}
    for raw in text.splitlines():
        line = strip_comment(raw)
        if not line.strip():
            continue
        indent = len(line) - len(line.lstrip(" "))
        s = line.strip()
        if s.startswith("- "):
            # list item under current path — fold into parent as multi-value marker
            if not stack:
                continue
            path = ".".join(p for _, p in stack)
            item = s[2:].strip()
            prev = out.get(path)
            out[path] = item if prev is None else prev + "\n" + item
            continue
        m = re.match(r"^([\w.-]+)\s*:\s*(.*)$", s)
        if not m:
            continue
        key, val = m.group(1), m.group(2).strip()
        while stack and stack[-1][0] >= indent:
            stack.pop()
        stack.append((indent, key))
        path = ".".join(p for _, p in stack)
        if val == "" or val in ("|", ">", "|-"):
            # nested mapping / block — path is a container, not a leaf yet
            continue
        out[path] = val
    return out


failed = False
for seed_rel, jar_rel in PAIRS:
    seed = root / "config/defaults/plugins" / seed_rel
    jar = root / jar_rel
    if not seed.is_file():
        print(f"MISSING seed: {seed}")
        failed = True
        continue
    if not jar.is_file():
        print(f"MISSING jar resource: {jar}")
        failed = True
        continue
    a = leaves(seed.read_text(encoding="utf-8"))
    b = leaves(jar.read_text(encoding="utf-8"))
    shared = sorted(set(a) & set(b))
    diffs = [(k, a[k], b[k]) for k in shared if a[k] != b[k]]
    if diffs:
        failed = True
        print(f"VALUE DRIFT: {seed_rel} ↔ {jar_rel}")
        for k, av, bv in diffs:
            print(f"  {k}: seed={av!r} jar={bv!r}")
    else:
        print(f"OK {seed_rel} ({len(shared)} shared leaves)")

if failed:
    sys.exit(1)
print("Seed↔jar value drift check OK")
PY
