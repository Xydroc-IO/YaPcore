#!/usr/bin/env python3
"""Compare Folia-ecosystem MSPT JSON results (M5 product gate).

Modes:
  compare-folia.py stock-folia.json yapcore.json
      Pairwise stock Folia vs YaP Folia+chassis (exit 0/1/3/4).
  compare-folia.py --rank a.json b.json [c.json ...]
      Multi-way MSPT ranking (Canvas / Folia / YaP). Exit 0 always unless usage error.

Anti-gaming:
  - Load proofs must match (TNT, fuse, hoppers).
  - Game JVM heap flags must match when recorded.
  - Low MSPT + small delta → NOT CITEABLE (within noise), not a win.
  - MSPT is game-tick only; chassis_present=true means parent JVM is excluded.

Exit codes (pairwise):
  0 = tie / acceptable overhead (≤+2% vs stock) OR not citeable (noise)
  1 = YaP slower than stock Folia
  3 = fairness fail — do not claim anything
  4 = not citeable — delta too small at low MSPT (re-run heavier load)
"""
from __future__ import annotations

import json
import os
import sys
from pathlib import Path

# Below this MSPT, ±5% deltas are treated as noise unless sample is heavy.
NOISE_MSPT_CEILING = 2.0
NOISE_PCT = 5.0


def load(p: Path) -> dict:
    return json.loads(p.read_text())


def fairness_check(stock: dict, yap: dict) -> list[str]:
    reasons: list[str] = []
    scenario = str(stock.get("scenario", ""))

    def near(a: float, b: float, tol: float) -> bool:
        return abs(a - b) <= tol

    if stock.get("scenario") != yap.get("scenario"):
        reasons.append(f"scenario mismatch {stock.get('scenario')} vs {yap.get('scenario')}")
    if int(stock.get("sample_seconds", 0)) != int(yap.get("sample_seconds", 0)):
        reasons.append("sample_seconds mismatch")
    if int(stock.get("warmup_seconds", 0)) != int(yap.get("warmup_seconds", 0)):
        reasons.append("warmup_seconds mismatch")

    for side, d in ("stock", stock), ("yap", yap):
        scope = d.get("measurement_scope")
        if scope and scope != "game_tick_mspt":
            reasons.append(f"{side} measurement_scope={scope!r} (expected game_tick_mspt)")

    for key in ("game_jvm_xms", "game_jvm_xmx"):
        sv, yv = stock.get(key), yap.get(key)
        if sv and yv and str(sv) != str(yv):
            reasons.append(f"JVM {key} mismatch: stock={sv} yap={yv}")

    if "tnt_start" not in stock or "tnt_start" not in yap:
        if scenario in ("entity", "heavypop"):
            reasons.append("MISSING_LOAD_PROOFS — re-run with updated yap-mspt-bench")
        return reasons

    if scenario in ("entity", "heavypop", "spawncollapse"):
        for side, d in ("stock", stock), ("yap", yap):
            exp = int(d.get("expected_tnt", 0))
            start = int(d.get("tnt_start", 0))
            end = int(d.get("tnt_end", 0))
            if exp > 0 and start < exp * 0.90:
                reasons.append(f"{side} spawned too few TNT: start={start} expected≈{exp}")
            if start > 0 and end < start * 0.98:
                reasons.append(f"{side} lost TNT during sample: {start} → {end}")
            if start > 0 and not d.get("fuse_ticking_ok", False):
                reasons.append(
                    f"{side} fuse not draining: drop={d.get('fuse_drop')} "
                    f"expected≈{d.get('fuse_drop_expected')}"
                )
        if not near(float(stock["tnt_start"]), float(yap["tnt_start"]), max(5.0, stock["tnt_start"] * 0.05)):
            reasons.append(
                f"TNT mismatch start: stock={stock['tnt_start']} yap={yap['tnt_start']}"
            )
        if not near(float(stock["tnt_end"]), float(yap["tnt_end"]), max(5.0, stock["tnt_end"] * 0.05)):
            reasons.append(
                f"TNT mismatch end: stock={stock['tnt_end']} yap={yap['tnt_end']}"
            )

    if scenario in ("heavypop", "spawncollapse"):
        for side, d in ("stock", stock), ("yap", yap):
            hs, he = int(d.get("hoppers_start", 0)), int(d.get("hoppers_end", 0))
            if hs < 100:
                reasons.append(f"{side} hopper count suspiciously low: {hs}")
            if he < hs * 0.98:
                reasons.append(f"{side} lost hoppers: {hs} → {he}")
        if not near(
            float(stock["hoppers_start"]), float(yap["hoppers_start"]),
            max(4.0, stock["hoppers_start"] * 0.05),
        ):
            reasons.append(
                f"hopper mismatch: stock={stock['hoppers_start']} yap={yap['hoppers_start']}"
            )

    # Ship-profile cite: require YaP JSON to disclose smart knobs (micro/subregion/entity).
    # Env YAP_MSPT_REQUIRE_SHIP_KNOBS=1 (cite-fullcite) fails if missing or below ship floor.
    require_ship = os.environ.get("YAP_MSPT_REQUIRE_SHIP_KNOBS", "") in ("1", "true", "TRUE")
    if require_ship and scenario in ("fullcite", "heavypop", "highpop"):
        if "knob_entity_tick_budget" not in yap:
            reasons.append(
                "yap missing knob_* fields — rebuild yap-mspt-bench and re-run cite"
            )
        else:
            ent = int(yap.get("knob_entity_tick_budget", 0))
            micro = int(yap.get("knob_microtick_budget_ms", 0))
            part = bool(yap.get("knob_subregion_partition", False))
            async_save = bool(yap.get("knob_async_chunk_save", False))
            if ent < 400:
                reasons.append(
                    f"ship cite requires entity-tick-budget≥400 (got {ent})"
                )
            if micro < 8:
                reasons.append(
                    f"ship cite requires microtick-budget-ms≥8 (got {micro})"
                )
            if not part:
                reasons.append("ship cite requires subregion-partition=true")
            if not async_save:
                reasons.append("ship cite requires async-chunk-save=true")

    return reasons


