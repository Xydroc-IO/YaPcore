#!/usr/bin/env python3
"""Upgrade ChunkMap.newTrackerTick so non-player moonrise$tick leaves main.

Idempotent. Called from apply-yap-paper-hooks.sh after other ChunkMap patches.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CM = ROOT / "vendor/paper/paper-server/src/minecraft/java/net/minecraft/server/level/ChunkMap.java"

BRIDGE = """    private static volatile Class<?> yapTrackerBridgeCl;
    private static volatile java.lang.invoke.MethodHandle yapOfferTrackerMh;
    private static volatile java.lang.invoke.MethodHandle yapOfferTrackerSendMh;
    private static volatile java.lang.invoke.MethodHandle yapFlushTrackerMh;
    private static volatile java.lang.invoke.MethodHandle yapSpatialTrackerEnabledMh;
    private static volatile java.lang.invoke.MethodHandle yapSpatialTrackerPlayersMh;
    private static volatile java.lang.invoke.MethodHandle yapNoteTrackerSkipMh;
    private static volatile boolean yapTrackerBridgeFailed;
    private static volatile boolean yapTrackerSkipClean = true;

    private static boolean yapEnsureTrackerBridge() {
        if (yapTrackerBridgeFailed) {
            return false;
        }
        if (yapOfferTrackerMh != null) {
            return true;
        }
        try {
            yapTrackerBridgeCl = Class.forName(
                    "com.yapcore.paper.phase3.nms.InteriorWorldTickBridge",
                    true,
                    ClassLoader.getSystemClassLoader());
            java.lang.invoke.MethodHandles.Lookup lookup = java.lang.invoke.MethodHandles.publicLookup();
            yapOfferTrackerMh = lookup.findStatic(yapTrackerBridgeCl, "offerTrackerTickUnit",
                    java.lang.invoke.MethodType.methodType(boolean.class, Object.class, Object.class, Object.class, int.class, int.class, boolean.class, boolean.class));
            yapOfferTrackerSendMh = lookup.findStatic(yapTrackerBridgeCl, "offerTrackerSendChanges",
                    java.lang.invoke.MethodType.methodType(boolean.class, Object.class, Object.class, int.class, int.class));
            yapFlushTrackerMh = lookup.findStatic(yapTrackerBridgeCl, "flushTrackerSendChanges",
                    java.lang.invoke.MethodType.methodType(void.class));
            yapSpatialTrackerEnabledMh = lookup.findStatic(yapTrackerBridgeCl, "spatialTrackerEnabled",
                    java.lang.invoke.MethodType.methodType(boolean.class));
            try {
                yapSpatialTrackerPlayersMh = lookup.findStatic(yapTrackerBridgeCl, "spatialTrackerPlayersEnabled",
                        java.lang.invoke.MethodType.methodType(boolean.class));
            } catch (Throwable ignored) {
                yapSpatialTrackerPlayersMh = null;
            }
            try {
                yapNoteTrackerSkipMh = lookup.findStatic(yapTrackerBridgeCl, "noteTrackerSkip",
                        java.lang.invoke.MethodType.methodType(void.class));
            } catch (Throwable ignored) {
                yapNoteTrackerSkipMh = null;
            }
            String skip = System.getProperty("yapcore.phase3.spatial-tracker-skip-clean");
            yapTrackerSkipClean = skip == null || Boolean.parseBoolean(skip);
            return true;
        } catch (Throwable t) {
            yapTrackerBridgeFailed = true;
            return false;
        }
    }

    private static boolean yapSpatialTrackerEnabled() {
        try {
            if (!yapEnsureTrackerBridge()) {
                return Boolean.getBoolean("yapcore.phase3.spatial-tracker");
            }
            return (boolean) yapSpatialTrackerEnabledMh.invokeExact();
        } catch (Throwable t) {
            return Boolean.getBoolean("yapcore.phase3.spatial-tracker");
        }
    }

    private static boolean yapSpatialTrackerPlayersEnabled() {
        try {
            if (!yapEnsureTrackerBridge()) {
                String p = System.getProperty("yapcore.phase3.spatial-tracker-players");
                return Boolean.getBoolean("yapcore.phase3.spatial-tracker")
                        && (p == null || Boolean.parseBoolean(p));
            }
            if (yapSpatialTrackerPlayersMh == null) {
                return false;
            }
            return (boolean) yapSpatialTrackerPlayersMh.invokeExact();
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean yapOfferTrackerTickUnit(final Entity entity, final ChunkMap.TrackedEntity tracker,
                                                   final Object nearbyPlayers, final boolean sendChanges,
                                                   final boolean bumpIfNoSend) {
        try {
            if (!yapEnsureTrackerBridge() || yapOfferTrackerMh == null) {
                return false;
            }
            final net.minecraft.world.level.ChunkPos pos = entity.chunkPosition();
            return (boolean) yapOfferTrackerMh.invokeExact(
                    (Object) entity, (Object) tracker, nearbyPlayers, pos.x(), pos.z(), sendChanges, bumpIfNoSend);
        } catch (Throwable t) {
            yapTrackerBridgeFailed = true;
            return false;
        }
    }

    private static boolean yapOfferTrackerSendChanges(final Entity entity, final ChunkMap.TrackedEntity tracker) {
        try {
            if (!yapEnsureTrackerBridge() || yapOfferTrackerSendMh == null) {
                return false;
            }
            final net.minecraft.world.level.ChunkPos pos = entity.chunkPosition();
            return (boolean) yapOfferTrackerSendMh.invokeExact(
                    (Object) entity, (Object) tracker.serverEntity, pos.x(), pos.z());
        } catch (Throwable t) {
            yapTrackerBridgeFailed = true;
            return false;
        }
    }

    private static void yapNoteTrackerSkip() {
        try {
            if (yapNoteTrackerSkipMh != null) {
                yapNoteTrackerSkipMh.invokeExact();
            }
        } catch (Throwable ignored) {
        }
    }

    private static void yapFlushTrackerSendChanges() {
        try {
            if (yapTrackerBridgeFailed || yapFlushTrackerMh == null) {
                return;
            }
            yapFlushTrackerMh.invokeExact();
        } catch (Throwable ignored) {
        }
    }
    // Paper end - optimise entity tracker / YaPcore spatial tracker"""

# Loop body shared by dirty-bit and simple forms (from after yapSpatialTracker=... through flush)
LOOP_BODY = """            // YaPcore Leaf-gap — non-player moonrise$tick + sendChanges on spatial;
            // Phase 3.12 — players: moonrise$tick on main, sendChanges offered to spatial
            final boolean yapSpatialTracker = yapSpatialTrackerEnabled();
            final boolean yapSpatialTrackerPlayers = yapSpatialTrackerPlayersEnabled();
            for (int i = 0; i < len; ++i) {
                final Entity entity = trackerEntitiesRaw[i];
                if (entity == null) {
                    continue;
                }
                final ChunkMap.TrackedEntity tracker = ((ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerEntity)entity).moonrise$getTrackedEntity();
                if (tracker == null) {
                    continue;
                }
                final var nearbyPlayers = ((ca.spottedleaf.moonrise.patches.chunk_system.entity.ChunkSystemEntity)entity).moonrise$getChunkData().nearbyPlayers;
                final boolean needsSend = ((ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerTrackedEntity)tracker).moonrise$hasPlayers()
                    || ((ca.spottedleaf.moonrise.patches.chunk_system.entity.ChunkSystemEntity)entity).moonrise$getChunkStatus().isOrAfter(FullChunkStatus.ENTITY_TICKING);
                if (yapSpatialTracker && !(entity instanceof net.minecraft.world.entity.player.Player)) {
                    final boolean clean = yapTrackerSkipClean && tracker.serverEntity.yapIsCleanTrackerSend();
                    final boolean send = needsSend && !clean;
                    final boolean bump = needsSend && clean;
                    if (yapOfferTrackerTickUnit(entity, tracker, nearbyPlayers, send, bump)) {
                        continue;
                    }
                }
                ((ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerTrackedEntity)tracker).moonrise$tick(nearbyPlayers);
                if (needsSend) {
                    if (yapTrackerSkipClean && tracker.serverEntity.yapIsCleanTrackerSend()) {
                        tracker.serverEntity.yapBumpCleanTrackerTick();
                        yapNoteTrackerSkip();
                        continue;
                    }
                    if (yapSpatialTrackerPlayers
                            && entity instanceof net.minecraft.world.entity.player.Player
                            && yapOfferTrackerSendChanges(entity, tracker)) {
                        continue;
                    }
                    tracker.serverEntity.sendChanges();
                }
            }
            if (yapSpatialTracker) {
                yapFlushTrackerSendChanges();
            }"""


def patch_bridge(text: str) -> str:
    pat = re.compile(
        r"    private static volatile Class<\?> yapTrackerBridgeCl;.*?    // Paper end - optimise entity tracker / YaPcore spatial tracker",
        re.S,
    )
    if not pat.search(text):
        raise SystemExit("ChunkMap tracker bridge block not found")
    return pat.sub(BRIDGE, text, count=1)


def patch_dirty_loop(text: str) -> str:
    """Replace try-body of dirty-bit newTrackerTick (has len + trackerEntitiesRaw)."""
    # Match from comment or "final boolean yapSpatialTracker" inside newTrackerTick through flush
    pat = re.compile(
        r"(        try \{\n)"
        r"(?:            // YaPcore[^\n]*\n)?"
        r"            final boolean yapSpatialTracker = yapSpatialTrackerEnabled\(\);\n"
        r"            for \(int i = 0; i < len; \+\+i\) \{.*?"
        r"            if \(yapSpatialTracker\) \{\n"
        r"                yapFlushTrackerSendChanges\(\);\n"
        r"            \}",
        re.S,
    )
    m = pat.search(text)
    if not m:
        return text
    return pat.sub(r"\1" + LOOP_BODY, text, count=1)


def patch_simple_loop(text: str) -> str:
    """Replace simple (non-dirty) newTrackerTick for-loop if still present."""
    pat = re.compile(
        r"(            final Entity\[\] trackerEntitiesRaw = trackerEntities\.getRawDataUnchecked\(\);\n)"
        r"(?:            // YaPcore[^\n]*\n)?"
        r"            final boolean yapSpatialTracker = yapSpatialTrackerEnabled\(\);\n"
        r"            for \(int i = 0, len = trackerEntities\.size\(\); i < len; \+\+i\) \{.*?"
        r"            if \(yapSpatialTracker\) \{\n"
        r"                yapFlushTrackerSendChanges\(\);\n"
        r"            \}",
        re.S,
    )
    m = pat.search(text)
    if not m:
        return text
    # Adapt LOOP_BODY for simple form: for (int i = 0, len = ...) without null check requirement
    simple = LOOP_BODY.replace(
        "            for (int i = 0; i < len; ++i) {\n"
        "                final Entity entity = trackerEntitiesRaw[i];\n"
        "                if (entity == null) {\n"
        "                    continue;\n"
        "                }",
        "            for (int i = 0, len = trackerEntities.size(); i < len; ++i) {\n"
        "                final Entity entity = trackerEntitiesRaw[i];",
    )
    return pat.sub(r"\1" + simple, text, count=1)


def main() -> int:
    if not CM.is_file():
        print(f"SKIP: {CM} missing (vendor paper not extracted yet)")
        return 0
    text = CM.read_text()
    if "yapOfferTrackerSendChanges" in text and "yapSpatialTrackerPlayersEnabled" in text:
        print("ChunkMap Phase 3.12 player sendChanges offload already present")
        return 0

    text = patch_bridge(text)
    before = text
    text = patch_dirty_loop(text)
    if text == before:
        text = patch_simple_loop(text)
    if "yapOfferTrackerTickUnit" not in text:
        raise SystemExit("FAILED: could not install yapOfferTrackerTickUnit")
    if "offerTrackerTickUnit" not in text:
        raise SystemExit("FAILED: MethodHandle still points at old offer")
    if "yapOfferTrackerSendChanges" not in text:
        raise SystemExit("FAILED: could not install player sendChanges offer")
    CM.write_text(text)
    print("Patched ChunkMap: non-player moonrise + Phase 3.12 player sendChanges offer")
    return 0


if __name__ == "__main__":
    sys.exit(main())