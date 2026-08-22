#!/usr/bin/env bash
# Re-apply YaPcore Phase 3 / 3.5 / 3.6 hooks after paper-server:applyPatches
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
TT="$ROOT/vendor/paper/paper-server/src/main/java/ca/spottedleaf/moonrise/common/util/TickThread.java"
SL="$ROOT/vendor/paper/paper-server/src/minecraft/java/net/minecraft/server/level/ServerLevel.java"

if [[ ! -f "$TT" ]]; then
  echo "TickThread.java not found — applyPatches may have failed" >&2
  exit 1
fi
if [[ ! -f "$SL" ]]; then
  echo "ServerLevel.java not found — applyPatches may have failed" >&2
  exit 1
fi

# --- TickThread: allow Yap spatial cores as tick threads ---
if grep -q 'yapcore.phase3.spatial-tick' "$TT"; then
  echo "YaP TickThread hook already present"
else
python3 - <<PY
from pathlib import Path
path = Path("$TT")
text = path.read_text()
old = """    public static boolean isTickThread() {
        return Thread.currentThread() instanceof TickThread;
    }"""
new = """    public static boolean isTickThread() {
        Thread t = Thread.currentThread();
        if (t instanceof TickThread) {
            return true;
        }
        // YaPcore Phase 3 — spatial cores 3–6 are tick-capable when leased
        if (Boolean.getBoolean(\"yapcore.phase3.spatial-tick\")) {
            String n = t.getName();
            return n != null && (n.startsWith(\"yap-t3-spatial\")
                    || n.startsWith(\"yap-t4-spatial\")
                    || n.startsWith(\"yap-t5-spatial\")
                    || n.startsWith(\"yap-t6-spatial\")
                    || n.startsWith(\"yap-t8-boundary\")
                    || n.contains(\"phase3-tick\"));
        }
        return false;
    }"""
if old not in text:
    raise SystemExit("TickThread.isTickThread() pattern not found — Paper may have changed")
path.write_text(text.replace(old, new, 1))
print("Patched TickThread.isTickThread for YaPcore Phase 3")
PY
fi

# --- ServerLevel: Phase 3.5 + 3.6 (idempotent python) ---
python3 - <<'PY'
from pathlib import Path
import re

path = Path("/home/xydroc/Desktop/YaPcore/vendor/paper/paper-server/src/minecraft/java/net/minecraft/server/level/ServerLevel.java")
text = path.read_text()
changed = False

# If a full prior helper block exists without 3.6, we still add 3.6 pieces below.

# Ensure Phase 3.5 helpers exist (minimal insert if missing entirely)
if "yapResolveWorldTickBridge" not in text:
    helper = r'''
    // YaPcore Phase 3.5 — cached reflection bridge to host InteriorWorldTickBridge
    private static volatile Class<?> yapWorldTickBridgeCl;
    private static volatile java.lang.reflect.Method yapIsInteriorChunkM;
    private static volatile java.lang.reflect.Method yapOfferBlockM;
    private static volatile java.lang.reflect.Method yapOfferFluidM;
    private static volatile java.lang.reflect.Method yapOfferRandomM;
    private static volatile java.lang.reflect.Method yapFlushBlockFluidM;
    private static volatile java.lang.reflect.Method yapFlushRandomM;
    private static volatile boolean yapWorldTickBridgeFailed;
    private static volatile boolean yapCachedSpatialTick;
    private static volatile boolean yapCachedSpatialRandom;
    private static volatile boolean yapCachedSpatialBlockFluid;
    private static volatile boolean yapCachedFlushing;
    private static volatile long yapFlagsNanos;

    private static void yapRefreshCachedFlags() {
        yapCachedFlushing = Boolean.getBoolean("yapcore.phase3.spatial-tick.flushing");
        long now = System.nanoTime();
        if (now - yapFlagsNanos < 100_000_000L) {
            return;
        }
        yapCachedSpatialTick = Boolean.getBoolean("yapcore.phase3.spatial-tick");
        yapCachedSpatialRandom = Boolean.getBoolean("yapcore.phase3.spatial-random");
        yapCachedSpatialBlockFluid = Boolean.getBoolean("yapcore.phase3.spatial-blockfluid");
        yapFlagsNanos = now;
    }

    private static boolean yapResolveWorldTickBridge() {
        if (yapWorldTickBridgeFailed) return false;
        if (yapWorldTickBridgeCl != null) return true;
        try {
            Class<?> cl = Class.forName("com.yapcore.paper.phase3.nms.InteriorWorldTickBridge", true, ClassLoader.getSystemClassLoader());
            yapIsInteriorChunkM = cl.getMethod("isInteriorChunk", int.class, int.class);
            yapOfferBlockM = cl.getMethod("offerBlock", Object.class, Object.class, Object.class);
            yapOfferFluidM = cl.getMethod("offerFluid", Object.class, Object.class, Object.class);
            yapOfferRandomM = cl.getMethod("offerRandom", Object.class, Object.class, int.class);
            yapFlushBlockFluidM = cl.getMethod("flushBlockFluid");
            yapFlushRandomM = cl.getMethod("flushRandom");
            yapWorldTickBridgeCl = cl;
            return true;
        } catch (Throwable t) {
            yapWorldTickBridgeFailed = true;
            return false;
        }
    }

    private static boolean yapInteriorChunk(int cx, int cz) {
        // Sign-bit quadrants: only chunks on x=0 or z=0 plane touch another quadrant
        return !(cx == -1 || cx == 0 || cz == -1 || cz == 0);
    }
    private static void yapOfferInteriorBlock(Object level, Object pos, Object type) {
        try {
            if (!yapResolveWorldTickBridge()) return;
            yapOfferBlockM.invoke(null, level, pos, type);
        } catch (Throwable ignored) {}
    }
    private static void yapOfferInteriorFluid(Object level, Object pos, Object type) {
        try {
            if (!yapResolveWorldTickBridge()) return;
            yapOfferFluidM.invoke(null, level, pos, type);
        } catch (Throwable ignored) {}
    }
    private static void yapOfferInteriorRandom(Object level, Object chunk, int tickSpeed) {
        try {
            if (!yapResolveWorldTickBridge()) return;
            yapOfferRandomM.invoke(null, level, chunk, tickSpeed);
        } catch (Throwable ignored) {}
    }
    private static void yapFlushInteriorBlockFluid() {
        try {
            if (!yapResolveWorldTickBridge()) return;
            yapFlushBlockFluidM.invoke(null);
        } catch (Throwable ignored) {}
    }
    private static void yapFlushInteriorRandom() {
        try {
            if (!yapResolveWorldTickBridge()) return;
            yapFlushRandomM.invoke(null);
        } catch (Throwable ignored) {}
    }
    // Paper end - YaPcore Phase 3.5 helpers

'''
    anchor = "    public void tickNonPassenger(final Entity entity) {"
    if anchor not in text:
        raise SystemExit("tickNonPassenger anchor not found")
    text = text.replace(anchor, helper + anchor, 1)
    changed = True
    print("Inserted Phase 3.5 helper block")

# Phase 3.6 flags + methods (idempotent)
if "yapCachedSpatialBlockEntities" not in text:
    text = text.replace(
        "    private static volatile boolean yapCachedSpatialBlockFluid;\n    private static volatile boolean yapCachedFlushing;",
        "    private static volatile boolean yapCachedSpatialBlockFluid;\n"
        "    private static volatile boolean yapCachedSpatialBlockEntities;\n"
        "    private static volatile boolean yapCachedSpatialRedstone;\n"
        "    private static volatile boolean yapCachedFlushing;",
        1,
    )
    changed = True

if "yapCachedSpatialBlockEntities = Boolean.getBoolean" not in text:
    text = text.replace(
        '        yapCachedSpatialBlockFluid = Boolean.getBoolean("yapcore.phase3.spatial-blockfluid");\n        yapFlagsNanos = now;',
        '        yapCachedSpatialBlockFluid = Boolean.getBoolean("yapcore.phase3.spatial-blockfluid");\n'
        '        yapCachedSpatialBlockEntities = Boolean.getBoolean("yapcore.phase3.spatial-blockentities");\n'
        '        yapCachedSpatialRedstone = Boolean.getBoolean("yapcore.phase3.spatial-redstone");\n'
        '        yapFlagsNanos = now;',
        1,
    )
    changed = True

if "yapOfferBlockEntityM" not in text:
    text = text.replace(
        "    private static volatile java.lang.reflect.Method yapFlushRandomM;",
        "    private static volatile java.lang.reflect.Method yapFlushRandomM;\n"
        "    private static volatile java.lang.reflect.Method yapOfferBlockEntityM;\n"
        "    private static volatile java.lang.reflect.Method yapFlushBlockEntitiesM;\n"
        "    private static volatile java.lang.reflect.Method yapOfferBlockEventM;\n"
        "    private static volatile java.lang.reflect.Method yapFlushBlockEventsM;",
        1,
    )
    changed = True

if 'getMethod("offerBlockEntity"' not in text:
    text = text.replace(
        '            yapFlushRandomM = cl.getMethod("flushRandom");\n            yapWorldTickBridgeCl = cl;',
        '            yapFlushRandomM = cl.getMethod("flushRandom");\n'
        '            yapOfferBlockEntityM = cl.getMethod("offerBlockEntity", Object.class);\n'
        '            yapFlushBlockEntitiesM = cl.getMethod("flushBlockEntities");\n'
        '            yapOfferBlockEventM = cl.getMethod("offerBlockEvent", Object.class, Object.class);\n'
        '            yapFlushBlockEventsM = cl.getMethod("flushBlockEvents");\n'
        '            yapWorldTickBridgeCl = cl;',
        1,
    )
    changed = True