def not_citeable(stock_mspt: float, yap_mspt: float) -> bool:
    """True when MSPT is too low or delta too small to cite (anti-gaming)."""
    if stock_mspt <= 0:
        return True
    # Sub-2 ms absolute MSPT is smoke — jitter dominates even large %-deltas.
    if max(stock_mspt, yap_mspt) < NOISE_MSPT_CEILING:
        return True
    pct = abs(yap_mspt - stock_mspt) / stock_mspt * 100.0
    return pct < NOISE_PCT


def pairwise(stock_path: Path, yap_path: Path) -> int:
    stock = load(stock_path)
    yap = load(yap_path)

    print(f"scenario={stock.get('scenario')} / {yap.get('scenario')}")
    print(
        f"stock-folia  mspt_mean={float(stock['mspt_mean']):.4f}  "
        f"tps={float(stock.get('tps_1m_mean', 0)):.3f}  label={stock.get('label')}"
    )
    print(
        f"yap-folia    mspt_mean={float(yap['mspt_mean']):.4f}  "
        f"tps={float(yap.get('tps_1m_mean', 0)):.3f}  label={yap.get('label')}"
    )
    if yap.get("chassis_present"):
        print("NOTE: yap row is game-tick MSPT only — YaP chassis JVM overhead is NOT in mspt_mean.")
    if "knob_entity_tick_budget" in yap:
        print(
            "yap knobs: entity=%s microtick_ms=%s hopper=%s async=%s partition=%s budget_mspt=%s"
            % (
                yap.get("knob_entity_tick_budget"),
                yap.get("knob_microtick_budget_ms"),
                yap.get("knob_hopper_tick_budget"),
                yap.get("knob_async_chunk_save"),
                yap.get("knob_subregion_partition"),
                yap.get("knob_budget_mspt_threshold"),
            )
        )

    fails = fairness_check(stock, yap)
    if fails:
        print("FAIRNESS FAIL:")
        for r in fails:
            print(f"  - {r}")
        print("VERDICT: INVALID — do not claim win/loss")
        return 3

    s = float(stock["mspt_mean"])
    y = float(yap["mspt_mean"])
    delta = y - s
    pct = (delta / s * 100.0) if s > 0 else 0.0
    print(f"delta yap-stock = {delta:+.4f} ms ({pct:+.2f}%)")

    if not_citeable(s, y):
        print(
            f"VERDICT: NOT CITEABLE — MSPT<{NOISE_MSPT_CEILING:.0f} or delta within {NOISE_PCT:.0f}% "
            "(increase load until mspt_mean≥2 before fork/marketing decisions)."
        )
        return 4

    print("NOTE: Cite only with mspt_mean≥~2 and fairness_check OK (see BENCH_VS_FOLIA.md).")

    if y < s * 0.95:
        print(
            f"VERDICT: YaP-Folia FASTER than stock Folia by {-pct:.1f}% "
            f"(citeable; document knobs e.g. entity-tick-budget)"
        )
        return 0
    if y <= s * 1.02:
        print("VERDICT: YaP game tick ≈ stock Folia (+2% tie band)")
        return 0
    print("VERDICT: YaP game tick slower than stock Folia")
    return 1


def rank(paths: list[Path]) -> int:
    rows: list[tuple[str, float, float, Path]] = []
    for p in paths:
        d = load(p)
        label = str(d.get("label") or p.stem)
        mspt = float(d["mspt_mean"])
        tps = float(d.get("tps_1m_mean", 0))
        rows.append((label, mspt, tps, p))
    rows.sort(key=lambda r: r[1])
    print(f"{'rank':<5} {'label':<28} {'mspt':>10} {'tps':>8}  file")
    for i, (label, mspt, tps, p) in enumerate(rows, 1):
        print(f"{i:<5} {label:<28} {mspt:10.4f} {tps:8.3f}  {p.name}")
    best = rows[0][1]
    print()
    print("Deltas vs best (lower MSPT wins):")
    for label, mspt, _tps, _p in rows:
        delta = mspt - best
        pct = (delta / best * 100.0) if best > 0 else 0.0
        print(f"  {label:<28} {delta:+.4f} ms ({pct:+.2f}%)")
    if best < NOISE_MSPT_CEILING:
        print(
            f"NOTE: best MSPT<{NOISE_MSPT_CEILING:.0f} — ranking order is not citeable; "
            "increase load before fork/marketing decisions."
        )
    print("NOTE: Canvas is a Folia fork peer; YaP MSPT excludes chassis parent JVM.")
    print("Kaiiju skipped on 26.2 (public releases are 1.20.x).")
    return 0


def main() -> int:
    if len(sys.argv) >= 3 and sys.argv[1] == "--rank":
        paths = [Path(a) for a in sys.argv[2:]]
        if len(paths) < 2:
            print(f"Usage: {sys.argv[0]} --rank a.json b.json [...]", file=sys.stderr)
            return 2
        return rank(paths)
    if len(sys.argv) != 3:
        print(
            f"Usage: {sys.argv[0]} stock-folia.json yapcore.json\n"
            f"       {sys.argv[0]} --rank a.json b.json [...]",
            file=sys.stderr,
        )
        return 2
    return pairwise(Path(sys.argv[1]), Path(sys.argv[2]))


if __name__ == "__main__":
    raise SystemExit(main())
