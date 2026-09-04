#!/usr/bin/env python3
"""YaP MMO quest compendium — validate (default) or document Wave 3 objective types.

Usage:
  python3 scripts/content/generate-mmo-quest-compendium.py
  python3 scripts/content/generate-mmo-quest-compendium.py --validate
  python3 scripts/content/generate-mmo-quest-compendium.py --list-types

Source of truth for quest YAML:
  yap-first-party/gameplay/mmo-content-plugin/src/main/resources/quests/

After editing packs:
  ./gradlew installGameplayDefaults
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
QUESTS_DIR = ROOT / "yap-first-party/gameplay/mmo-content-plugin/src/main/resources/quests"

KNOWN_TYPES = {
    "BREAK_BLOCK",
    "GATHER",
    "KILL_MOB",
    "SKILL_LEVEL",
    "CRAFT_ITEM",
    "KILL_BOSS",
    "PLAYTIME",
    "ECONOMY_BALANCE",
    "ECONOMY_EARN",
    "PLACE_BLOCKS",
    "ENCHANT",
    "ANVIL_USE",
    "TALK",
}

# Quests rewritten in Wave 3 — must use these objective types (no silent proxies).
WAVE3_EXPECTATIONS: dict[str, set[str]] = {
    "t4_tycoon": {"ECONOMY_BALANCE"},
    "t4_architect": {"PLACE_BLOCKS"},
    "t5_full_enchant": {"ENCHANT"},
    "t5_anvil": {"ANVIL_USE"},
    "t6_veteran": {"PLAYTIME"},
    "t6_quest_master": {"TALK"},
    "t6_economy_king": {"ECONOMY_EARN"},
    "t6_architect": {"PLACE_BLOCKS"},
    "ult_completionist": {"TALK"},
    "ult_economy_dom": {"ECONOMY_BALANCE"},
    "ult_time": {"PLAYTIME"},
}

# Remaining intentional stand-ins (documented in YAML descriptions + MMO_QUESTS.md).
DOCUMENTED_STANDINS = (
    "Zombie (Goblin Scout stand-in)",
    "void_shade (Void Lord stand-in)",
    "Defence skill (Agility stand-in until RS agility ships)",
)

SILENT_PROXY_RE = re.compile(r"\bproxy\b", re.IGNORECASE)
TYPE_RE = re.compile(r"^\s*type:\s*([A-Za-z0-9_]+)\s*$", re.MULTILINE)
QUEST_ID_RE = re.compile(r"^  ([a-z0-9_]+):\s*$", re.MULTILINE)


def quest_files() -> list[Path]:
    return sorted(QUESTS_DIR.glob("compendium_*.yml"))


def parse_quest_types(text: str) -> dict[str, list[str]]:
    """Map quest id -> objective type list (best-effort line scan)."""
    lines = text.splitlines()
    current: str | None = None
    in_objectives = False
    out: dict[str, list[str]] = {}
    for line in lines:
        m = QUEST_ID_RE.match(line)
        if m and not line.startswith("   "):
            current = m.group(1)
            out.setdefault(current, [])
            in_objectives = False
            continue
        if current is None:
            continue
        if re.match(r"^\s{4}objectives:\s*$", line):
            in_objectives = True
            continue
        if in_objectives and re.match(r"^\s{4}\w", line) and not line.strip().startswith("-"):
            in_objectives = False
        if in_objectives:
            tm = re.match(r"^\s+type:\s*([A-Za-z0-9_]+)\s*$", line)
            if tm:
                out[current].append(tm.group(1).upper())
    return out


def validate() -> int:
    if not QUESTS_DIR.is_dir():
        print(f"ERROR: missing quests dir {QUESTS_DIR}", file=sys.stderr)
        return 1

    errors: list[str] = []
    warnings: list[str] = []
    all_types: set[str] = set()
    found_wave3: dict[str, list[str]] = {}

    for path in quest_files():
        text = path.read_text(encoding="utf-8")
        for m in TYPE_RE.finditer(text):
            t = m.group(1).upper()
            all_types.add(t)
            if t not in KNOWN_TYPES:
                errors.append(f"{path.name}: unknown objective type {t}")

        # Flag silent "proxy" wording (stand-in is OK when explicit).
        for i, line in enumerate(text.splitlines(), 1):
            if SILENT_PROXY_RE.search(line) and "stand-in" not in line.lower():
                warnings.append(f"{path.name}:{i}: contains 'proxy' — prefer real types or 'stand-in' docs")

        types_by_quest = parse_quest_types(text)
        for qid, expected in WAVE3_EXPECTATIONS.items():
            if qid in types_by_quest:
                found_wave3[qid] = types_by_quest[qid]
                actual = set(types_by_quest[qid])
                if not expected.issubset(actual):
                    errors.append(
                        f"{path.name}: {qid} expected types {sorted(expected)}, got {types_by_quest[qid]}"
                    )

    missing = [qid for qid in WAVE3_EXPECTATIONS if qid not in found_wave3]
    for qid in missing:
        errors.append(f"Wave 3 quest missing from packs: {qid}")

    print(f"Validated {len(quest_files())} compendium files under {QUESTS_DIR.relative_to(ROOT)}")
    print(f"Objective types seen: {', '.join(sorted(all_types))}")
    print("Documented stand-ins:")
    for s in DOCUMENTED_STANDINS:
        print(f"  - {s}")
    for w in warnings:
        print(f"WARN: {w}")
    if errors:
        for e in errors:
            print(f"ERROR: {e}", file=sys.stderr)
        return 1
    print("OK — Wave 3 expectations met; no unknown objective types.")
    return 0


def list_types() -> int:
    print("Supported QuestDefinition.ObjectiveType values:")
    for t in sorted(KNOWN_TYPES):
        print(f"  {t}")
    print("\nYAML keys:")
    print("  PLAYTIME         → minutes:")
    print("  ECONOMY_BALANCE  → min-balance:")
    print("  ECONOMY_EARN     → amount: (gold earned via deposits)")
    print("  PLACE_BLOCKS     → material: (optional; omit/AIR = any) + amount:")
    print("  ENCHANT          → amount:")
    print("  ANVIL_USE        → amount:")
    print("  TALK             → npc-id: + amount:")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--validate", action="store_true", help="Validate quest YAML (default)")
    parser.add_argument("--list-types", action="store_true", help="Print supported objective types")
    args = parser.parse_args()
    if args.list_types:
        return list_types()
    return validate()


if __name__ == "__main__":
    sys.exit(main())
