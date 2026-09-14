#!/usr/bin/env python3
"""Paper/Purpur vs YaP-Folia *scale* context — not a Folia peer MSPT cite.

Compares disclosed tick_model rows from the same stamp/scenario:

  compare-paper-scale.py a.json b.json [c.json ...]

Scale-win heuristic (tune after first real numbers):
  - At least one single_thread row is degraded: tps_1m_mean < 15 OR mspt_mean > 50
  - YaP regionized row stays playable: tps_1m_mean >= 18 AND mspt_mean < 50
  - Same players_target / scenario when recorded

Never emits "citeable ≥5% MSPT vs Paper" — tick models differ.

Exit codes:
  0 = scale win OR inconclusive (printed); always printable table
  2 = usage / missing tick_model
  3 = fairness fail (scenario / player target mismatch)
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

# Degraded single-thread thresholds (documented; tune after first stamp).
PAPER_TPS_COLLAPSE = 15.0
PAPER_MSPT_HOT = 50.0
# Playable YaP regionized thresholds.
YAP_TPS_OK = 18.0
YAP_MSPT_OK = 50.0


def load(p: Path) -> dict:
    return json.loads(p.read_text())


def main(argv: list[str]) -> int:
    if len(argv) < 2:
        print("usage: compare-paper-scale.py result.json [result.json ...]", file=sys.stderr)
        return 2

    rows: list[tuple[Path, dict]] = []
    for a in argv:
        p = Path(a)
        d = load(p)
        tm = d.get("tick_model")
        if not tm:
            print(f"ERROR: {p.name} missing tick_model — re-run with updated yap-mspt-bench", file=sys.stderr)
            return 2
        rows.append((p, d))

    scenarios = {str(d.get("scenario")) for _, d in rows}
    if len(scenarios) > 1:
        print(f"FAIRNESS FAIL: scenario mismatch {scenarios}", file=sys.stderr)
        return 3

    targets = {int(d.get("players_target", 0) or 0) for _, d in rows}
    if len(targets) > 1 and 0 not in targets:
        print(f"FAIRNESS FAIL: players_target mismatch {targets}", file=sys.stderr)
        return 3

    print("DISCLOSE: tick_model mismatch is the product claim (single_thread vs regionized).")
    print("This is NOT a Folia peer ≥5% MSPT cite.\n")
    print(f"{'label':<22} {'tick_model':<14} {'players':>7} {'mspt_mean':>10} {'tps_1m':>8}  file")
    print("-" * 90)

    single: list[dict] = []
    region: list[dict] = []
    for p, d in sorted(rows, key=lambda x: (str(x[1].get("tick_model")), float(x[1].get("mspt_mean", 0)))):
        label = str(d.get("label", p.stem))
        tm = str(d.get("tick_model"))
        players = int(d.get("players_end", d.get("players_target", 0)) or 0)
        mspt = float(d.get("mspt_mean", 0))
        tps = float(d.get("tps_1m_mean", 0))
        print(f"{label:<22} {tm:<14} {players:7d} {mspt:10.3f} {tps:8.3f}  {p.name}")
        if tm == "single_thread":
            single.append(d)
        elif tm == "regionized":
            region.append(d)

    if not single or not region:
        print("\nINCONCLUSIVE: need ≥1 single_thread and ≥1 regionized row.")
        return 0

    # Prefer yapcore / yap-folia-chassis as the YaP side when present.
    yap = None
    for d in region:
        lab = str(d.get("label", "")).lower()
        if "yap" in lab:
            yap = d
            break
    if yap is None:
        yap = region[0]

    yap_mspt = float(yap.get("mspt_mean", 0))
    yap_tps = float(yap.get("tps_1m_mean", 0))
    yap_ok = yap_tps >= YAP_TPS_OK and yap_mspt < YAP_MSPT_OK

    degraded = []
    for d in single:
        mspt = float(d.get("mspt_mean", 0))
        tps = float(d.get("tps_1m_mean", 0))
        if tps < PAPER_TPS_COLLAPSE or mspt > PAPER_MSPT_HOT:
            degraded.append(d)

    print()
    print(
        f"Heuristics: Paper collapse if tps_1m < {PAPER_TPS_COLLAPSE} or mspt > {PAPER_MSPT_HOT}; "
        f"YaP playable if tps_1m ≥ {YAP_TPS_OK} and mspt < {YAP_MSPT_OK}."
    )

    if yap_ok and degraded:
        names = ", ".join(str(d.get("label")) for d in degraded)
        print(
            f"SCALE WIN: YaP regionized stays playable "
            f"(mspt={yap_mspt:.2f}, tps={yap_tps:.2f}) while single_thread degraded: {names}"
        )
        return 0

    if not yap_ok:
        print(
            f"NO SCALE WIN YET: YaP not playable under thresholds "
            f"(mspt={yap_mspt:.2f}, tps={yap_tps:.2f}) — raise headroom or lower load."
        )
    elif not degraded:
        print(
            "NO SCALE WIN YET: Paper/Purpur still playable at this population — "
            "raise YAP_BENCH_PLAYERS until single_thread collapses."
        )
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
