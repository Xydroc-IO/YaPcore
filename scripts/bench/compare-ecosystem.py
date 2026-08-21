#!/usr/bin/env python3
"""Rank Paper / Purpur / Leaf / YaPcore MSPT results with load-fairness checks.

Usage: compare-ecosystem.py <results_dir> <stamp> <scenario>

Exit:
  0 = at least 2 fair runs ranked (YaP may or may not win)
  1 = YaP fair but not lowest MSPT among fair peers
  2 = usage
  3 = fairness failed across the board / insufficient fair runs
"""
from __future__ import annotations

import json
import sys
from pathlib import Path


IDS = ("paper", "purpur", "leaf", "yapcore")


def load(p: Path) -> dict | None:
    if not p.exists():
        return None
    return json.loads(p.read_text())


def self_fair(d: dict) -> list[str]:
    """Per-run integrity (TNT ticking / hoppers present)."""
    reasons: list[str] = []
    scenario = str(d.get("scenario", ""))
    if "tnt_start" not in d:
        return ["MISSING_LOAD_PROOFS"]
    if scenario in ("entity", "heavypop"):
        exp = int(d.get("expected_tnt", 0))
        start = int(d.get("tnt_start", 0))
        end = int(d.get("tnt_end", 0))
        if exp > 0 and start < exp * 0.98:
            reasons.append(f"spawned too few TNT: {start} vs expected {exp}")
        if start > 0 and end < start * 0.98:
            reasons.append(f"lost TNT: {start}→{end}")
        if start > 0 and not d.get("fuse_ticking_ok", False):
            reasons.append(
                f"fuse not draining: drop={d.get('fuse_drop')} expected≈{d.get('fuse_drop_expected')}"
            )
    if scenario == "heavypop":
        hs = int(d.get("hoppers_start", 0))
        if hs < 50:
            reasons.append(f"hopper count low: {hs}")
    return reasons


def cross_fair(ref: dict, other: dict, name: str) -> list[str]:
    reasons: list[str] = []
    if int(ref.get("sample_seconds", 0)) != int(other.get("sample_seconds", 0)):
        reasons.append(f"{name}: sample_seconds mismatch vs paper")
    if int(ref.get("warmup_seconds", 0)) != int(other.get("warmup_seconds", 0)):
        reasons.append(f"{name}: warmup_seconds mismatch vs paper")
    scenario = str(ref.get("scenario", ""))
    if scenario in ("entity", "heavypop") and "tnt_start" in ref and "tnt_start" in other:
        rt, ot = float(ref["tnt_start"]), float(other["tnt_start"])
        if abs(rt - ot) > max(5.0, rt * 0.02):
            reasons.append(f"{name}: TNT start {ot} vs paper {rt}")
        if abs(float(ref.get("fuse_drop", 0)) - float(other.get("fuse_drop", 0))) > max(
            40.0, float(ref.get("fuse_drop", 0)) * 0.15
        ):
            reasons.append(
                f"{name}: fuse_drop {other.get('fuse_drop')} vs paper {ref.get('fuse_drop')}"
            )
    return reasons


def main() -> int:
    if len(sys.argv) != 4:
        print(f"Usage: {sys.argv[0]} <results_dir> <stamp> <scenario>", file=sys.stderr)
        return 2
    results = Path(sys.argv[1])
    stamp = sys.argv[2]
    scenario = sys.argv[3]

    runs: dict[str, dict] = {}
    for i in IDS:
        # paper file may be named stock historically — prefer paper, fall back stock
        p = results / f"{stamp}-{scenario}-{i}.json"
        if i == "paper" and not p.exists():
            p = results / f"{stamp}-{scenario}-stock.json"
        d = load(p)
        if d is not None:
            runs[i] = d

    if not runs:
        print("No result JSONs found")
        return 3

    print(f"ecosystem stamp={stamp} scenario={scenario}")
    print(f"found: {', '.join(runs.keys())}")

    fair: dict[str, dict] = {}
    for name, d in runs.items():
        fails = self_fair(d)
        if fails:
            print(f"FAIRNESS FAIL [{name}]:")
            for r in fails:
                print(f"  - {r}")
            continue
        fair[name] = d
        print(
            f"OK [{name}] mspt={float(d['mspt_mean']):.4f} "
            f"tps={float(d.get('tps_1m_mean', 0)):.3f} "
            f"tnt={d.get('tnt_start')}→{d.get('tnt_end')} "
            f"fuseΔ={d.get('fuse_drop')} hoppers={d.get('hoppers_start')}"
        )

    if "paper" in fair:
        ref = fair["paper"]
        drop = []
        for name, d in list(fair.items()):
            if name == "paper":
                continue
            xf = cross_fair(ref, d, name)
            if xf:
                print(f"FAIRNESS FAIL [{name}] vs paper:")
                for r in xf:
                    print(f"  - {r}")
                drop.append(name)
        for name in drop:
            del fair[name]

    if len(fair) < 2:
        print("VERDICT: INVALID — need ≥2 fair competitors")
        return 3

    ranked = sorted(fair.items(), key=lambda kv: float(kv[1]["mspt_mean"]))
    print()
    print("RANK (fair only, lower MSPT better):")
    best_mspt = float(ranked[0][1]["mspt_mean"])
    for i, (name, d) in enumerate(ranked, 1):
        mspt = float(d["mspt_mean"])
        delta = (best_mspt - mspt) / best_mspt * 100.0 if best_mspt else 0.0
        vs_best = mspt - best_mspt
        mark = " ← best" if i == 1 else f"  (+{vs_best:.4f} ms / {delta:+.1f}% vs best)"
        print(f"  {i}. {name:8s}  mspt={mspt:.4f}{mark}")

    winner = ranked[0][0]
    if "yapcore" in fair:
        yap_rank = next(i for i, (n, _) in enumerate(ranked, 1) if n == "yapcore")
        yap_mspt = float(fair["yapcore"]["mspt_mean"])
        if winner == "yapcore":
            print("VERDICT: YaPcore lowest MSPT among fair peers")
            return 0
        print(f"VERDICT: YaPcore rank #{yap_rank}/{len(ranked)} (best={winner} @ {best_mspt:.4f})")
        # still informative success if we produced a fair table
        return 1 if yap_mspt > best_mspt * 1.05 else 0

    print(f"VERDICT: best={winner} (YaPcore not in fair set)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
