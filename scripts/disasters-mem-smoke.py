#!/usr/bin/env python3
"""YaPDisasters memory / edge-case smoke against a live dashboard.

Measures parent + Folia heap before/after rapid start-stop + warning churn.
Fails if heap growth looks pathological or region ownership errors appear.
"""
from __future__ import annotations

import json
import os
import re
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LOG = ROOT / "folia-kernel" / "logs" / "latest.log"
PROPS = ROOT / "config" / "server.properties"
TYPES = [
    "thunder", "hurricane", "tornado", "earthquake", "volcano",
    "blizzard", "drought", "meteor", "tsunami",
]


def token() -> str:
    m = re.search(r"^web-dashboard-token=(.*)$", PROPS.read_text(), re.M)
    if not m:
        raise SystemExit("missing web-dashboard-token")
    return m.group(1).strip()


def api(method: str, path: str, body=None, tok: str | None = None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(
        f"http://127.0.0.1:8080{path}",
        data=data,
        headers={
            "Authorization": f"Bearer {tok or token()}",
            "Content-Type": "application/json",
        },
        method=method,
    )
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.loads(r.read().decode())


def cmd(c: str, tok: str):
    return api("POST", "/api/command", {"command": c}, tok)


def heap_mb(pid: int) -> float | None:
    try:
        out = subprocess.check_output(
            ["jcmd", str(pid), "GC.heap_info"], text=True, stderr=subprocess.DEVNULL
        )
    except Exception:
        try:
            out = subprocess.check_output(
                ["jcmd", str(pid), "VM.native_memory", "summary"],
                text=True,
                stderr=subprocess.DEVNULL,
            )
        except Exception:
            return None
    # Prefer used from heap_info garbage-first / ZGC lines
    m = re.search(r"used\s+(\d+)([KMG])", out, re.I)
    if not m:
        m = re.search(r"committed=(\d+)([KMG])", out, re.I)
    if not m:
        return None
    n = float(m.group(1))
    unit = m.group(2).upper()
    if unit == "G":
        return n * 1024
    if unit == "K":
        return n / 1024
    return n


def pids() -> tuple[int | None, int | None]:
    parent = folia = None
    try:
        out = subprocess.check_output(["pgrep", "-af", "yapcore.jar|folia-26"], text=True)
    except subprocess.CalledProcessError:
        return None, None
    for line in out.splitlines():
        parts = line.split(None, 1)
        if len(parts) < 2:
            continue
        pid = int(parts[0])
        if "yapcore.jar" in parts[1] and "folia" not in parts[1]:
            parent = pid
        if "folia-26" in parts[1] or "folia-kernel" in parts[1]:
            folia = pid
    return parent, folia


def wait_ready(tok: str, timeout=120):
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            api("GET", "/api/disasters", tok=tok)
            cmd("yapdisaster status", tok)
            return
        except Exception:
            time.sleep(2)
    raise SystemExit("dashboard/disasters not ready")


def main() -> int:
    tok = token()
    wait_ready(tok)
    parent, folia = pids()
    print(f"pids parent={parent} folia={folia}")
    h0 = heap_mb(folia) if folia else None
    print(f"folia heap before: {h0} MB")

    # Edge cases
    cases = [
        "yapdisaster stop",
        "yapdisaster stop world",
        "yapdisaster clear 5",
        "yapdisaster rain 5",
        "yapdisaster nosuch 5",
        "yapdisaster tornado 5 world",
        "yapdisaster stop",
        "yapdisaster random now thunder",
        "yapdisaster stop",  # cancel during warning
        "yapdisaster random now meteor",
        "yapdisaster tornado 30",  # replace pending/start
        "yapdisaster hurricane 5",  # replace active
        "yapdisaster site erupt ember_peak 5",
        "yapdisaster site erupt missing_site 5",
        "yapdisaster status",
    ]
    for c in cases:
        try:
            print(">", c, "->", cmd(c, tok).get("result", "")[:80])
        except Exception as e:
            print(">", c, "ERR", e)
        time.sleep(0.35)

    # Rapid churn stress
    for i in range(24):
        t = TYPES[i % len(TYPES)]
        cmd(f"yapdisaster {t} 4", tok)
        if i % 3 == 2:
            cmd("yapdisaster stop", tok)
        time.sleep(0.25)
    cmd("yapdisaster stop", tok)
    time.sleep(2)

    # Warning churn: start warn, cancel, repeat
    for _ in range(8):
        cmd("yapdisaster random now blizzard", tok)
        time.sleep(0.4)
        cmd("yapdisaster stop", tok)
        time.sleep(0.2)

    time.sleep(3)
    status = cmd("yapdisaster status", tok)
    print("status cmd:", status)

    # Force GC on Folia if possible then re-measure
    if folia:
        try:
            subprocess.check_call(
                ["jcmd", str(folia), "GC.run"],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
            time.sleep(1)
        except Exception:
            pass
    h1 = heap_mb(folia) if folia else None
    print(f"folia heap after:  {h1} MB")

    log = LOG.read_text(errors="ignore") if LOG.is_file() else ""
    recent = log[-25000:]
    bad = [
        ln
        for ln in recent.splitlines()
        if any(
            s in ln
            for s in (
                "IllegalStateException",
                "owning region",
                "Cannot modify",
                "OutOfMemory",
                "leak",
            )
        )
        and "YaPDisasters" in recent  # only care if disasters context generally
    ]
    # Filter to lines near disasters activity in last chunk
    disaster_errs = [
        ln
        for ln in recent.splitlines()
        if any(
            s in ln
            for s in (
                "IllegalStateException",
                "TickThread",
                "owning region",
                "OutOfMemoryError",
            )
        )
    ]
    print(f"region/oom-ish lines in recent log: {len(disaster_errs)}")
    for ln in disaster_errs[-12:]:
        print(" ", ln)

    # Dashboard status for pending/undos if present in liveStatus log
    live = api("GET", "/api/disasters", tok=tok)
    print("dashboard liveStatus:", (live.get("liveStatus") or "")[:160])

    fail = False
    if disaster_errs:
        # Allow none - any TickThread during our window is a fail
        fail = True
        print("FAIL: region/thread errors observed")
    if h0 is not None and h1 is not None and (h1 - h0) > 400:
        fail = True
        print(f"FAIL: heap grew {h1 - h0:.0f} MB (>400 MB threshold for this smoke)")
    if not fail:
        print("PASS: no ownership errors; heap delta within smoke threshold")
    return 1 if fail else 0


if __name__ == "__main__":
    sys.exit(main())
