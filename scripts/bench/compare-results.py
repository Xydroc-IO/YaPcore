#!/usr/bin/env python3
"""Compare two MSPT bench JSON files with load-fairness checks.

Exit codes:
  0 = YaP win or tie (and load proofs OK)
  1 = YaP MSPT loss
  2 = usage error
  3 = fairness fail (unequal load / TNT not ticking) — do not claim a win
"""
from __future__ import annotations

import json
import sys
from pathlib import Path


def load(p: Path) -> dict:
    return json.loads(p.read_text())


def fairness_check(stock: dict, yap: dict) -> list[str]:
    """Return list of fairness failure reasons (empty = OK)."""
    reasons: list[str] = []
    scenario = str(stock.get("scenario", ""))

    def near(a: float, b: float, tol: float) -> bool:
        return abs(a - b) <= tol

    # Both sides must have run the same scenario window
    if stock.get("scenario") != yap.get("scenario"):
        reasons.append(f"scenario mismatch {stock.get('scenario')} vs {yap.get('scenario')}")
    if int(stock.get("sample_seconds", 0)) != int(yap.get("sample_seconds", 0)):
        reasons.append("sample_seconds mismatch")
    if int(stock.get("warmup_seconds", 0)) != int(yap.get("warmup_seconds", 0)):
        reasons.append("warmup_seconds mismatch")

    # Legacy JSON without load proofs — warn but don't hard-fail (pre-fairness runs)
    if "tnt_start" not in stock or "tnt_start" not in yap:
        reasons.append("MISSING_LOAD_PROOFS — re-run bench with updated yap-mspt-bench plugin")
        return reasons

    if scenario in ("entity", "heavypop"):
        for side, d in ("stock", stock), ("yap", yap):
            exp = int(d.get("expected_tnt", 0))
            start = int(d.get("tnt_start", 0))
            end = int(d.get("tnt_end", 0))
            if exp > 0 and start < exp * 0.98:
                reasons.append(f"{side} spawned too few TNT: start={start} expected≈{exp}")
            if start > 0 and end < start * 0.98:
                reasons.append(f"{side} lost TNT during sample: {start} → {end}")
            if start > 0 and not d.get("fuse_ticking_ok", False):
                reasons.append(
                    f"{side} fuse not draining (entities may not be ticking): "
                    f"drop={d.get('fuse_drop')} expected≈{d.get('fuse_drop_expected')}"
                )

        # Cross-side: same living load
        if not near(float(stock["tnt_start"]), float(yap["tnt_start"]), max(5.0, stock["tnt_start"] * 0.02)):
            reasons.append(
                f"TNT count mismatch at sample start: stock={stock['tnt_start']} yap={yap['tnt_start']}"
            )
        if not near(float(stock["tnt_end"]), float(yap["tnt_end"]), max(5.0, stock["tnt_end"] * 0.02)):
            reasons.append(
                f"TNT count mismatch at sample end: stock={stock['tnt_end']} yap={yap['tnt_end']}"
            )

    if scenario == "heavypop":
        for side, d in ("stock", stock), ("yap", yap):
            hs, he = int(d.get("hoppers_start", 0)), int(d.get("hoppers_end", 0))
            if hs < 100:
                reasons.append(f"{side} hopper count suspiciously low: {hs}")
            if he < hs * 0.98:
                reasons.append(f"{side} lost hoppers during sample: {hs} → {he}")
        if not near(float(stock["hoppers_start"]), float(yap["hoppers_start"]), max(4.0, stock["hoppers_start"] * 0.02)):
            reasons.append(
                f"hopper mismatch: stock={stock['hoppers_start']} yap={yap['hoppers_start']}"
            )

    return reasons


def main() -> int:
    if len(sys.argv) != 3:
        print(f"Usage: {sys.argv[0]} stock.json yapcore.json", file=sys.stderr)
        return 2
    stock = load(Path(sys.argv[1]))
    yap = load(Path(sys.argv[2]))

    print(f"scenario={stock.get('scenario')} / {yap.get('scenario')}")
    print(f"stock  mspt_mean={float(stock['mspt_mean']):.4f}  tps={float(stock.get('tps_1m_mean', 0)):.3f}  label={stock.get('label')}")
    print(f"yap    mspt_mean={float(yap['mspt_mean']):.4f}  tps={float(yap.get('tps_1m_mean', 0)):.3f}  label={yap.get('label')}")

    if "tnt_start" in stock and "tnt_start" in yap:
        print(
            f"load   stock tnt {stock.get('tnt_start')}→{stock.get('tnt_end')} "
            f"fuseΔ={stock.get('fuse_drop')} hoppers={stock.get('hoppers_start')}"
        )
        print(
            f"load   yap   tnt {yap.get('tnt_start')}→{yap.get('tnt_end')} "
            f"fuseΔ={yap.get('fuse_drop')} hoppers={yap.get('hoppers_start')}"
        )

    fails = fairness_check(stock, yap)
    if fails:
        print("FAIRNESS: FAIL")
        for r in fails:
            print(f"  - {r}")
        print("VERDICT: INVALID — do not claim beat-Paper from this pair")
        return 3

    print("FAIRNESS: OK (equal surviving load + fuse drain)")
    sm = float(stock["mspt_mean"])
    ym = float(yap["mspt_mean"])
    delta = sm - ym
    pct = (delta / sm * 100.0) if sm > 0 else 0.0
    print(f"delta  mspt={delta:+.4f} ({pct:+.1f}% vs stock; positive = yap faster)")
    print("note   MSPT is main-thread tick time (player-facing). Work must still run — proven by fuseΔ.")
    if ym < sm:
        print("VERDICT: WIN — YaPcore lower MSPT (fair load)")
        return 0
    if ym <= sm * 1.05:
        print("VERDICT: TIE — within 5%")
        return 0
    print("VERDICT: LOSS — YaPcore higher MSPT")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