helpers36 = '''
    private static void yapOfferInteriorBlockEntity(Object ticker) {
        try {
            if (!yapResolveWorldTickBridge()) return;
            yapOfferBlockEntityM.invoke(null, ticker);
        } catch (Throwable ignored) {}
    }
    private static void yapFlushInteriorBlockEntities() {
        try {
            if (!yapResolveWorldTickBridge()) return;
            yapFlushBlockEntitiesM.invoke(null);
        } catch (Throwable ignored) {}
    }
    private static void yapOfferInteriorBlockEvent(Object level, Object eventData) {
        try {
            if (!yapResolveWorldTickBridge()) return;
            yapOfferBlockEventM.invoke(null, level, eventData);
        } catch (Throwable ignored) {}
    }
    private static void yapFlushInteriorBlockEvents() {
        try {
            if (!yapResolveWorldTickBridge()) return;
            yapFlushBlockEventsM.invoke(null);
        } catch (Throwable ignored) {}
    }

    private void yapTickBlockEntitiesSpatial() {
        this.tickingBlockEntities = true;
        if (!this.pendingBlockEntityTickers.isEmpty()) {
            this.blockEntityTickers.addAll(this.pendingBlockEntityTickers);
            this.pendingBlockEntityTickers.clear();
        }
        boolean tickBlockEntities = this.tickRateManager().runsNormally();
        final it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet<@Nullable TickingBlockEntity> toRemove =
                new it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet<>();
        toRemove.add(null);
        for (int tickerIndex = 0; tickerIndex < this.blockEntityTickers.size(); tickerIndex++) {
            final TickingBlockEntity ticker = this.blockEntityTickers.get(tickerIndex);
            if (ticker.isRemoved()) {
                toRemove.add(ticker);
            } else if (tickBlockEntities && this.shouldTickBlocksAt(ticker.getPos())) {
                final BlockPos pos = ticker.getPos();
                if (yapInteriorChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                    yapOfferInteriorBlockEntity(ticker);
                } else {
                    ticker.tick();
                }
            }
        }
        yapFlushInteriorBlockEntities();
        this.blockEntityTickers.removeAll(toRemove);
        this.tickingBlockEntities = false;
    }

    private void yapBroadcastBlockEvent(Object raw) {
        if (!(raw instanceof BlockEventData eventData)) {
            return;
        }
        this.server
            .getPlayerList()
            .broadcast(
                null,
                eventData.pos().getX(),
                eventData.pos().getY(),
                eventData.pos().getZ(),
                64.0,
                this.dimension(),
                new ClientboundBlockEventPacket(eventData.pos(), eventData.block(), eventData.paramA(), eventData.paramB())
            );
    }

    private void yapRunBlockEventsSpatial() {
        this.blockEventsToReschedule.clear();
        while (!this.blockEvents.isEmpty()) {
            BlockEventData eventData = this.blockEvents.removeFirst();
            if (!this.shouldTickBlocksAt(eventData.pos())) {
                this.blockEventsToReschedule.add(eventData);
                continue;
            }
            if (yapInteriorChunk(eventData.pos().getX() >> 4, eventData.pos().getZ() >> 4)) {
                yapOfferInteriorBlockEvent(this, eventData);
            } else if (this.doBlockEvent(eventData)) {
                yapBroadcastBlockEvent(eventData);
            }
        }
        yapFlushInteriorBlockEvents();
        this.blockEvents.addAll(this.blockEventsToReschedule);
    }

'''

if "yapTickBlockEntitiesSpatial" not in text:
    marker = "    // Paper end - YaPcore Phase 3.5 helpers"
    if marker not in text:
        raise SystemExit("Phase 3.5 helper end marker missing — cannot attach 3.6")
    text = text.replace(marker, helpers36 + marker, 1)
    changed = True
    print("Inserted Phase 3.6 BE/redstone helpers")

old_bevt = '''        profiler.popPush("blockEvents");
        if (runs) {
            this.runBlockEvents();
        }'''
new_bevt = '''        profiler.popPush("blockEvents");
        if (runs) {
            yapRefreshCachedFlags();
            if (yapCachedSpatialTick && yapCachedSpatialRedstone) {
                this.yapRunBlockEventsSpatial();
            } else {
                this.runBlockEvents();
            }
        }'''
if old_bevt in text:
    text = text.replace(old_bevt, new_bevt, 1)
    changed = True
    print("Patched blockEvents call site")

old_te = '''            if (this.paperConfig().unsupportedSettings.ticking.blockEntities) { // Paper - option to disable ticking
            profiler.popPush("blockEntities");
            this.tickBlockEntities();
            profiler.pop();
            } // Paper - option to disable ticking'''
new_te = '''            if (this.paperConfig().unsupportedSettings.ticking.blockEntities) { // Paper - option to disable ticking
            profiler.popPush("blockEntities");
            yapRefreshCachedFlags();
            if (yapCachedSpatialTick && yapCachedSpatialBlockEntities) {
                this.yapTickBlockEntitiesSpatial();
            } else {
                this.tickBlockEntities();
            }
            profiler.pop();
            } // Paper - option to disable ticking'''
if old_te in text:
    text = text.replace(old_te, new_te, 1)
    changed = True
    print("Patched tickBlockEntities call site")

# Phase 3.5 block/fluid/random call sites if missing (best-effort)
if "yapFlushInteriorBlockFluid" not in text or "yapOfferInteriorBlock(" not in text:
    print("WARN: Phase 3.5 block/fluid deferral may be missing — rebuild from known-good vendor or re-apply 3.5 section manually")

# --- Phase 3.7: border tick on T8 under DLM leases ---
if "yapCachedSpatialBorders" not in text:
    text = text.replace(
        "    private static volatile boolean yapCachedSpatialRedstone;\n",
        "    private static volatile boolean yapCachedSpatialRedstone;\n"
        "    private static volatile boolean yapCachedSpatialBorders;\n",
        1,
    )
    changed = True

if 'yapCachedSpatialBorders = Boolean.getBoolean' not in text:
    text = text.replace(
        '        yapCachedSpatialRedstone = Boolean.getBoolean("yapcore.phase3.spatial-redstone");\n'
        '        yapFlagsNanos = now;',
        '        yapCachedSpatialRedstone = Boolean.getBoolean("yapcore.phase3.spatial-redstone");\n'
        '        yapCachedSpatialBorders = Boolean.getBoolean("yapcore.phase3.spatial-borders");\n'
        '        yapFlagsNanos = now;',
        1,
    )
    changed = True

border_helpers = '''
    private static volatile java.lang.reflect.Method yapOfferBorderEntityM;
    private static volatile java.lang.reflect.Method yapFlushBorderEntitiesM;
    private static volatile java.lang.reflect.Method yapOfferBorderBlockEntityM;
    private static volatile java.lang.reflect.Method yapFlushBorderBlockEntitiesM;
    private static volatile java.lang.reflect.Method yapOfferBorderBlockEventM;
    private static volatile java.lang.reflect.Method yapFlushBorderBlockEventsM;

    private static void yapOfferBorderEntity(Object entity) {
        try {
            if (!yapResolveWorldTickBridge()) return;
            if (yapOfferBorderEntityM == null) {
                yapOfferBorderEntityM = yapWorldTickBridgeCl.getMethod("offerBorderEntity", Object.class);
            }
            yapOfferBorderEntityM.invoke(null, entity);
        } catch (Throwable ignored) {}
    }
    private static void yapFlushBorderEntities() {
        try {
            if (!yapResolveWorldTickBridge()) return;
            if (yapFlushBorderEntitiesM == null) {
                yapFlushBorderEntitiesM = yapWorldTickBridgeCl.getMethod("flushBorderEntities");
            }
            yapFlushBorderEntitiesM.invoke(null);
        } catch (Throwable ignored) {}
    }
    private static void yapOfferBorderBlockEntity(Object ticker) {
        try {
            if (!yapResolveWorldTickBridge()) return;
            if (yapOfferBorderBlockEntityM == null) {
                yapOfferBorderBlockEntityM = yapWorldTickBridgeCl.getMethod("offerBorderBlockEntity", Object.class);
            }
            yapOfferBorderBlockEntityM.invoke(null, ticker);
        } catch (Throwable ignored) {}
    }
    private static void yapFlushBorderBlockEntities() {
        try {
            if (!yapResolveWorldTickBridge()) return;
            if (yapFlushBorderBlockEntitiesM == null) {
                yapFlushBorderBlockEntitiesM = yapWorldTickBridgeCl.getMethod("flushBorderBlockEntities");
            }
            yapFlushBorderBlockEntitiesM.invoke(null);
        } catch (Throwable ignored) {}
    }
    private static void yapOfferBorderBlockEvent(Object level, Object eventData) {
        try {
            if (!yapResolveWorldTickBridge()) return;
            if (yapOfferBorderBlockEventM == null) {
                yapOfferBorderBlockEventM = yapWorldTickBridgeCl.getMethod("offerBorderBlockEvent", Object.class, Object.class);
            }
            yapOfferBorderBlockEventM.invoke(null, level, eventData);
        } catch (Throwable ignored) {}
    }
    private static void yapFlushBorderBlockEvents() {
        try {
            if (!yapResolveWorldTickBridge()) return;
            if (yapFlushBorderBlockEventsM == null) {
                yapFlushBorderBlockEventsM = yapWorldTickBridgeCl.getMethod("flushBorderBlockEvents");
            }
            yapFlushBorderBlockEventsM.invoke(null);
        } catch (Throwable ignored) {}
    }

'''

