#!/usr/bin/env python3
"""Compare two MSPT bench JSON files. Exit 0 if yap mspt_mean < stock (win)."""
from __future__ import annotations

import json
import sys
from pathlib import Path


def load(p: Path) -> dict:
    return json.loads(p.read_text())


def main() -> int:
    if len(sys.argv) != 3:
        print(f"Usage: {sys.argv[0]} stock.json yapcore.json", file=sys.stderr)
        return 2
    stock = load(Path(sys.argv[1]))
    yap = load(Path(sys.argv[2]))
    sm = float(stock["mspt_mean"])
    ym = float(yap["mspt_mean"])
    st = float(stock.get("tps_1m_mean", 0))
    yt = float(yap.get("tps_1m_mean", 0))
    delta = sm - ym
    pct = (delta / sm * 100.0) if sm > 0 else 0.0
    print(f"scenario={stock.get('scenario')} / {yap.get('scenario')}")
    print(f"stock  mspt_mean={sm:.4f}  tps={st:.3f}  label={stock.get('label')}")
    print(f"yap    mspt_mean={ym:.4f}  tps={yt:.3f}  label={yap.get('label')}")
    print(f"delta  mspt={delta:+.4f} ({pct:+.1f}% vs stock; positive = yap faster)")
    if ym < sm:
        print("VERDICT: WIN — YaPcore lower MSPT")
        return 0
    if ym <= sm * 1.05:
        print("VERDICT: TIE — within 5%")
        return 0
    print("VERDICT: LOSS — YaPcore higher MSPT (investigate overhead)")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
