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


echo "YaP Paper hooks applied"