if "yapOfferBorderEntity" not in text:
    marker = "    // Paper end - YaPcore Phase 3.5 helpers"
    if marker not in text:
        raise SystemExit("Phase 3.5 helper end marker missing — cannot attach 3.7 borders")
    text = text.replace(marker, border_helpers + marker, 1)
    changed = True
    print("Inserted Phase 3.7 border helpers")

# Interior entity offer/flush helpers (post-applyPatches may only skip-return)
entity_helpers = '''
    private static volatile java.lang.reflect.Method yapOfferEntityM;
    private static volatile java.lang.reflect.Method yapFlushEntitiesM;
    /** @return true if entity was queued for spatial flush (caller must skip main tick) */
    private static boolean yapOfferInteriorEntity(Object entity) {
        try {
            if (!yapResolveWorldTickBridge()) return false;
            if (yapOfferEntityM == null) {
                yapOfferEntityM = yapWorldTickBridgeCl.getMethod("offerEntity", Object.class);
            }
            yapOfferEntityM.invoke(null, entity);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
    private static void yapFlushInteriorEntities() {
        try {
            if (!yapResolveWorldTickBridge()) return;
            if (yapFlushEntitiesM == null) {
                yapFlushEntitiesM = yapWorldTickBridgeCl.getMethod("flushEntities");
            }
            yapFlushEntitiesM.invoke(null);
        } catch (Throwable ignored) {}
    }

'''
if "yapOfferInteriorEntity" not in text:
    marker = "    // Paper end - YaPcore Phase 3.5 helpers"
    text = text.replace(marker, entity_helpers + marker, 1)
    changed = True
    print("Inserted interior entity offer/flush helpers")

# Replace skip-return Phase 3 tickNonPassenger with offer/flush path
old_skip = '''    public void tickNonPassenger(final Entity entity) {
        // Paper start - log detailed entity tick information
        // YaPcore Phase 3: interior non-players are ticked on YapEngine spatial cores
        if (Boolean.getBoolean("yapcore.phase3.spatial-tick")
                && !(entity instanceof net.minecraft.world.entity.player.Player)) {
            int cx = entity.chunkPosition().x();
            int cz = entity.chunkPosition().z();
            // border = any neighbor in another Yap quadrant; skip only true interior
            boolean border = false;
            int selfEast = cx >= 0 ? 1 : 0;
            int selfSouth = cz >= 0 ? 1 : 0;
            int self = selfEast | (selfSouth << 1);
            for (int dx = -1; dx <= 1 && !border; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    int e = (cx + dx) >= 0 ? 1 : 0;
                    int s = (cz + dz) >= 0 ? 1 : 0;
                    if ((e | (s << 1)) != self) { border = true; break; }
                }
            }
            if (!border) {
                return; // YapSpatialTickPlugin + leases will tick on cores 3–6
            }
        }
        // Paper end - YaPcore Phase 3'''
new_offer = '''    public void tickNonPassenger(final Entity entity) {
        // Paper start - log detailed entity tick information
        // YaPcore Phase 3 / 3.7: interior → cores 3–6; border → T8 under DLM (players stay main)
        // CRITICAL: only skip main tick when offer actually queued — never silently drop work.
        yapRefreshCachedFlags();
        if (yapCachedSpatialTick
                && !(entity instanceof net.minecraft.world.entity.player.Player)) {
            int cx = entity.chunkPosition().x();
            int cz = entity.chunkPosition().z();
            // O(1) border: chunks on the x=0 / z=0 quadrant plane
            boolean border = cx == -1 || cx == 0 || cz == -1 || cz == 0;
            if (!border) {
                if (yapOfferInteriorEntity(entity)) {
                    return; // flushed on spatial cores after entity forEach
                }
            } else if (yapCachedSpatialBorders) {
                yapOfferBorderEntity(entity);
                return; // flushed on T8 under DLM lease after entity forEach
            }
        }
        // Paper end - YaPcore Phase 3'''
if old_skip in text:
    text = text.replace(old_skip, new_offer, 1)
    changed = True
    print("Replaced skip-return tickNonPassenger with offer path")
elif "boolean border = cx == -1 || cx == 0 || cz == -1 || cz == 0;" not in text:
    # Upgrade 3×3 border scan → O(1) plane check
    old_loop = '''            boolean border = false;
            int selfEast = cx >= 0 ? 1 : 0;
            int selfSouth = cz >= 0 ? 1 : 0;
            int self = selfEast | (selfSouth << 1);
            for (int dx = -1; dx <= 1 && !border; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    int e = (cx + dx) >= 0 ? 1 : 0;
                    int s = (cz + dz) >= 0 ? 1 : 0;
                    if ((e | (s << 1)) != self) { border = true; break; }
                }
            }'''
    new_o1 = '''            // O(1) border: chunks on the x=0 / z=0 quadrant plane
            boolean border = cx == -1 || cx == 0 || cz == -1 || cz == 0;'''
    if old_loop in text:
        text = text.replace(old_loop, new_o1, 1)
        changed = True
        print("Patched tickNonPassenger O(1) border")
elif "yapOfferBorderEntity(entity)" not in text:
    old_np = '''            if (!border) {
                yapOfferInteriorEntity(entity);
                return; // flushed on spatial cores after entity forEach
            }
        }'''
    new_np = '''            if (!border) {
                yapOfferInteriorEntity(entity);
                return; // flushed on spatial cores after entity forEach
            }
            if (yapCachedSpatialBorders) {
                yapOfferBorderEntity(entity);
                return; // flushed on T8 under DLM lease after entity forEach
            }
        }'''
    if old_np in text:
        text = text.replace(old_np, new_np, 1)
        changed = True
        print("Patched tickNonPassenger border offer")

# Ensure flushEntitiesForced helper exists (must run before/independent of call-site check)
if "yapFlushEntitiesForcedM" not in text:
    old_flush_h = '''    private static void yapFlushInteriorEntities() {
        try {
            if (!yapResolveWorldTickBridge()) return;
            if (yapFlushEntitiesM == null) {
                yapFlushEntitiesM = yapWorldTickBridgeCl.getMethod("flushEntities");
            }
            yapFlushEntitiesM.invoke(null);
        } catch (Throwable ignored) {}
    }'''
    new_flush_h = '''    private static volatile java.lang.reflect.Method yapFlushEntitiesForcedM;
    private static void yapFlushInteriorEntities() {
        try {
            if (!yapResolveWorldTickBridge()) return;
            if (yapFlushEntitiesM == null) {
                yapFlushEntitiesM = yapWorldTickBridgeCl.getMethod("flushEntities");
            }
            yapFlushEntitiesM.invoke(null);
        } catch (Throwable ignored) {}
    }
    private static void yapFlushInteriorEntitiesForced() {
        try {
            if (!yapResolveWorldTickBridge()) return;
            if (yapFlushEntitiesForcedM == null) {
                yapFlushEntitiesForcedM = yapWorldTickBridgeCl.getMethod("flushEntitiesForced");
            }
            yapFlushEntitiesForcedM.invoke(null);
        } catch (Throwable ignored) {}
    }'''
    if old_flush_h in text:
        text = text.replace(old_flush_h, new_flush_h, 1)
        changed = True
        print("Inserted yapFlushInteriorEntitiesForced helper")
    else:
        print("WARN: could not insert yapFlushInteriorEntitiesForced — flush helper pattern missing")

# Upgrade yapInteriorChunk 3×3 → O(1) if still looping
old_interior = '''    private static boolean yapInteriorChunk(int cx, int cz) {
        int selfEast = cx >= 0 ? 1 : 0;
        int selfSouth = cz >= 0 ? 1 : 0;
        int self = selfEast | (selfSouth << 1);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                int e = (cx + dx) >= 0 ? 1 : 0;
                int s = (cz + dz) >= 0 ? 1 : 0;
                if ((e | (s << 1)) != self) return false;
            }
        }
        return true;
    }'''
new_interior = '''    private static boolean yapInteriorChunk(int cx, int cz) {
        // Sign-bit quadrants: only chunks on x=0 or z=0 plane touch another quadrant
        return !(cx == -1 || cx == 0 || cz == -1 || cz == 0);
    }'''
if old_interior in text:
    text = text.replace(old_interior, new_interior, 1)
    changed = True
    print("Patched yapInteriorChunk O(1)")

# Flush after entity forEach (may be missing after applyPatches)
old_fe_anchor = '''                );
            if (this.paperConfig().unsupportedSettings.ticking.blockEntities) { // Paper - option to disable ticking
            profiler.popPush("blockEntities");'''
new_fe_anchor = '''                );
            if (yapCachedSpatialTick) {
                yapFlushInteriorEntities(); // may defer when spatial-blockentities (coalesce)
                if (yapCachedSpatialBorders) {
                    yapFlushBorderEntities();
                }
                if (yapCachedSpatialBlockEntities
                        && !this.paperConfig().unsupportedSettings.ticking.blockEntities) {
                    yapFlushInteriorEntitiesForced();
                }
            }
            if (this.paperConfig().unsupportedSettings.ticking.blockEntities) { // Paper - option to disable ticking
            profiler.popPush("blockEntities");'''
if "yapFlushInteriorEntitiesForced();" not in text and old_fe_anchor in text:
    text = text.replace(old_fe_anchor, new_fe_anchor, 1)
    changed = True
    print("Patched entity flush after forEach (coalesce-aware)")
