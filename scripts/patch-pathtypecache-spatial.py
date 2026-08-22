#!/usr/bin/env python3
"""Synchronize PathTypeCache for Yap spatial parallel sendBlockUpdated / pathfinding."""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CACHE = (
    ROOT
    / "vendor/paper/paper-server/src/minecraft/java/net/minecraft/world/level/pathfinder/PathTypeCache.java"
)


def main() -> int:
    if not CACHE.is_file():
        print(f"SKIP: {CACHE} missing")
        return 0
    text = CACHE.read_text()
    if "YaPcore Phase 3 — spatial" in text:
        print("PathTypeCache spatial sync already present")
        return 0

    old = """public class PathTypeCache {
    private static final int SIZE = 4096;
    private static final int MASK = 4095;
    private final long[] positions = new long[4096];
    private final PathType[] pathTypes = new PathType[4096];

    public PathType getOrCompute(final BlockGetter level, final BlockPos pos) {
        long key = pos.asLong();
        int index = index(key);
        PathType cachedPathType = this.get(index, key);
        return cachedPathType != null ? cachedPathType : this.compute(level, pos, index, key);
    }

    private @Nullable PathType get(final int index, final long key) {
        return this.positions[index] == key ? this.pathTypes[index] : null;
    }

    private PathType compute(final BlockGetter level, final BlockPos pos, final int index, final long key) {
        PathType pathType = WalkNodeEvaluator.getPathTypeFromState(level, pos);
        this.positions[index] = key;
        this.pathTypes[index] = pathType;
        return pathType;
    }

    public void invalidate(final BlockPos pos) {
        long key = pos.asLong();
        int index = index(key);
        if (this.positions[index] == key) {
            this.pathTypes[index] = null;
        }
    }"""

    new = """public class PathTypeCache {
    private static final int SIZE = 4096;
    private static final int MASK = 4095;
    private final long[] positions = new long[4096];
    private final PathType[] pathTypes = new PathType[4096];
    // YaPcore Phase 3 — spatial cores call invalidate/getOrCompute concurrently via sendBlockUpdated
    private final Object yapLock = new Object();

    public PathType getOrCompute(final BlockGetter level, final BlockPos pos) {
        long key = pos.asLong();
        int index = index(key);
        synchronized (this.yapLock) {
            PathType cachedPathType = this.get(index, key);
            return cachedPathType != null ? cachedPathType : this.compute(level, pos, index, key);
        }
    }

    private @Nullable PathType get(final int index, final long key) {
        return this.positions[index] == key ? this.pathTypes[index] : null;
    }

    private PathType compute(final BlockGetter level, final BlockPos pos, final int index, final long key) {
        PathType pathType = WalkNodeEvaluator.getPathTypeFromState(level, pos);
        this.positions[index] = key;
        this.pathTypes[index] = pathType;
        return pathType;
    }

    public void invalidate(final BlockPos pos) {
        long key = pos.asLong();
        int index = index(key);
        synchronized (this.yapLock) {
            if (this.positions[index] == key) {
                this.pathTypes[index] = null;
            }
        }
    }"""

    if old not in text:
        raise SystemExit("PathTypeCache pattern not found")
    CACHE.write_text(text.replace(old, new, 1))
    print("Patched PathTypeCache for spatial concurrent invalidate/getOrCompute")
    return 0


if __name__ == "__main__":
    sys.exit(main())
