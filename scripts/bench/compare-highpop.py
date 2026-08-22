#!/usr/bin/env python3
"""Rank highpop results — strict hold + matched online counts (anti-gaming).

cite-stable / keepalive-only swarms are HOLD checks, not MSPT gameplay cites.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

IDS = ("paper", "purpur", "leaf", "yapcore")
HOLD_FRAC = 0.90
PEER_FRAC = 0.90
MAX_BLEED = 0.10


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
    cite_stable = False
    for name, d in runs.items():
        ps = int(d.get("players_start", 0))
        pe = int(d.get("players_end", 0))
        target = int(d.get("players_target") or 0)
        ok = bool(d.get("players_ok", False))
        bot_load = str(d.get("bot_load") or "active")
        if bot_load in ("cite-stable", "keepalive", "hold"):
            cite_stable = True
        need = max(1, int(target * HOLD_FRAC + 0.999)) if target else 1
        bleed = (ps - pe) / ps if ps > 0 else 1.0
        chunks_s = d.get("chunks_loaded_start")
        chunks_e = d.get("chunks_loaded_end")
        print(
            f"  [{name}] mspt={float(d['mspt_mean']):.4f} tps={float(d.get('tps_1m_mean', 0)):.3f} "
            f"players={ps}→{pe} target={target} need≥{need} bleed={bleed:.1%} "
            f"chunks={chunks_s}→{chunks_e} bot_load={bot_load} players_ok={ok}"
        )
        if not ok or ps < need or pe < need:
            print(
                f"FAIRNESS FAIL [{name}]: insufficient held population "
                f"(need start&end ≥{need}, got {ps}→{pe})"
            )
            continue
        if bleed > MAX_BLEED:
            print(
                f"FAIRNESS FAIL [{name}]: sample bleed {bleed:.1%} > {MAX_BLEED:.0%} "
                f"({ps}→{pe})"
            )
            continue
        fair[name] = d

    if len(fair) < 2:
        print("VERDICT: INVALID — need ≥2 fair highpop runs (held pop)")
        return 3

    ref_start = max(int(d["players_start"]) for d in fair.values())
    ref_end = max(int(d["players_end"]) for d in fair.values())
    for name, d in list(fair.items()):
        ps = int(d["players_start"])
        pe = int(d["players_end"])
        if ps < ref_start * PEER_FRAC:
            print(f"FAIRNESS FAIL [{name}]: start players {ps} << peer {ref_start}")
            del fair[name]
            continue
        if pe < ref_end * PEER_FRAC:
            print(f"FAIRNESS FAIL [{name}]: end players {pe} << peer {ref_end}")
            del fair[name]

    if len(fair) < 2:
        print("VERDICT: INVALID — player counts not comparable under hold rules")
        return 3

    # Frozen chunks across the sample ⇒ bots likely idle (cite-stable / no world work).
    frozen = 0
    for name, d in fair.items():
        cs, ce = d.get("chunks_loaded_start"), d.get("chunks_loaded_end")
        es, ee = d.get("entities_start"), d.get("entities_end")
        if cs is not None and ce is not None and cs == ce:
            if es is not None and ee is not None and abs(int(es) - int(ee)) <= max(2, int(es) * 0.02):
                frozen += 1
    if frozen >= 2 and not cite_stable:
        # Infer cite-stable from frozen world even if older JSON lacks bot_load
        cite_stable = True
        print("NOTE: ≥2 fair runs show frozen chunks/entities — treating as cite-stable/hold load")

    ranked = sorted(fair.items(), key=lambda kv: float(kv[1]["mspt_mean"]))
    print("\nRANK (lower MSPT):")
    best = float(ranked[0][1]["mspt_mean"])
    for i, (name, d) in enumerate(ranked, 1):
        m = float(d["mspt_mean"])
        mark = " ← best" if i == 1 else f"  (+{m - best:.4f} ms)"
        print(f"  {i}. {name:8s}  mspt={m:.4f}{mark}")

    if cite_stable:
        print(
            "VERDICT: HOLD-ONLY — cite-stable/keepalive swarm (physics OFF). "
            "Not an MSPT gameplay cite. Keep 100-bot active three-way for MSPT claims."
        )
        return 4  # distinct from win/loss so CI can gate

    winner = ranked[0][0]
    if winner == "yapcore":
        print("VERDICT: YaPcore lowest MSPT under fair held highpop (active bots)")
        return 0
    if "yapcore" in fair:
        print(f"VERDICT: best={winner}; YaPcore not first under fair held highpop")
        return 1
    print(f"VERDICT: best={winner} (YaPcore missing)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