elif "yapFlushInteriorEntitiesForced();" not in text and "yapFlushInteriorEntities();" in text:
    old_fe = '''            if (yapCachedSpatialTick) {
                yapFlushInteriorEntities();
                if (yapCachedSpatialBorders) {
                    yapFlushBorderEntities();
                }
            }'''
    new_fe = '''            if (yapCachedSpatialTick) {
                yapFlushInteriorEntities(); // may defer when spatial-blockentities (coalesce)
                if (yapCachedSpatialBorders) {
                    yapFlushBorderEntities();
                }
                if (yapCachedSpatialBlockEntities
                        && !this.paperConfig().unsupportedSettings.ticking.blockEntities) {
                    yapFlushInteriorEntitiesForced();
                }
            }'''
    if old_fe in text:
        text = text.replace(old_fe, new_fe, 1)
        changed = True
        print("Patched entity flush coalesce + forced drain")
elif "yapFlushBorderEntities();" not in text:
    old_fe = '''            if (yapCachedSpatialTick) {
                yapFlushInteriorEntities();
            }'''
    new_fe = '''            if (yapCachedSpatialTick) {
                yapFlushInteriorEntities();
                if (yapCachedSpatialBorders) {
                    yapFlushBorderEntities();
                }
            }'''
    if old_fe in text:
        text = text.replace(old_fe, new_fe, 1)
        changed = True
        print("Patched entity border flush")

old_te_b = '''                if (yapInteriorChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                    yapOfferInteriorBlockEntity(ticker);
                } else {
                    ticker.tick();
                }'''
new_te_b = '''                if (yapInteriorChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                    yapOfferInteriorBlockEntity(ticker);
                } else if (yapCachedSpatialBorders) {
                    yapOfferBorderBlockEntity(ticker);
                } else {
                    ticker.tick();
                }'''
if "yapOfferBorderBlockEntity(ticker)" not in text and old_te_b in text:
    text = text.replace(old_te_b, new_te_b, 1)
    changed = True
    print("Patched TE border offer")

old_te_f = '''        yapFlushInteriorBlockEntities();
        this.blockEntityTickers.removeAll(toRemove);'''
new_te_f = '''        yapFlushInteriorBlockEntities();
        if (yapCachedSpatialBorders) {
            yapFlushBorderBlockEntities();
        }
        this.blockEntityTickers.removeAll(toRemove);'''
if "yapFlushBorderBlockEntities();" not in text and old_te_f in text:
    text = text.replace(old_te_f, new_te_f, 1)
    changed = True
    print("Patched TE border flush")

old_ev_b = '''            if (yapInteriorChunk(eventData.pos().getX() >> 4, eventData.pos().getZ() >> 4)) {
                yapOfferInteriorBlockEvent(this, eventData);
            } else if (this.doBlockEvent(eventData)) {
                yapBroadcastBlockEvent(eventData);
            }'''
new_ev_b = '''            if (yapInteriorChunk(eventData.pos().getX() >> 4, eventData.pos().getZ() >> 4)) {
                yapOfferInteriorBlockEvent(this, eventData);
            } else if (yapCachedSpatialBorders) {
                yapOfferBorderBlockEvent(this, eventData);
            } else if (this.doBlockEvent(eventData)) {
                yapBroadcastBlockEvent(eventData);
            }'''
if "yapOfferBorderBlockEvent(this, eventData)" not in text and old_ev_b in text:
    text = text.replace(old_ev_b, new_ev_b, 1)
    changed = True
    print("Patched block-event border offer")

old_ev_f = '''        yapFlushInteriorBlockEvents();
        this.blockEvents.addAll(this.blockEventsToReschedule);'''
new_ev_f = '''        yapFlushInteriorBlockEvents();
        if (yapCachedSpatialBorders) {
            yapFlushBorderBlockEvents();
        }
        this.blockEvents.addAll(this.blockEventsToReschedule);'''
if "yapFlushBorderBlockEvents();" not in text and old_ev_f in text:
    text = text.replace(old_ev_f, new_ev_f, 1)
    changed = True
    print("Patched block-event border flush")

path.write_text(text)
print("YaP ServerLevel hooks OK" + (" (updated)" if changed else " (already current)"))
PY


# --- Level: expose BE ticker fields for ServerLevel spatial tick ---
LVL="$ROOT/vendor/paper/paper-server/src/minecraft/java/net/minecraft/world/level/Level.java"
if [[ -f "$LVL" ]] && ! grep -q 'YaPcore Phase 3.6 — protected so ServerLevel' "$LVL"; then
  python3 - <<'PY2'
from pathlib import Path
path = Path("/home/xydroc/Desktop/YaPcore/vendor/paper/paper-server/src/minecraft/java/net/minecraft/world/level/Level.java")
text = path.read_text()
old = """    private final List<TickingBlockEntity> pendingBlockEntityTickers = Lists.newArrayList();
    private boolean tickingBlockEntities;"""
new = """    // YaPcore Phase 3.6 — protected so ServerLevel can spatial-tick under leases
    protected final List<TickingBlockEntity> pendingBlockEntityTickers = Lists.newArrayList();
    protected boolean tickingBlockEntities;"""
if old in text:
    path.write_text(text.replace(old, new, 1))
    print("Patched Level pendingBlockEntityTickers visibility")
else:
    print("Level BE field pattern missing or already patched")
PY2
fi

# --- ChunkMap: Phase 3.8/3.9/Leaf-gap spatial non-player tracker sendChanges ---
CM="$ROOT/vendor/paper/paper-server/src/minecraft/java/net/minecraft/server/level/ChunkMap.java"
if [[ -f "$CM" ]]; then
  if grep -q 'offerTrackerTickUnit' "$CM" && grep -q 'yapOfferTrackerTickUnit' "$CM"; then
    echo "YaP ChunkMap moonrise\$tick offload already present"
  elif grep -q 'yapOfferTrackerMh' "$CM"; then
    echo "YaP ChunkMap Leaf-gap tracker MethodHandle hook present (will moonrise-offload below)"
  elif grep -q 'yapSpatialTrackerEnabledM\|yapOfferTrackerSendChanges' "$CM"; then
    echo "Upgrading YaP ChunkMap tracker → Leaf-gap MethodHandles + cx/cz…"
    python3 - <<'PYCMLEAF'
from pathlib import Path
import re
path = Path("/home/xydroc/Desktop/YaPcore/vendor/paper/paper-server/src/minecraft/java/net/minecraft/server/level/ChunkMap.java")
text = path.read_text()
# Replace bridge statics + helpers from first "private static volatile Class<?> yapTrackerBridgeCl"
# through end marker
pat = re.compile(
    r"    private static volatile Class<\?> yapTrackerBridgeCl;.*?    // Paper end - optimise entity tracker / YaPcore spatial tracker",
    re.S,
)
new = '''    private static volatile Class<?> yapTrackerBridgeCl;
    private static volatile java.lang.invoke.MethodHandle yapOfferTrackerMh;
    private static volatile java.lang.invoke.MethodHandle yapFlushTrackerMh;
    private static volatile java.lang.invoke.MethodHandle yapSpatialTrackerEnabledMh;
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
            yapOfferTrackerMh = lookup.findStatic(yapTrackerBridgeCl, "offerTrackerSendChanges",
                    java.lang.invoke.MethodType.methodType(boolean.class, Object.class, Object.class, int.class, int.class));
            yapFlushTrackerMh = lookup.findStatic(yapTrackerBridgeCl, "flushTrackerSendChanges",
                    java.lang.invoke.MethodType.methodType(void.class));
            yapSpatialTrackerEnabledMh = lookup.findStatic(yapTrackerBridgeCl, "spatialTrackerEnabled",
                    java.lang.invoke.MethodType.methodType(boolean.class));
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

    private static boolean yapOfferTrackerSendChanges(final Entity entity, final ChunkMap.TrackedEntity tracker) {
        try {
            if (!yapEnsureTrackerBridge() || yapOfferTrackerMh == null) {
                return false;
            }
            if (yapTrackerSkipClean && tracker.serverEntity.yapIsCleanTrackerSend()) {
                tracker.serverEntity.yapBumpCleanTrackerTick();
                if (yapNoteTrackerSkipMh != null) {
                    yapNoteTrackerSkipMh.invokeExact();
                }
                return true;
            }
            final net.minecraft.world.level.ChunkPos pos = entity.chunkPosition();
            return (boolean) yapOfferTrackerMh.invokeExact(
                    (Object) entity, (Object) tracker.serverEntity, pos.x(), pos.z());
        } catch (Throwable t) {
            yapTrackerBridgeFailed = true;
            return false;
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
    // Paper end - optimise entity tracker / YaPcore spatial tracker'''
m = pat.search(text)
if not m:
    raise SystemExit("WARN: ChunkMap tracker bridge block not found for Leaf-gap upgrade")
path.write_text(pat.sub(new, text, count=1))
print("Upgraded ChunkMap to Leaf-gap MethodHandle tracker hooks")
PYCMLEAF
  else
    python3 - <<'PYCM'
