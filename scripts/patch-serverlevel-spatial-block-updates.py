#!/usr/bin/env python3
"""Make ServerLevel.sendBlockUpdated safe under Yap spatial cores 3–6.

Root cause of Yap-only FALLING_BLOCK piles:
  isUpdatingNavigations is a single world boolean and navigatingMobs is an
  ObjectOpenHashSet. Parallel interior entity ticks race those structures during
  FallingBlockEntity.land → setBlock → sendBlockUpdated, so gravity blocks fail
  to settle cleanly and accumulate as entities.

Fix (idempotent):
  - ThreadLocal recursion guard (per spatial core, not world-global)
  - ConcurrentHashMap.newKeySet() for navigatingMobs
  - Snapshot before pathfinding iteration
"""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SL = ROOT / "vendor/paper/paper-server/src/minecraft/java/net/minecraft/server/level/ServerLevel.java"


def main() -> int:
    if not SL.is_file():
        print(f"SKIP: {SL} missing")
        return 0
    text = SL.read_text()
    if "yapUpdatingNavigations" in text and "ConcurrentHashMap.newKeySet()" in text:
        print("ServerLevel spatial sendBlockUpdated safety already present")
        return 0

    old_fields = """    private final PathTypeCache pathTypesByPosCache = new PathTypeCache();
    private final Set<Mob> navigatingMobs = new ObjectOpenHashSet<>();
    private volatile boolean isUpdatingNavigations;"""
    new_fields = """    private final PathTypeCache pathTypesByPosCache = new PathTypeCache();
    // YaPcore Phase 3 — spatial cores tick entities in parallel; ObjectOpenHashSet is not concurrent.
    private final Set<Mob> navigatingMobs = java.util.concurrent.ConcurrentHashMap.newKeySet();
    // YaPcore — recursion guard must be per-thread. A world-global flag races across cores 3–6
    // and breaks FallingBlock land / farmland trampling (setBlock → sendBlockUpdated).
    private final ThreadLocal<Boolean> yapUpdatingNavigations = ThreadLocal.withInitial(() -> Boolean.FALSE);
    /** Kept for Paper dead-code refs (`if (false && …)`); use {@link #yapUpdatingNavigations}. */
    @Deprecated
    private volatile boolean isUpdatingNavigations;"""
    if old_fields not in text:
        raise SystemExit("ServerLevel navigatingMobs field pattern not found")
    text = text.replace(old_fields, new_fields, 1)

    old_method = """    @Override
    public void sendBlockUpdated(final BlockPos pos, final BlockState old, final BlockState current, final @Block.UpdateFlags int updateFlags) {
        if (this.isUpdatingNavigations) {
            String message = "recursive call to sendBlockUpdated";
            Util.logAndPauseIfInIde("recursive call to sendBlockUpdated", new IllegalStateException("recursive call to sendBlockUpdated"));
        }

        this.getChunkSource().blockChanged(pos);
        this.pathTypesByPosCache.invalidate(pos);
        if (this.paperConfig().misc.updatePathfindingOnBlockUpdate) { // Paper - option to disable pathfinding updates
        VoxelShape oldShape = old.getCollisionShape(this, pos);
        VoxelShape newShape = current.getCollisionShape(this, pos);
        if (Shapes.joinIsNotEmpty(oldShape, newShape, BooleanOp.NOT_SAME)) {
            List<PathNavigation> navigationsToUpdate = new ObjectArrayList<>();

            try { // Paper - catch CME see below why
            for (Mob navigatingMob : this.navigatingMobs) {
                PathNavigation pathNavigation = navigatingMob.getNavigation();
                if (pathNavigation.shouldRecomputePath(pos)) {
                    navigationsToUpdate.add(pathNavigation);
                }
            }
            // Paper start - catch CME see below why
            } catch (final java.util.ConcurrentModificationException concurrentModificationException) {
                // This can happen because the pathfinder update below may trigger a chunk load, which in turn may cause more navigators to register
                // In this case we just run the update again across all the iterators as the chunk will then be loaded
                // As this is a relative edge case it is much faster than copying navigators (on either read or write)
                this.sendBlockUpdated(pos, old, current, updateFlags);
                return;
            }
            // Paper end - catch CME see below why

            try {
                this.isUpdatingNavigations = true;

                for (PathNavigation navigation : navigationsToUpdate) {
                    navigation.recomputePath();
                }
            } finally {
                this.isUpdatingNavigations = false;
            }
        }
        } // Paper - option to disable pathfinding updates
    }"""

    new_method = """    @Override
    public void sendBlockUpdated(final BlockPos pos, final BlockState old, final BlockState current, final @Block.UpdateFlags int updateFlags) {
        // YaPcore: per-thread guard — parallel spatial cores must not trip each other
        if (Boolean.TRUE.equals(this.yapUpdatingNavigations.get())) {
            String message = "recursive call to sendBlockUpdated";
            Util.logAndPauseIfInIde("recursive call to sendBlockUpdated", new IllegalStateException("recursive call to sendBlockUpdated"));
        }

        this.getChunkSource().blockChanged(pos);
        this.pathTypesByPosCache.invalidate(pos);
        if (this.paperConfig().misc.updatePathfindingOnBlockUpdate) { // Paper - option to disable pathfinding updates
        VoxelShape oldShape = old.getCollisionShape(this, pos);
        VoxelShape newShape = current.getCollisionShape(this, pos);
        if (Shapes.joinIsNotEmpty(oldShape, newShape, BooleanOp.NOT_SAME)) {
            List<PathNavigation> navigationsToUpdate = new ObjectArrayList<>();

            // YaPcore: snapshot — ConcurrentHashMap views are weakly consistent; avoid CME + torn reads
            // under spatial entity ticks (FallingBlock land, trampling, pistons).
            for (Mob navigatingMob : java.util.List.copyOf(this.navigatingMobs)) {
                PathNavigation pathNavigation = navigatingMob.getNavigation();
                if (pathNavigation.shouldRecomputePath(pos)) {
                    navigationsToUpdate.add(pathNavigation);
                }
            }

            try {
                this.yapUpdatingNavigations.set(Boolean.TRUE);

                for (PathNavigation navigation : navigationsToUpdate) {
                    navigation.recomputePath();
                }
            } finally {
                this.yapUpdatingNavigations.set(Boolean.FALSE);
            }
        }
        } // Paper - option to disable pathfinding updates
    }"""

    if old_method not in text:
        raise SystemExit("ServerLevel.sendBlockUpdated body pattern not found")
    text = text.replace(old_method, new_method, 1)

    SL.write_text(text)
    print("Patched ServerLevel: ThreadLocal nav guard + concurrent navigatingMobs (FallingBlock land safe)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
