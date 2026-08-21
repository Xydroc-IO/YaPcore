#!/usr/bin/env python3
"""Rank highpop results — requires players_ok and comparable online counts."""
from __future__ import annotations

import json
import sys
from pathlib import Path

IDS = ("paper", "purpur", "leaf", "yapcore")


def main() -> int:
    if len(sys.argv) != 3:
        print(f"Usage: {sys.argv[0]} <results_dir> <stamp>", file=sys.stderr)
        return 2
    results = Path(sys.argv[1])
    stamp = sys.argv[2]
    runs: dict[str, dict] = {}
    for i in IDS:
        p = results / f"{stamp}-highpop-{i}.json"
        if p.exists():
            runs[i] = json.loads(p.read_text())

    if not runs:
        print("No highpop results")
        return 3

    print(f"highpop stamp={stamp} found={', '.join(runs)}")
    fair: dict[str, dict] = {}
    for name, d in runs.items():
        ps = int(d.get("players_start", 0))
        pe = int(d.get("players_end", 0))
        ok = bool(d.get("players_ok", False))
        print(
            f"  [{name}] mspt={float(d['mspt_mean']):.4f} tps={float(d.get('tps_1m_mean', 0)):.3f} "
            f"players={ps}→{pe} target={d.get('players_target')} "
            f"chunks={d.get('chunks_loaded_start')}→{d.get('chunks_loaded_end')} "
            f"entities={d.get('entities_start')} players_ok={ok}"
        )
        if not ok or ps < 1:
            print(f"FAIRNESS FAIL [{name}]: insufficient bot population")
            continue
        fair[name] = d

    if len(fair) < 2:
        print("VERDICT: INVALID — need ≥2 fair highpop runs")
        return 3

    # Cross-check player counts within 20%
    ref_p = max(int(d["players_start"]) for d in fair.values())
    for name, d in list(fair.items()):
        if int(d["players_start"]) < ref_p * 0.8:
            print(f"FAIRNESS FAIL [{name}]: players {d['players_start']} << peer {ref_p}")
            del fair[name]

    if len(fair) < 2:
        print("VERDICT: INVALID — player counts not comparable")
        return 3

    ranked = sorted(fair.items(), key=lambda kv: float(kv[1]["mspt_mean"]))
    print("\nRANK (fair highpop, lower MSPT better):")
    best = float(ranked[0][1]["mspt_mean"])
    for i, (name, d) in enumerate(ranked, 1):
        m = float(d["mspt_mean"])
        mark = " ← best" if i == 1 else f"  (+{m - best:.4f} ms)"
        print(f"  {i}. {name:8s}  mspt={m:.4f}{mark}")

    winner = ranked[0][0]
    if winner == "yapcore":
        print("VERDICT: YaPcore lowest MSPT under highpop bot load")
        return 0
    if "yapcore" in fair:
        print(f"VERDICT: best={winner}; YaPcore not first under highpop")
        return 1
    print(f"VERDICT: best={winner} (YaPcore missing)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