from pathlib import Path
path = Path("/home/xydroc/Desktop/YaPcore/vendor/paper/paper-server/src/minecraft/java/net/minecraft/server/level/ChunkMap.java")
text = path.read_text()
old = """    private void newTrackerTick() {
        this.iteratingTrackerEntities = true;
        try {
            final ca.spottedleaf.moonrise.common.list.ReferenceList<net.minecraft.world.entity.Entity> trackerEntities = this.trackerEntities;
            final Entity[] trackerEntitiesRaw = trackerEntities.getRawDataUnchecked();
            for (int i = 0, len = trackerEntities.size(); i < len; ++i) {
                final Entity entity = trackerEntitiesRaw[i];
                final ChunkMap.TrackedEntity tracker = ((ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerEntity)entity).moonrise$getTrackedEntity();
                if (tracker == null) {
                    continue;
                }
                ((ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerTrackedEntity)tracker).moonrise$tick(((ca.spottedleaf.moonrise.patches.chunk_system.entity.ChunkSystemEntity)entity).moonrise$getChunkData().nearbyPlayers);
                if (((ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerTrackedEntity)tracker).moonrise$hasPlayers()
                    || ((ca.spottedleaf.moonrise.patches.chunk_system.entity.ChunkSystemEntity)entity).moonrise$getChunkStatus().isOrAfter(FullChunkStatus.ENTITY_TICKING)) {
                    tracker.serverEntity.sendChanges();
                }
            }
        } finally {
            this.iteratingTrackerEntities = false;
        }
    }
    // Paper end - optimise entity tracker"""
new = """    private void newTrackerTick() {
        this.iteratingTrackerEntities = true;
        try {
            final ca.spottedleaf.moonrise.common.list.ReferenceList<net.minecraft.world.entity.Entity> trackerEntities = this.trackerEntities;
            final Entity[] trackerEntitiesRaw = trackerEntities.getRawDataUnchecked();
            // YaPcore Phase 3.8/3.9 — non-player sendChanges on spatial cores; moonrise$tick + players stay main
            final boolean yapSpatialTracker = yapSpatialTrackerEnabled();
            for (int i = 0, len = trackerEntities.size(); i < len; ++i) {
                final Entity entity = trackerEntitiesRaw[i];
                final ChunkMap.TrackedEntity tracker = ((ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerEntity)entity).moonrise$getTrackedEntity();
                if (tracker == null) {
                    continue;
                }
                ((ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerTrackedEntity)tracker).moonrise$tick(((ca.spottedleaf.moonrise.patches.chunk_system.entity.ChunkSystemEntity)entity).moonrise$getChunkData().nearbyPlayers);
                if (((ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerTrackedEntity)tracker).moonrise$hasPlayers()
                    || ((ca.spottedleaf.moonrise.patches.chunk_system.entity.ChunkSystemEntity)entity).moonrise$getChunkStatus().isOrAfter(FullChunkStatus.ENTITY_TICKING)) {
                    if (yapSpatialTracker
                            && !(entity instanceof net.minecraft.world.entity.player.Player)
                            && yapOfferTrackerSendChanges(entity, tracker)) {
                        continue;
                    }
                    tracker.serverEntity.sendChanges();
                }
            }
            if (yapSpatialTracker) {
                yapFlushTrackerSendChanges();
            }
        } finally {
            this.iteratingTrackerEntities = false;
        }
    }

    private static volatile Class<?> yapTrackerBridgeCl;
    private static volatile java.lang.invoke.MethodHandle yapOfferTrackerMh;
    private static volatile java.lang.invoke.MethodHandle yapFlushTrackerMh;
    private static volatile java.lang.invoke.MethodHandle yapSpatialTrackerEnabledMh;
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
            yapOfferTrackerMh = lookup.findStatic(yapTrackerBridgeCl, "offerTrackerSendChanges",
                    java.lang.invoke.MethodType.methodType(boolean.class, Object.class, Object.class, int.class, int.class));
            yapFlushTrackerMh = lookup.findStatic(yapTrackerBridgeCl, "flushTrackerSendChanges",
                    java.lang.invoke.MethodType.methodType(void.class));
            yapSpatialTrackerEnabledMh = lookup.findStatic(yapTrackerBridgeCl, "spatialTrackerEnabled",
                    java.lang.invoke.MethodType.methodType(boolean.class));
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

    private static boolean yapOfferTrackerSendChanges(final Entity entity, final ChunkMap.TrackedEntity tracker) {
        try {
            if (!yapEnsureTrackerBridge() || yapOfferTrackerMh == null) {
                return false;
            }
            if (yapTrackerSkipClean && tracker.serverEntity.yapIsCleanTrackerSend()) {
                tracker.serverEntity.yapBumpCleanTrackerTick();
                if (yapNoteTrackerSkipMh != null) {
                    yapNoteTrackerSkipMh.invokeExact();
                }
                return true;
            }
            final net.minecraft.world.level.ChunkPos pos = entity.chunkPosition();
            return (boolean) yapOfferTrackerMh.invokeExact(
                    (Object) entity, (Object) tracker.serverEntity, pos.x(), pos.z());
        } catch (Throwable t) {
            yapTrackerBridgeFailed = true;
            return false;
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
if old not in text:
    raise SystemExit("ChunkMap.newTrackerTick pattern not found — already patched or Paper changed")
path.write_text(text.replace(old, new, 1))
print("Patched ChunkMap.newTrackerTick for YaPcore Leaf-gap spatial-tracker")
PYCM
  fi

  # Serialize trackerEntities for Yap spatial TickThread cores (ReferenceList is not concurrent).
  # Prevents ArrayIndexOutOfBoundsException Index -1 under multiplayer NaturalSpawner + interior tick.
  if grep -q 'yapTrackerEntitiesDirty' "$CM"; then
    echo "YaP ChunkMap trackerEntities dirty-bit snapshot already present"
  elif grep -q 'yapTrackerEntitiesLock' "$CM"; then
    echo "Upgrading ChunkMap trackerEntities → dirty-bit snapshot (high-pop)…"
    python3 - <<'PYCMDIRTY'
from pathlib import Path
path = Path("/home/xydroc/Desktop/YaPcore/vendor/paper/paper-server/src/minecraft/java/net/minecraft/server/level/ChunkMap.java")
text = path.read_text()
if "yapTrackerEntitiesDirty" in text:
    print("dirty-bit already present")
    raise SystemExit(0)

# Mark dirty on list mutations (add inside lock)
old_add = """                    this.checkIteratingTrackerEntities();
                    this.trackerEntities.add(entity);
                    // Paper end - optimise entity tracker"""
new_add = """                    this.checkIteratingTrackerEntities();
                    this.trackerEntities.add(entity);
                    this.yapTrackerEntitiesDirty = true;
                    // Paper end - optimise entity tracker"""
if old_add in text:
    text = text.replace(old_add, new_add, 1)

old_rm = """            this.checkIteratingTrackerEntities();
            this.trackerEntities.remove(entity);
            // Paper end - optimise entity tracker"""
new_rm = """            this.checkIteratingTrackerEntities();
            this.trackerEntities.remove(entity);
            this.yapTrackerEntitiesDirty = true;
            // Paper end - optimise entity tracker"""
if old_rm in text:
    text = text.replace(old_rm, new_rm, 1)

old_fields = """    /** YaPcore — ReferenceList is single-threaded; spatial TickThread cores need a lock. */
    private final Object yapTrackerEntitiesLock = new Object();

    private void checkIteratingTrackerEntities() {
        if (!this.iteratingTrackerEntities) {
            return;
        }

        this.trackerEntities = this.trackerEntities.copy();
        this.iteratingTrackerEntities = false;
    }

    private void newTrackerTick() {
        final Entity[] trackerEntitiesRaw;
        final int len;
        // Snapshot under lock so spatial add/remove cannot corrupt iteration (Paper #14032 + Yap races).
        // Do not hold the lock across sendChanges / spatial flush (deadlock risk).
        synchronized (this.yapTrackerEntitiesLock) {
            this.iteratingTrackerEntities = true;
            final ca.spottedleaf.moonrise.common.list.ReferenceList<net.minecraft.world.entity.Entity> trackerEntities = this.trackerEntities;
            len = trackerEntities.size();
            trackerEntitiesRaw = trackerEntities.getRawDataUnchecked().clone();
        }"""
new_fields = """    /** YaPcore — ReferenceList is single-threaded; spatial TickThread cores need a lock. */
    private final Object yapTrackerEntitiesLock = new Object();
    /** YaPcore high-pop — avoid cloning trackerEntities every tick; refresh only when dirty. */
    private volatile boolean yapTrackerEntitiesDirty = true;
    private Entity[] yapTrackerEntitiesSnap = EMPTY_ENTITY_ARRAY;
    private int yapTrackerEntitiesSnapLen = 0;

    private void checkIteratingTrackerEntities() {
        if (!this.iteratingTrackerEntities) {
            return;
        }

        this.trackerEntities = this.trackerEntities.copy();
        this.iteratingTrackerEntities = false;
        this.yapTrackerEntitiesDirty = true;
    }

    private void newTrackerTick() {
        final Entity[] trackerEntitiesRaw;
        final int len;
        // Snapshot under lock so spatial add/remove cannot corrupt iteration (Paper #14032 + Yap races).
        // Dirty-bit: clone only when the list changed — Paper/Leaf never pay this tax (single-thread).
        // Do not hold the lock across sendChanges / spatial flush (deadlock risk).
        synchronized (this.yapTrackerEntitiesLock) {
            this.iteratingTrackerEntities = true;
            if (this.yapTrackerEntitiesDirty || this.yapTrackerEntitiesSnap == null) {
                final ca.spottedleaf.moonrise.common.list.ReferenceList<net.minecraft.world.entity.Entity> trackerEntities = this.trackerEntities;
                this.yapTrackerEntitiesSnapLen = trackerEntities.size();
                this.yapTrackerEntitiesSnap = trackerEntities.getRawDataUnchecked().clone();
                this.yapTrackerEntitiesDirty = false;
            }
            len = this.yapTrackerEntitiesSnapLen;
            trackerEntitiesRaw = this.yapTrackerEntitiesSnap;
        }"""
if old_fields not in text:
    raise SystemExit("WARN: ChunkMap dirty-bit upgrade pattern not found (tree may already differ)")
text = text.replace(old_fields, new_fields, 1)

# Player-path skip-clean before main sendChanges
old_send = """                    if (yapSpatialTracker
                            && !(entity instanceof net.minecraft.world.entity.player.Player)
                            && yapOfferTrackerSendChanges(entity, tracker)) {
                        continue;
                    }
                    tracker.serverEntity.sendChanges();"""
new_send = """                    if (yapSpatialTracker
                            && !(entity instanceof net.minecraft.world.entity.player.Player)
                            && yapOfferTrackerSendChanges(entity, tracker)) {
                        continue;
                    }
                    // Phase 3.9 player-path: skip clean sendChanges on main (bots) without Folia player tick
                    if (yapTrackerSkipClean && tracker.serverEntity.yapIsCleanTrackerSend()) {
                        tracker.serverEntity.yapBumpCleanTrackerTick();
                        try {
                            if (yapNoteTrackerSkipMh != null) {
                                yapNoteTrackerSkipMh.invokeExact();
                            }
                        } catch (Throwable ignored) {
                        }
                        continue;
                    }
                    tracker.serverEntity.sendChanges();"""
if old_send in text and "Phase 3.9 player-path" not in text:
    text = text.replace(old_send, new_send, 1)

old_note = """            if (yapTrackerSkipClean && tracker.serverEntity.yapIsCleanTrackerSend()) {
                tracker.serverEntity.yapBumpCleanTrackerTick();
                if (yapNoteTrackerSkipMh != null) {
                    yapNoteTrackerSkipMh.invokeExact();
                }
                return true;
            }"""
new_note = """            if (yapTrackerSkipClean && tracker.serverEntity.yapIsCleanTrackerSend()) {
                tracker.serverEntity.yapBumpCleanTrackerTick();
                yapNoteTrackerSkip();
                return true;
            }"""
if old_note in text:
    text = text.replace(old_note, new_note, 1)

if "private static void yapNoteTrackerSkip()" not in text:
    needle = "    private static void yapFlushTrackerSendChanges() {"
    helper = """    private static void yapNoteTrackerSkip() {
        try {
            if (yapNoteTrackerSkipMh != null) {
                yapNoteTrackerSkipMh.invokeExact();
            }
        } catch (Throwable ignored) {
        }
    }

    private static void yapFlushTrackerSendChanges() {"""
    if needle not in text:
        raise SystemExit("WARN: yapFlushTrackerSendChanges not found for yapNoteTrackerSkip helper")
    text = text.replace(needle, helper, 1)

path.write_text(text)
print("Patched ChunkMap dirty-bit snapshot + player-path skip-clean")
PYCMDIRTY
  elif grep -q 'yapOfferTrackerMh' "$CM"; then
    echo "Upgrading ChunkMap trackerEntities → Yap spatial lock + snapshot iteration…"
    python3 - <<'PYCMLOCK'
from pathlib import Path
path = Path("/home/xydroc/Desktop/YaPcore/vendor/paper/paper-server/src/minecraft/java/net/minecraft/server/level/ChunkMap.java")
text = path.read_text()
old_add = """                ((ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerEntity)entity).moonrise$setTrackedEntity(trackedEntity);
                // note: the tick loop is OK when adding entities
                this.trackerEntities.add(entity);
                // Paper end - optimise entity tracker"""
new_add = """                ((ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerEntity)entity).moonrise$setTrackedEntity(trackedEntity);
                // YaPcore: spatial cores are TickThread — serialize Moonrise ReferenceList mutations
                synchronized (this.yapTrackerEntitiesLock) {
                    this.checkIteratingTrackerEntities();
                    this.trackerEntities.add(entity);
                    this.yapTrackerEntitiesDirty = true;
                }
                // Paper end - optimise entity tracker"""
if old_add not in text and "yapTrackerEntitiesLock" not in text:
    raise SystemExit("WARN: ChunkMap.addEntity trackerEntities pattern not found for lock upgrade")
if old_add in text:
    text = text.replace(old_add, new_add, 1)

old_rm = """        ((ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerEntity)entity).moonrise$setTrackedEntity(null);
        this.checkIteratingTrackerEntities();
        this.trackerEntities.remove(entity);
        // Paper end - optimise entity tracker"""
new_rm = """        ((ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerEntity)entity).moonrise$setTrackedEntity(null);
        synchronized (this.yapTrackerEntitiesLock) {
            this.checkIteratingTrackerEntities();
            this.trackerEntities.remove(entity);
            this.yapTrackerEntitiesDirty = true;
        }
        // Paper end - optimise entity tracker"""
if old_rm in text:
    text = text.replace(old_rm, new_rm, 1)

old_tick = """    private ca.spottedleaf.moonrise.common.list.ReferenceList<net.minecraft.world.entity.Entity> trackerEntities = new ca.spottedleaf.moonrise.common.list.ReferenceList<>(EMPTY_ENTITY_ARRAY);
    private boolean iteratingTrackerEntities = false;

    private void checkIteratingTrackerEntities() {
        if (!this.iteratingTrackerEntities) {
            return;
        }

        this.trackerEntities = this.trackerEntities.copy();
        this.iteratingTrackerEntities = false;
    }

    private void newTrackerTick() {
        this.iteratingTrackerEntities = true;
        try {
            final ca.spottedleaf.moonrise.common.list.ReferenceList<net.minecraft.world.entity.Entity> trackerEntities = this.trackerEntities;
            final Entity[] trackerEntitiesRaw = trackerEntities.getRawDataUnchecked();
            // YaPcore Phase 3.8/3.9 — non-player sendChanges on spatial cores; moonrise$tick + players stay main
            final boolean yapSpatialTracker = yapSpatialTrackerEnabled();
            for (int i = 0, len = trackerEntities.size(); i < len; ++i) {
                final Entity entity = trackerEntitiesRaw[i];
                final ChunkMap.TrackedEntity tracker = ((ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerEntity)entity).moonrise$getTrackedEntity();
                if (tracker == null) {
                    continue;
                }
                ((ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerTrackedEntity)tracker).moonrise$tick(((ca.spottedleaf.moonrise.patches.chunk_system.entity.ChunkSystemEntity)entity).moonrise$getChunkData().nearbyPlayers);
                if (((ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerTrackedEntity)tracker).moonrise$hasPlayers()
                    || ((ca.spottedleaf.moonrise.patches.chunk_system.entity.ChunkSystemEntity)entity).moonrise$getChunkStatus().isOrAfter(FullChunkStatus.ENTITY_TICKING)) {
                    if (yapSpatialTracker
                            && !(entity instanceof net.minecraft.world.entity.player.Player)
                            && yapOfferTrackerSendChanges(entity, tracker)) {
                        continue;
                    }
                    tracker.serverEntity.sendChanges();
                }
            }
            if (yapSpatialTracker) {
                yapFlushTrackerSendChanges();
            }
        } finally {
            this.iteratingTrackerEntities = false;
        }
    }"""
new_tick = """    private ca.spottedleaf.moonrise.common.list.ReferenceList<net.minecraft.world.entity.Entity> trackerEntities = new ca.spottedleaf.moonrise.common.list.ReferenceList<>(EMPTY_ENTITY_ARRAY);
    private boolean iteratingTrackerEntities = false;
    /** YaPcore — ReferenceList is single-threaded; spatial TickThread cores need a lock. */
    private final Object yapTrackerEntitiesLock = new Object();
    /** YaPcore high-pop — avoid cloning trackerEntities every tick; refresh only when dirty. */
    private volatile boolean yapTrackerEntitiesDirty = true;
    private Entity[] yapTrackerEntitiesSnap = EMPTY_ENTITY_ARRAY;
    private int yapTrackerEntitiesSnapLen = 0;

    private void checkIteratingTrackerEntities() {
        if (!this.iteratingTrackerEntities) {
            return;
        }

        this.trackerEntities = this.trackerEntities.copy();
        this.iteratingTrackerEntities = false;
        this.yapTrackerEntitiesDirty = true;
    }

    private void newTrackerTick() {
        final Entity[] trackerEntitiesRaw;
        final int len;
        // Snapshot under lock so spatial add/remove cannot corrupt iteration (Paper #14032 + Yap races).
        // Dirty-bit: clone only when the list changed — Paper/Leaf never pay this tax (single-thread).
        // Do not hold the lock across sendChanges / spatial flush (deadlock risk).
        synchronized (this.yapTrackerEntitiesLock) {
            this.iteratingTrackerEntities = true;
            if (this.yapTrackerEntitiesDirty || this.yapTrackerEntitiesSnap == null) {
                final ca.spottedleaf.moonrise.common.list.ReferenceList<net.minecraft.world.entity.Entity> trackerEntities = this.trackerEntities;
                this.yapTrackerEntitiesSnapLen = trackerEntities.size();
                this.yapTrackerEntitiesSnap = trackerEntities.getRawDataUnchecked().clone();
                this.yapTrackerEntitiesDirty = false;
            }
            len = this.yapTrackerEntitiesSnapLen;
            trackerEntitiesRaw = this.yapTrackerEntitiesSnap;
        }
        try {
            // YaPcore Phase 3.8/3.9 — non-player sendChanges on spatial cores; moonrise$tick + players stay main
            final boolean yapSpatialTracker = yapSpatialTrackerEnabled();
            for (int i = 0; i < len; ++i) {
                final Entity entity = trackerEntitiesRaw[i];
                if (entity == null) {
                    continue;
                }
                final ChunkMap.TrackedEntity tracker = ((ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerEntity)entity).moonrise$getTrackedEntity();
                if (tracker == null) {
                    continue;
                }
                ((ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerTrackedEntity)tracker).moonrise$tick(((ca.spottedleaf.moonrise.patches.chunk_system.entity.ChunkSystemEntity)entity).moonrise$getChunkData().nearbyPlayers);
                if (((ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerTrackedEntity)tracker).moonrise$hasPlayers()
                    || ((ca.spottedleaf.moonrise.patches.chunk_system.entity.ChunkSystemEntity)entity).moonrise$getChunkStatus().isOrAfter(FullChunkStatus.ENTITY_TICKING)) {
                    if (yapSpatialTracker
                            && !(entity instanceof net.minecraft.world.entity.player.Player)
                            && yapOfferTrackerSendChanges(entity, tracker)) {
                        continue;
                    }
                    if (yapTrackerSkipClean && tracker.serverEntity.yapIsCleanTrackerSend()) {
                        tracker.serverEntity.yapBumpCleanTrackerTick();
                        try {
                            if (yapNoteTrackerSkipMh != null) {
                                yapNoteTrackerSkipMh.invokeExact();
                            }
                        } catch (Throwable ignored) {
                        }
                        continue;
                    }
                    tracker.serverEntity.sendChanges();
                }
            }
            if (yapSpatialTracker) {
                yapFlushTrackerSendChanges();
            }
        } finally {
            synchronized (this.yapTrackerEntitiesLock) {
                this.iteratingTrackerEntities = false;
            }
        }
    }"""
if old_tick not in text:
    if "yapTrackerEntitiesLock" not in text:
        raise SystemExit("WARN: ChunkMap.newTrackerTick pattern not found for lock upgrade")
else:
    text = text.replace(old_tick, new_tick, 1)
path.write_text(text)
print("Patched ChunkMap trackerEntities lock + snapshot iteration")
PYCMLOCK
  fi

  # Leaf-gap: move non-player moonrise$tick off main onto spatial cores (idempotent).
  python3 "$ROOT/scripts/patch-chunkmap-moonrise-offload.py"
fi

# --- ServerLevel / PathTypeCache / FallingBlock: spatial-safe block updates (FallingBlock land) ---
python3 "$ROOT/scripts/patch-serverlevel-spatial-block-updates.py"
python3 "$ROOT/scripts/patch-pathtypecache-spatial.py"
python3 "$ROOT/scripts/patch-fallingblock-double-spawn.py"

# --- ServerEntity: Phase 3.9 clean sendChanges early-out + Leaf-gap helpers ---
SE="$ROOT/vendor/paper/paper-server/src/minecraft/java/net/minecraft/server/level/ServerEntity.java"
if [[ -f "$SE" ]]; then
  if grep -q 'passengersUnchanged' "$SE"; then
    echo "YaP ServerEntity passenger fast-path already present"
  elif grep -q 'yapIsCleanTrackerSend' "$SE"; then
    echo "Upgrading ServerEntity yapIsCleanTrackerSend → passenger fast-path…"
    python3 - <<'PYSEPASS'
from pathlib import Path
path = Path("/home/xydroc/Desktop/YaPcore/vendor/paper/paper-server/src/minecraft/java/net/minecraft/server/level/ServerEntity.java")
text = path.read_text()
old = """    boolean yapIsCleanTrackerSend() {
        return !this.forceStateResync
                && !this.entity.needsSync
                && !this.entity.hurtMarked
                && !this.entity.syncPosition
                && (this.tickCount % this.updateInterval) != 0
                && !(this.entity instanceof ItemFrame)
                && !this.entity.getEntityData().isDirty()
                && this.entity.getPassengers().equals(this.lastPassengers);
    }"""
new = """    boolean yapIsCleanTrackerSend() {
        // High-pop: avoid getPassengers().equals when we know the vehicle is still empty
        final boolean passengersUnchanged = this.lastPassengers.isEmpty()
                ? !this.entity.isVehicle()
                : this.entity.getPassengers().equals(this.lastPassengers);
        return !this.forceStateResync
                && !this.entity.needsSync
                && !this.entity.hurtMarked
                && !this.entity.syncPosition
                && (this.tickCount % this.updateInterval) != 0
                && !(this.entity instanceof ItemFrame)
                && !this.entity.getEntityData().isDirty()
                && passengersUnchanged;
    }"""
if old not in text:
    if "passengersUnchanged" in text:
        print("passenger fast-path already present")
        raise SystemExit(0)
    raise SystemExit("WARN: yapIsCleanTrackerSend pattern missing for passenger fast-path")
path.write_text(text.replace(old, new, 1))
print("Patched ServerEntity passenger fast-path")
PYSEPASS
  elif grep -q 'YaPcore Phase 3.9' "$SE"; then
    echo "Upgrading ServerEntity early-out → yapIsCleanTrackerSend helper…"
    python3 - <<'PYSEUP'
from pathlib import Path
path = Path("/home/xydroc/Desktop/YaPcore/vendor/paper/paper-server/src/minecraft/java/net/minecraft/server/level/ServerEntity.java")
text = path.read_text()
old = """    public void sendChanges() {
        // YaPcore Phase 3.9 — skip empty packet work (players stay on main; non-players too)
        // Does not move player tick off the server thread.
        if (!this.forceStateResync
                && !this.entity.needsSync
                && !this.entity.hurtMarked
                && !this.entity.syncPosition
                && (this.tickCount % this.updateInterval) != 0
                && !(this.entity instanceof ItemFrame)
                && !this.entity.getEntityData().isDirty()
                && this.entity.getPassengers().equals(this.lastPassengers)) {
            this.tickCount++;
            return;
        }"""
new = """    /**
     * YaPcore Leaf-gap — true when {@link #sendChanges()} would only bump tickCount.
     * Called from ChunkMap before spatial offer (direct fields, no reflect).
     */
    boolean yapIsCleanTrackerSend() {
        // High-pop: avoid getPassengers().equals when we know the vehicle is still empty
        final boolean passengersUnchanged = this.lastPassengers.isEmpty()
                ? !this.entity.isVehicle()
                : this.entity.getPassengers().equals(this.lastPassengers);
        return !this.forceStateResync
                && !this.entity.needsSync
                && !this.entity.hurtMarked
                && !this.entity.syncPosition
                && (this.tickCount % this.updateInterval) != 0
                && !(this.entity instanceof ItemFrame)
                && !this.entity.getEntityData().isDirty()
                && passengersUnchanged;
    }

    void yapBumpCleanTrackerTick() {
        this.tickCount++;
    }

    public void sendChanges() {
        // YaPcore Phase 3.9 — skip empty packet work (players stay on main; non-players too)
        // Does not move player tick off the server thread.
        if (this.yapIsCleanTrackerSend()) {
            this.yapBumpCleanTrackerTick();
            return;
        }"""
if old not in text:
    raise SystemExit("WARN: ServerEntity Phase 3.9 early-out pattern missing for upgrade")
path.write_text(text.replace(old, new, 1))
print("Upgraded ServerEntity with yapIsCleanTrackerSend")
PYSEUP
  else
    python3 - <<'PYSE'
from pathlib import Path
path = Path("/home/xydroc/Desktop/YaPcore/vendor/paper/paper-server/src/minecraft/java/net/minecraft/server/level/ServerEntity.java")
text = path.read_text()
old = """    public void sendChanges() {
        // Paper start - optimise collisions
        if (((ca.spottedleaf.moonrise.patches.chunk_system.entity.ChunkSystemEntity)this.entity).moonrise$isHardColliding()) {
            this.teleportDelay = 9999;
        }
        // Paper end - optimise collisions
        this.entity.updateDataBeforeSync();"""
new = """    /**
     * YaPcore Leaf-gap — true when {@link #sendChanges()} would only bump tickCount.
     * Called from ChunkMap before spatial offer (direct fields, no reflect).
     */
    boolean yapIsCleanTrackerSend() {
        // High-pop: avoid getPassengers().equals when we know the vehicle is still empty
        final boolean passengersUnchanged = this.lastPassengers.isEmpty()
                ? !this.entity.isVehicle()
                : this.entity.getPassengers().equals(this.lastPassengers);
        return !this.forceStateResync
                && !this.entity.needsSync
                && !this.entity.hurtMarked
                && !this.entity.syncPosition
                && (this.tickCount % this.updateInterval) != 0
                && !(this.entity instanceof ItemFrame)
                && !this.entity.getEntityData().isDirty()
                && passengersUnchanged;
    }

    void yapBumpCleanTrackerTick() {
        this.tickCount++;
    }

    public void sendChanges() {
        // YaPcore Phase 3.9 — skip empty packet work (players stay on main; non-players too)
        // Does not move player tick off the server thread.
        if (this.yapIsCleanTrackerSend()) {
            this.yapBumpCleanTrackerTick();
            return;
        }
        // Paper start - optimise collisions
        if (((ca.spottedleaf.moonrise.patches.chunk_system.entity.ChunkSystemEntity)this.entity).moonrise$isHardColliding()) {
            this.teleportDelay = 9999;
        }
        // Paper end - optimise collisions
        this.entity.updateDataBeforeSync();"""
if old not in text:
    raise SystemExit("ServerEntity.sendChanges pattern not found")
path.write_text(text.replace(old, new, 1))
print("Patched ServerEntity.sendChanges early-out + Leaf-gap helpers")
PYSE
  fi
fi


# --- PathNavigation: Phase 3.10/3.11 distant path throttle ---
PN="$ROOT/vendor/paper/paper-server/src/minecraft/java/net/minecraft/world/entity/ai/navigation/PathNavigation.java"
if [[ -f "$PN" ]]; then
  if ! grep -q 'yapShouldSkipPathRecompute' "$PN"; then
    python3 - <<'PYPN'
from pathlib import Path
path = Path("/home/xydroc/Desktop/YaPcore/vendor/paper/paper-server/src/minecraft/java/net/minecraft/world/entity/ai/navigation/PathNavigation.java")
text = path.read_text()
old = """    public void recomputePath() {
        if (this.level.getGameTime() - this.timeLastRecompute <= 20L || !this.canUpdatePath()) {
            this.hasDelayedRecomputation = true;
        } else if (this.targetPos != null) {
            this.path = null;
            this.path = this.createPath(this.targetPos, this.reachRange);
            this.timeLastRecompute = this.level.getGameTime();
            this.hasDelayedRecomputation = false;
        }
    }"""
new = """    public void recomputePath() {
        // YaPcore Phase 3.10 — first-party distant path throttle (not Leaf source)
        if (yapShouldSkipPathRecompute(this.mob)) {
            return;
        }
        if (this.level.getGameTime() - this.timeLastRecompute <= 20L || !this.canUpdatePath()) {
            this.hasDelayedRecomputation = true;
        } else if (this.targetPos != null) {
            this.path = null;
            this.path = this.createPath(this.targetPos, this.reachRange);
            this.timeLastRecompute = this.level.getGameTime();
            this.hasDelayedRecomputation = false;
        }
    }

    private static volatile Class<?> yapBrainCl;
    private static volatile java.lang.reflect.Method yapSkipPathM;
    private static volatile boolean yapBrainFailed;

    private static boolean yapShouldSkipPathRecompute(final net.minecraft.world.entity.Mob mob) {
        try {
            if (yapBrainFailed || mob == null) {
                return false;
            }
            if (yapBrainCl == null) {
                yapBrainCl = Class.forName(
                        "com.yapcore.paper.phase3.nms.YapDistantBrain",
                        true,
                        ClassLoader.getSystemClassLoader());
                yapSkipPathM = yapBrainCl.getMethod("shouldSkipPathRecompute", Object.class);
            }
            return Boolean.TRUE.equals(yapSkipPathM.invoke(null, mob));
        } catch (Throwable t) {
            yapBrainFailed = true;
            return false;
        }
    }"""
if old not in text:
    raise SystemExit("PathNavigation.recomputePath pattern not found")
path.write_text(text.replace(old, new, 1))
print("Patched PathNavigation.recomputePath for YaP distant-brain")
PYPN
  else
    echo "YaP PathNavigation recomputePath hook already present"
  fi
  if ! grep -q 'skip expensive findPath when distant' "$PN"; then
    python3 - <<'PYCP'
from pathlib import Path
path = Path("/home/xydroc/Desktop/YaPcore/vendor/paper/paper-server/src/minecraft/java/net/minecraft/world/entity/ai/navigation/PathNavigation.java")
text = path.read_text()
old = """        // Paper end - EntityPathfindEvent
        ProfilerFiller profiler = Profiler.get();
        profiler.push("pathfind");"""
new = """        // Paper end - EntityPathfindEvent
        // YaPcore Phase 3.11 — skip expensive findPath when distant (not Leaf source)
        if (yapShouldSkipPathRecompute(this.mob)) {
            return this.path;
        }
        ProfilerFiller profiler = Profiler.get();
        profiler.push("pathfind");"""
if old not in text:
    raise SystemExit("PathNavigation.createPath pathfind pattern not found")
path.write_text(text.replace(old, new, 1))
print("Patched PathNavigation.createPath for YaP distant-brain")
PYCP
  else
    echo "YaP PathNavigation createPath hook already present"
  fi
fi

# --- Mob.serverAiStep: Phase 3.11 distant goal throttle ---
MOB="$ROOT/vendor/paper/paper-server/src/minecraft/java/net/minecraft/world/entity/Mob.java"
if [[ -f "$MOB" ]]; then
  if grep -q 'private static boolean yapShouldThrottleGoals' "$MOB"; then
    echo "YaP Mob.serverAiStep distant-goals hook already present"
  else
    python3 - <<'PYMOB'
from pathlib import Path
path = Path("/home/xydroc/Desktop/YaPcore/vendor/paper/paper-server/src/minecraft/java/net/minecraft/world/entity/Mob.java")
text = path.read_text()
old = """        // Paper end - Allow nerfed mobs to jump and float
        ProfilerFiller profiler = Profiler.get();
        profiler.push("sensing");
        this.sensing.tick();
        profiler.pop();"""
new = """        // Paper end - Allow nerfed mobs to jump and float
        // YaPcore Phase 3.11 — first-party distant goal throttle (not Leaf source)
        if (yapShouldThrottleGoals(this)) {
            ProfilerFiller profiler = Profiler.get();
            profiler.push("controls");
            profiler.push("move");
            this.moveControl.tick();
            profiler.popPush("look");
            this.lookControl.tick();
            profiler.popPush("jump");
            this.jumpControl.tick();
            profiler.pop();
            profiler.pop();
            return;
        }
        ProfilerFiller profiler = Profiler.get();
        profiler.push("sensing");
        this.sensing.tick();
        profiler.pop();"""
if old in text:
    text = text.replace(old, new, 1)
elif "yapShouldThrottleGoals(this)" not in text:
    raise SystemExit("Mob.serverAiStep sensing pattern not found")
helper = """
    private static volatile Class<?> yapBrainCl;
    private static volatile java.lang.reflect.Method yapThrottleGoalsM;
    private static volatile boolean yapBrainFailed;

    private static boolean yapShouldThrottleGoals(final Mob mob) {
        try {
            if (yapBrainFailed || mob == null) {
                return false;
            }
            if (yapBrainCl == null) {
                yapBrainCl = Class.forName(
                        "com.yapcore.paper.phase3.nms.YapDistantBrain",
                        true,
                        ClassLoader.getSystemClassLoader());
                yapThrottleGoalsM = yapBrainCl.getMethod("shouldThrottleGoals", Object.class);
            }
            return Boolean.TRUE.equals(yapThrottleGoalsM.invoke(null, mob));
        } catch (Throwable t) {
            yapBrainFailed = true;
            return false;
        }
    }

"""
if "private static boolean yapShouldThrottleGoals" not in text:
    anchor = "    protected void customServerAiStep(final ServerLevel level) {"
    if anchor not in text:
        raise SystemExit("customServerAiStep anchor missing")
    text = text.replace(anchor, helper + anchor, 1)
path.write_text(text)
print("Patched Mob.serverAiStep for YaP distant-goals")
PYMOB
  fi
fi

# --- Brain.tick: Phase 3.11 distant brain throttle ---
BR="$ROOT/vendor/paper/paper-server/src/minecraft/java/net/minecraft/world/entity/ai/Brain.java"
if [[ -f "$BR" ]]; then
  if grep -q 'yapShouldThrottleBrain' "$BR"; then
    echo "YaP Brain.tick distant-brain hook already present"
  else
    python3 - <<'PYBR'
from pathlib import Path
path = Path("/home/xydroc/Desktop/YaPcore/vendor/paper/paper-server/src/minecraft/java/net/minecraft/world/entity/ai/Brain.java")
text = path.read_text()
old = """    public void tick(final ServerLevel level, final E body) {
        this.forgetOutdatedMemories();
        this.tickSensors(level, body);
        this.startEachNonRunningBehavior(level, body);
        this.tickEachRunningBehavior(level, body);
    }"""
new = """    public void tick(final ServerLevel level, final E body) {
        // YaPcore Phase 3.11 — first-party distant brain throttle (not Leaf source)
        if (yapShouldThrottleBrain(body)) {
            this.forgetOutdatedMemories();
            return;
        }
        this.forgetOutdatedMemories();
        this.tickSensors(level, body);
        this.startEachNonRunningBehavior(level, body);
        this.tickEachRunningBehavior(level, body);
    }

    private static volatile Class<?> yapBrainCl;
    private static volatile java.lang.reflect.Method yapThrottleBrainM;
    private static volatile boolean yapBrainFailed;

    private static boolean yapShouldThrottleBrain(final Object body) {
        try {
            if (yapBrainFailed || body == null) {
                return false;
            }
            if (yapBrainCl == null) {
                yapBrainCl = Class.forName(
                        "com.yapcore.paper.phase3.nms.YapDistantBrain",
                        true,
                        ClassLoader.getSystemClassLoader());
                yapThrottleBrainM = yapBrainCl.getMethod("shouldThrottleBrain", Object.class);
            }
            return Boolean.TRUE.equals(yapThrottleBrainM.invoke(null, body));
        } catch (Throwable t) {
            yapBrainFailed = true;
            return false;
        }
    }"""
if old not in text:
    raise SystemExit("Brain.tick pattern not found")
path.write_text(text.replace(old, new, 1))
print("Patched Brain.tick for YaP distant-brain")
PYBR
  fi
fi

echo "YaP Paper hooks applied"
