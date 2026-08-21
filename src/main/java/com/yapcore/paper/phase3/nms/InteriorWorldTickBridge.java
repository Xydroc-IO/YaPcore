package com.yapcore.paper.phase3.nms;

import com.yapcore.paper.phase3.PaperTickBridgeHolder;
import com.yapcore.paper.phase3.YapPhase3Flags;
import com.yapcore.paper.phase3.YapSpatialTickCoordinator;
import com.yaplabs.yapengine.core.spatial.SpatialQuadrant;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Phase 3.5 / 3.6 / 3.7 — world work deferred from Paper main onto YapEngine
 * under DLM leases: interior on cores 3–6; border entities/TE/events on T8
 * when {@code yapcore.phase3.spatial-borders} is set.
 * <p>
 * Called reflectively from vendored {@code ServerLevel} (Paper classloader →
 * system classloader host bridge).
 */
public final class InteriorWorldTickBridge {

    private static final Logger LOG = Logger.getLogger("YaPcore.Phase3.WorldTick");
    private static final AtomicLong BLOCKS = new AtomicLong();
    private static final AtomicLong FLUIDS = new AtomicLong();
    private static final AtomicLong RANDOMS = new AtomicLong();
    private static final AtomicLong BLOCK_ENTITIES = new AtomicLong();
    private static final AtomicLong BLOCK_EVENTS = new AtomicLong();
    private static final AtomicLong BORDER_ENTITIES = new AtomicLong();
    private static final AtomicLong BORDER_BLOCK_ENTITIES = new AtomicLong();
    private static final AtomicLong BORDER_BLOCK_EVENTS = new AtomicLong();
    private static final AtomicLong TRACKER_SENDS = new AtomicLong();
    private static final AtomicLong BORDER_TRACKER_SENDS = new AtomicLong();
    private static final AtomicLong TRACKER_SKIPPED = new AtomicLong();
    private static final AtomicLong FAULTS = new AtomicLong();

    private static final ThreadLocal<List<PendingTick>> PENDING_TICKS =
            ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<List<PendingRandom>> PENDING_RANDOM =
            ThreadLocal.withInitial(ArrayList::new);

    private static volatile Method tickBlock;
    private static volatile Method tickFluid;
    private static volatile Method tickChunk;

    private InteriorWorldTickBridge() {
    }

    public static long blockCount() {
        return BLOCKS.get();
    }

    public static long fluidCount() {
        return FLUIDS.get();
    }

    public static long randomCount() {
        return RANDOMS.get();
    }

    public static long blockEntityCount() {
        return BLOCK_ENTITIES.get();
    }

    public static long blockEventCount() {
        return BLOCK_EVENTS.get();
    }

    public static long borderEntityCount() {
        return BORDER_ENTITIES.get();
    }

    public static long borderBlockEntityCount() {
        return BORDER_BLOCK_ENTITIES.get();
    }

    public static long borderBlockEventCount() {
        return BORDER_BLOCK_EVENTS.get();
    }

    public static long trackerSendCount() {
        return TRACKER_SENDS.get();
    }

    public static long borderTrackerSendCount() {
        return BORDER_TRACKER_SENDS.get();
    }

    /** Phase 3.9 — clean sendChanges skipped (not queued, not run). */
    public static long trackerSkipCount() {
        return TRACKER_SKIPPED.get();
    }

    /** Hot-path flag for ChunkMap (avoids synchronized {@code Boolean.getBoolean}). */
    public static boolean spatialTrackerEnabled() {
        return YapPhase3Flags.spatialTracker();
    }

    public static long faultCount() {
        return FAULTS.get();
    }

    /** Same border rule as {@link YapSpatialTickCoordinator#isBorderChunk}. */
    public static boolean isInteriorChunk(int chunkX, int chunkZ) {
        return !YapSpatialTickCoordinator.isBorderChunk(chunkX, chunkZ);
    }

    public static boolean isInteriorBlock(int blockX, int blockZ) {
        return isInteriorChunk(blockX >> 4, blockZ >> 4);
    }

    public static void offerBlock(Object serverLevel, Object blockPos, Object blockType) {
        PENDING_TICKS.get().add(new PendingTick(serverLevel, blockPos, blockType, false));
    }

    public static void offerFluid(Object serverLevel, Object blockPos, Object fluidType) {
        PENDING_TICKS.get().add(new PendingTick(serverLevel, blockPos, fluidType, true));
    }

    public static void offerRandom(Object serverLevel, Object levelChunk, int tickSpeed) {
        PENDING_RANDOM.get().add(new PendingRandom(serverLevel, levelChunk, tickSpeed));
    }

    private static final ThreadLocal<List<Object>> PENDING_ENTITIES =
            ThreadLocal.withInitial(ArrayList::new);
    private static final AtomicLong ENTITIES = new AtomicLong();
    private static volatile Method entityTick;
    private static volatile Method entityChunkPos;
    private static volatile Method chunkPosX;
    private static volatile Method chunkPosZ;
    private static volatile Method checkIfActive;
    private static volatile Method inactiveTickMethod;
    private static volatile boolean activationResolved;
    private static final AtomicLong ACTIVATION_SKIPS = new AtomicLong();

    public static long entityCount() {
        return ENTITIES.get();
    }

    public static long activationSkipCount() {
        return ACTIVATION_SKIPS.get();
    }

    public static void offerEntity(Object nmsEntity) {
        if (nmsEntity != null) {
            PENDING_ENTITIES.get().add(nmsEntity);
        }
    }

    /**
     * Flush deferred interior entities onto spatial cores (call on Paper main after entity forEach).
     * When block-entity spatial tick is on, defer so {@link #flushBlockEntities()} can coalesce
     * entity + BE into one barrier. Caller must still invoke {@link #flushBlockEntities()} (or
     * force a drain) that tick — Paper hooks do this after the BE loop / when BE ticking is off.
     */
    public static void flushEntities() {
        if (YapPhase3Flags.spatialBlockEntities()) {
            return;
        }
        flushEntitiesNow();
    }

    /** Force entity drain even when BE coalesce is enabled (BE ticking disabled path). */
    public static void flushEntitiesForced() {
        flushEntitiesNow();
    }

    private static void flushEntitiesNow() {
        YapDistantBrain.bumpTick();
        List<Object> batch = PENDING_ENTITIES.get();
        if (batch.isEmpty()) {
            return;
        }
        List<Object> copy = new ArrayList<>(batch);
        batch.clear();
        fanOutEntities(copy);
    }

    private static void fanOutEntities(List<Object> copy) {
        YapSpatialTickCoordinator coord = PaperTickBridgeHolder.COORDINATOR;
        if (coord == null || !coord.isOnline()) {
            for (Object e : copy) {
                tickNmsEntity(e);
            }
            return;
        }
        EnumMap<SpatialQuadrant, List<Object>> byQ = new EnumMap<>(SpatialQuadrant.class);
        for (Object e : copy) {
            SpatialQuadrant q = quadrantOfEntity(e);
            byQ.computeIfAbsent(q, k -> new ArrayList<>()).add(e);
        }
        Map<SpatialQuadrant, Runnable> work = new EnumMap<>(SpatialQuadrant.class);
        for (var entry : byQ.entrySet()) {
            List<Object> list = entry.getValue();
            SpatialQuadrant q = entry.getKey();
            work.put(q, () -> coord.runOwned(() -> {
                for (Object e : list) {
                    tickNmsEntity(e);
                }
            }));
        }
        coord.runParallelTick(work);
    }

    private static void tickNmsEntity(Object nmsEntity) {
        try {
            if (YapPhase3Flags.spatialEntityActivation() && !isEntityActive(nmsEntity)) {
                inactiveTickEntity(nmsEntity);
                ACTIVATION_SKIPS.incrementAndGet();
                return;
            }
            if (YapDistantBrain.shouldThrottleFullTick(nmsEntity)) {
                inactiveTickEntity(nmsEntity);
                return;
            }
            Method tick = resolveEntityTick(nmsEntity.getClass());
            if (tick == null) {
                return;
            }
            tick.invoke(nmsEntity);
            ENTITIES.incrementAndGet();
        } catch (Throwable t) {
            long n = FAULTS.incrementAndGet();
            if (n <= 8 || (n % 500) == 0) {
                LOG.log(Level.WARNING, "Interior entity tick fault #" + n, t);
            }
        }
    }

    private static boolean isEntityActive(Object nmsEntity) {
        resolveActivation(nmsEntity);
        if (checkIfActive == null) {
            return true;
        }
        try {
            return Boolean.TRUE.equals(checkIfActive.invoke(null, nmsEntity));
        } catch (ReflectiveOperationException e) {
            return true;
        }
    }

    private static void inactiveTickEntity(Object nmsEntity) {
        resolveActivation(nmsEntity);
        if (inactiveTickMethod == null) {
            return;
        }
        try {
            inactiveTickMethod.invoke(nmsEntity);
        } catch (ReflectiveOperationException ignored) {
            // best-effort
        }
    }

    private static void resolveActivation(Object nmsEntity) {
        if (activationResolved) {
            return;
        }
        synchronized (InteriorWorldTickBridge.class) {
            if (activationResolved) {
                return;
            }
            ClassLoader cl = nmsEntity.getClass().getClassLoader();
            try {
                Class<?> ar = Class.forName("io.papermc.paper.entity.activation.ActivationRange", true, cl);
                Class<?> entityCl = Class.forName("net.minecraft.world.entity.Entity", true, cl);
                checkIfActive = ar.getMethod("checkIfActive", entityCl);
            } catch (ReflectiveOperationException e) {
                checkIfActive = null;
            }
            try {
                Class<?> entityCl = Class.forName("net.minecraft.world.entity.Entity", true, cl);
                inactiveTickMethod = entityCl.getMethod("inactiveTick");
                inactiveTickMethod.setAccessible(true);
            } catch (ReflectiveOperationException e) {
                inactiveTickMethod = null;
            }
            activationResolved = true;
        }
    }

    private static Method resolveEntityTick(Class<?> nmsClass) {
        Method cached = entityTick;
        if (cached != null && cached.getDeclaringClass().isAssignableFrom(nmsClass)) {
            return cached;
        }
        Class<?> c = nmsClass;
        while (c != null) {
            try {
                Method m = c.getDeclaredMethod("tick");
                m.setAccessible(true);
                entityTick = m;
                return m;
            } catch (NoSuchMethodException e) {
                c = c.getSuperclass();
            }
        }
        return null;
    }

    private static SpatialQuadrant quadrantOfEntity(Object nmsEntity) {
        try {
            if (entityChunkPos == null) {
                Method m = nmsEntity.getClass().getMethod("chunkPosition");
                entityChunkPos = m;
            }
            Object pos = entityChunkPos.invoke(nmsEntity);
            if (chunkPosX == null) {
                chunkPosX = pos.getClass().getMethod("x");
                chunkPosZ = pos.getClass().getMethod("z");
            }
            int x = ((Number) chunkPosX.invoke(pos)).intValue();
            int z = ((Number) chunkPosZ.invoke(pos)).intValue();
            return SpatialQuadrant.byId(quadrantId(x, z));
        } catch (ReflectiveOperationException e) {
            return SpatialQuadrant.SE;
        }
    }

    private static final int PARALLEL_TICK_THRESHOLD = 48;
    private static final int PARALLEL_RANDOM_THRESHOLD = 48;

    /** Flush deferred block/fluid ticks onto spatial cores (call on Paper main). */
    public static void flushBlockFluid() {
        List<PendingTick> batch = PENDING_TICKS.get();
        if (batch.isEmpty()) {
            return;
        }
        // Defer barrier until flushRandom when possible — one wakeup for world ticks.
        if (YapPhase3Flags.spatialRandom()) {
            return;
        }
        flushBlockFluidNow();
    }

    private static void flushBlockFluidNow() {
        List<PendingTick> batch = PENDING_TICKS.get();
        if (batch.isEmpty()) {
            return;
        }
        List<PendingTick> copy = new ArrayList<>(batch);
        batch.clear();
        if (copy.size() < PARALLEL_TICK_THRESHOLD) {
            for (PendingTick p : copy) {
                applyTick(p);
            }
            return;
        }
        fanOutTicks(copy);
    }

    /** Flush deferred random chunk ticks onto spatial cores (call on Paper main). */
    public static void flushRandom() {
        // Coalesce: drain pending block/fluid in the same barrier as random.
        boolean hasTicks = !PENDING_TICKS.get().isEmpty();
        boolean hasRandom = !PENDING_RANDOM.get().isEmpty();
        if (!hasTicks && !hasRandom) {
            return;
        }
        if (hasTicks && hasRandom) {
            flushWorldCombined();
            return;
        }
        if (hasTicks) {
            flushBlockFluidNow();
        }
        if (!hasRandom) {
            return;
        }
        List<PendingRandom> batch = PENDING_RANDOM.get();
        List<PendingRandom> copy = new ArrayList<>(batch);
        batch.clear();
        if (copy.size() < PARALLEL_RANDOM_THRESHOLD) {
            YapPhase3Flags.setFlushing(true);
            try {
                for (PendingRandom p : copy) {
                    applyRandom(p);
                }
            } finally {
                YapPhase3Flags.setFlushing(false);
            }
            return;
        }
        fanOutRandom(copy);
    }

    /** One barrier for scheduled block/fluid + random (high-pop path). */
    private static void flushWorldCombined() {
        List<PendingTick> ticks = new ArrayList<>(PENDING_TICKS.get());
        PENDING_TICKS.get().clear();
        List<PendingRandom> randoms = new ArrayList<>(PENDING_RANDOM.get());
        PENDING_RANDOM.get().clear();
        YapSpatialTickCoordinator coord = PaperTickBridgeHolder.COORDINATOR;
        if (coord == null || !coord.isOnline()
                || (ticks.size() + randoms.size()) < PARALLEL_TICK_THRESHOLD) {
            for (PendingTick p : ticks) {
                applyTick(p);
            }
            YapPhase3Flags.setFlushing(true);
            try {
                for (PendingRandom p : randoms) {
                    applyRandom(p);
                }
            } finally {
                YapPhase3Flags.setFlushing(false);
            }
            return;
        }
        EnumMap<SpatialQuadrant, List<PendingTick>> ticksByQ = new EnumMap<>(SpatialQuadrant.class);
        EnumMap<SpatialQuadrant, List<PendingRandom>> rndByQ = new EnumMap<>(SpatialQuadrant.class);
        for (PendingTick p : ticks) {
            ticksByQ.computeIfAbsent(quadrantOfPos(p.pos), k -> new ArrayList<>()).add(p);
        }
        for (PendingRandom p : randoms) {
            rndByQ.computeIfAbsent(quadrantOfChunk(p.chunk), k -> new ArrayList<>()).add(p);
        }
        Map<SpatialQuadrant, Runnable> work = new EnumMap<>(SpatialQuadrant.class);
        for (SpatialQuadrant q : SpatialQuadrant.values()) {
            List<PendingTick> tl = ticksByQ.get(q);
            List<PendingRandom> rl = rndByQ.get(q);
            if ((tl == null || tl.isEmpty()) && (rl == null || rl.isEmpty())) {
                continue;
            }
            work.put(q, () -> coord.runOwned(() -> {
                if (tl != null) {
                    for (PendingTick p : tl) {
                        applyTick(p);
                    }
                }
                if (rl != null) {
                    YapPhase3Flags.setFlushing(true);
                    try {
                        for (PendingRandom p : rl) {
                            applyRandom(p);
                        }
                    } finally {
                        YapPhase3Flags.setFlushing(false);
                    }
                }
            }));
        }
        coord.runParallelTick(work);
    }

    private static void fanOutTicks(List<PendingTick> copy) {
        YapSpatialTickCoordinator coord = PaperTickBridgeHolder.COORDINATOR;
        if (coord == null || !coord.isOnline()) {
            for (PendingTick p : copy) {
                applyTick(p);
            }
            return;
        }
        EnumMap<SpatialQuadrant, List<PendingTick>> byQ = new EnumMap<>(SpatialQuadrant.class);
        for (PendingTick p : copy) {
            byQ.computeIfAbsent(quadrantOfPos(p.pos), k -> new ArrayList<>()).add(p);
        }
        Map<SpatialQuadrant, Runnable> work = new EnumMap<>(SpatialQuadrant.class);
        for (var e : byQ.entrySet()) {
            List<PendingTick> list = e.getValue();
            work.put(e.getKey(), () -> coord.runOwned(() -> {
                for (PendingTick p : list) {
                    applyTick(p);
                }
            }));
        }
        coord.runParallelTick(work);
    }

    private static void fanOutRandom(List<PendingRandom> copy) {
        YapSpatialTickCoordinator coord = PaperTickBridgeHolder.COORDINATOR;
        if (coord == null || !coord.isOnline()) {
            for (PendingRandom p : copy) {
                applyRandom(p);
            }
            return;
        }
        EnumMap<SpatialQuadrant, List<PendingRandom>> byQ = new EnumMap<>(SpatialQuadrant.class);
        for (PendingRandom p : copy) {
            byQ.computeIfAbsent(quadrantOfChunk(p.chunk), k -> new ArrayList<>()).add(p);
        }
        Map<SpatialQuadrant, Runnable> work = new EnumMap<>(SpatialQuadrant.class);
        for (var e : byQ.entrySet()) {
            List<PendingRandom> list = e.getValue();
            work.put(e.getKey(), () -> coord.runOwned(() -> {
                for (PendingRandom p : list) {
                    applyRandom(p);
                }
            }));
        }
        coord.runParallelTick(work);
    }

    private static void applyTick(PendingTick p) {
        try {
            Method m = p.fluid ? resolveTickFluid(p.level) : resolveTickBlock(p.level);
            if (m == null) {
                FAULTS.incrementAndGet();
                return;
            }
            m.invoke(p.level, p.pos, p.type);
            if (p.fluid) {
                FLUIDS.incrementAndGet();
            } else {
                BLOCKS.incrementAndGet();
            }
        } catch (Throwable t) {
            FAULTS.incrementAndGet();
            if (FAULTS.get() < 5 || (FAULTS.get() % 200) == 0) {
                LOG.log(Level.FINE, "Interior block/fluid tick fault", t);
            }
        }
    }

    private static void applyRandom(PendingRandom p) {
        try {
            Method m = resolveTickChunk(p.level);
            if (m == null) {
                FAULTS.incrementAndGet();
                return;
            }
            boolean setFlush = !YapPhase3Flags.flushing();
            if (setFlush) {
                YapPhase3Flags.setFlushing(true);
            }
            try {
                m.invoke(p.level, p.chunk, p.tickSpeed);
            } finally {
                if (setFlush) {
                    YapPhase3Flags.setFlushing(false);
                }
            }
            RANDOMS.incrementAndGet();
        } catch (Throwable t) {
            FAULTS.incrementAndGet();
            if (FAULTS.get() < 5 || (FAULTS.get() % 200) == 0) {
                LOG.log(Level.FINE, "Interior random tick fault", t);
            }
        }
    }

    private static Method resolveTickBlock(Object level) {
        Method cached = tickBlock;
        if (cached != null) {
            return cached;
        }
        synchronized (InteriorWorldTickBridge.class) {
            if (tickBlock == null) {
                tickBlock = findPrivate(level.getClass(), "tickBlock", 2);
            }
            return tickBlock;
        }
    }

    private static Method resolveTickFluid(Object level) {
        Method cached = tickFluid;
        if (cached != null) {
            return cached;
        }
        synchronized (InteriorWorldTickBridge.class) {
            if (tickFluid == null) {
                tickFluid = findPrivate(level.getClass(), "tickFluid", 2);
            }
            return tickFluid;
        }
    }

    private static Method resolveTickChunk(Object level) {
        Method cached = tickChunk;
        if (cached != null) {
            return cached;
        }
        synchronized (InteriorWorldTickBridge.class) {
            if (tickChunk == null) {
                try {
                    tickChunk = level.getClass().getMethod("tickChunk",
                            Class.forName("net.minecraft.world.level.chunk.LevelChunk", true,
                                    level.getClass().getClassLoader()),
                            int.class);
                } catch (ReflectiveOperationException e) {
                    tickChunk = findPrivate(level.getClass(), "tickChunk", 2);
                }
            }
            return tickChunk;
        }
    }

    private static Method findPrivate(Class<?> cl, String name, int argc) {
        Class<?> c = cl;
        while (c != null) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == argc) {
                    m.setAccessible(true);
                    return m;
                }
            }
            c = c.getSuperclass();
        }
        return null;
    }

    private static SpatialQuadrant quadrantOfPos(Object blockPos) {
        try {
            int x = ((Number) blockPos.getClass().getMethod("getX").invoke(blockPos)).intValue();
            int z = ((Number) blockPos.getClass().getMethod("getZ").invoke(blockPos)).intValue();
            return SpatialQuadrant.byId(quadrantId(x >> 4, z >> 4));
        } catch (ReflectiveOperationException e) {
            return SpatialQuadrant.SE;
        }
    }

    private static SpatialQuadrant quadrantOfChunk(Object levelChunk) {
        try {
            Object pos = levelChunk.getClass().getMethod("getPos").invoke(levelChunk);
            int x = ((Number) pos.getClass().getMethod("x").invoke(pos)).intValue();
            int z = ((Number) pos.getClass().getMethod("z").invoke(pos)).intValue();
            return SpatialQuadrant.byId(quadrantId(x, z));
        } catch (ReflectiveOperationException e) {
            try {
                Object pos = levelChunk.getClass().getMethod("getPos").invoke(levelChunk);
                int x = pos.getClass().getField("x").getInt(pos);
                int z = pos.getClass().getField("z").getInt(pos);
                return SpatialQuadrant.byId(quadrantId(x, z));
            } catch (ReflectiveOperationException e2) {
                return SpatialQuadrant.SE;
            }
        }
    }

    private static String worldTag(Object level) {
        try {
            Object dim = level.getClass().getMethod("dimension").invoke(level);
            return String.valueOf(dim);
        } catch (ReflectiveOperationException e) {
            return "lvl" + System.identityHashCode(level);
        }
    }

    private static String leaseKey(Object level, Object blockPos) {
        try {
            int x = ((Number) blockPos.getClass().getMethod("getX").invoke(blockPos)).intValue();
            int z = ((Number) blockPos.getClass().getMethod("getZ").invoke(blockPos)).intValue();
            return YapSpatialTickCoordinator.chunkKey(worldTag(level), x >> 4, z >> 4);
        } catch (ReflectiveOperationException e) {
            return "c:unknown:0:0";
        }
    }

    private static String leaseKeyChunk(Object level, Object chunk) {
        try {
            Object pos = chunk.getClass().getMethod("getPos").invoke(chunk);
            int x;
            int z;
            try {
                x = ((Number) pos.getClass().getMethod("x").invoke(pos)).intValue();
                z = ((Number) pos.getClass().getMethod("z").invoke(pos)).intValue();
            } catch (NoSuchMethodException e) {
                x = pos.getClass().getField("x").getInt(pos);
                z = pos.getClass().getField("z").getInt(pos);
            }
            return YapSpatialTickCoordinator.chunkKey(worldTag(level), x, z);
        } catch (ReflectiveOperationException e) {
            return "c:unknown:0:0";
        }
    }

    private static int quadrantId(int chunkX, int chunkZ) {
        int east = chunkX >= 0 ? 1 : 0;
        int south = chunkZ >= 0 ? 1 : 0;
        return east | (south << 1);
    }

    private record PendingTick(Object level, Object pos, Object type, boolean fluid) {
    }

    private record PendingRandom(Object level, Object chunk, int tickSpeed) {
    }

    // --- Phase 3.6: block entities + redstone block events on quads ---

    private static final ThreadLocal<List<Object>> PENDING_BLOCK_ENTITIES =
            ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<List<PendingBlockEvent>> PENDING_BLOCK_EVENTS =
            ThreadLocal.withInitial(ArrayList::new);
    private static volatile Method tickingBeTick;
    private static volatile Method tickingBePos;
    private static volatile Method doBlockEvent;
    private static volatile Method blockEventPos;

    public static void offerBlockEntity(Object tickingBlockEntity) {
        if (tickingBlockEntity != null) {
            PENDING_BLOCK_ENTITIES.get().add(tickingBlockEntity);
        }
    }

    public static void offerBlockEvent(Object serverLevel, Object blockEventData) {
        if (serverLevel != null && blockEventData != null) {
            PENDING_BLOCK_EVENTS.get().add(new PendingBlockEvent(serverLevel, blockEventData));
        }
    }

    /**
     * Flush deferred interior redstone/piston block events onto spatial cores.
     * Phase 3.10: when coalesce is on, also drains deferred entities + BE in this barrier.
     */
    public static void flushBlockEvents() {
        if (YapPhase3Flags.spatialCoalesceBarriers() && YapPhase3Flags.spatialRedstone()) {
            if (!PENDING_BLOCK_ENTITIES.get().isEmpty()
                    || !PENDING_ENTITIES.get().isEmpty()
                    || !PENDING_BLOCK_EVENTS.get().isEmpty()) {
                flushEntitiesBeAndEventsCombined();
            }
            return;
        }
        List<PendingBlockEvent> batch = PENDING_BLOCK_EVENTS.get();
        if (batch.isEmpty()) {
            return;
        }
        List<PendingBlockEvent> copy = new ArrayList<>(batch);
        batch.clear();
        fanOutBlockEvents(copy);
    }

    /** Flush deferred interior block-entity tickers onto spatial cores (Paper main). */
    public static void flushBlockEntities() {
        // Defer until flushBlockEvents so redstone events share one runParallelTick
        if (YapPhase3Flags.spatialCoalesceBarriers() && YapPhase3Flags.spatialRedstone()) {
            return;
        }
        boolean hasBe = !PENDING_BLOCK_ENTITIES.get().isEmpty();
        boolean hasEnt = !PENDING_ENTITIES.get().isEmpty();
        if (!hasBe && !hasEnt) {
            return;
        }
        if (hasBe && hasEnt) {
            flushEntitiesBeAndEventsCombined();
            return;
        }
        if (hasEnt) {
            flushEntitiesNow();
        }
        if (!hasBe) {
            return;
        }
        List<Object> batch = PENDING_BLOCK_ENTITIES.get();
        List<Object> copy = new ArrayList<>(batch);
        batch.clear();
        fanOutBlockEntities(copy);
    }

    private static void flushEntitiesBeAndEventsCombined() {
        List<Object> ents = new ArrayList<>(PENDING_ENTITIES.get());
        PENDING_ENTITIES.get().clear();
        List<Object> bes = new ArrayList<>(PENDING_BLOCK_ENTITIES.get());
        PENDING_BLOCK_ENTITIES.get().clear();
        List<PendingBlockEvent> evs = new ArrayList<>(PENDING_BLOCK_EVENTS.get());
        PENDING_BLOCK_EVENTS.get().clear();
        YapDistantBrain.bumpTick();
        YapSpatialTickCoordinator coord = PaperTickBridgeHolder.COORDINATOR;
        if (ents.isEmpty() && bes.isEmpty() && evs.isEmpty()) {
            return;
        }
        if (coord == null || !coord.isOnline()) {
            for (Object e : ents) {
                tickNmsEntity(e);
            }
            for (Object t : bes) {
                tickBlockEntity(t);
            }
            for (PendingBlockEvent e : evs) {
                applyBlockEvent(e);
            }
            return;
        }
        EnumMap<SpatialQuadrant, List<Object>> entByQ = new EnumMap<>(SpatialQuadrant.class);
        EnumMap<SpatialQuadrant, List<Object>> beByQ = new EnumMap<>(SpatialQuadrant.class);
        EnumMap<SpatialQuadrant, List<PendingBlockEvent>> evByQ = new EnumMap<>(SpatialQuadrant.class);
        for (Object e : ents) {
            entByQ.computeIfAbsent(quadrantOfEntity(e), k -> new ArrayList<>()).add(e);
        }
        for (Object t : bes) {
            beByQ.computeIfAbsent(quadrantOfBlockEntity(t), k -> new ArrayList<>()).add(t);
        }
        for (PendingBlockEvent e : evs) {
            evByQ.computeIfAbsent(quadrantOfBlockEvent(e), k -> new ArrayList<>()).add(e);
        }
        Map<SpatialQuadrant, Runnable> work = new EnumMap<>(SpatialQuadrant.class);
        for (SpatialQuadrant q : SpatialQuadrant.values()) {
            List<Object> el = entByQ.get(q);
            List<Object> bl = beByQ.get(q);
            List<PendingBlockEvent> vl = evByQ.get(q);
            if ((el == null || el.isEmpty()) && (bl == null || bl.isEmpty())
                    && (vl == null || vl.isEmpty())) {
                continue;
            }
            work.put(q, () -> coord.runOwned(() -> {
                if (el != null) {
                    for (Object e : el) {
                        tickNmsEntity(e);
                    }
                }
                if (bl != null) {
                    for (Object t : bl) {
                        tickBlockEntity(t);
                    }
                }
                if (vl != null) {
                    for (PendingBlockEvent e : vl) {
                        applyBlockEvent(e);
                    }
                }
            }));
        }
        coord.runParallelTick(work);
    }

    private static SpatialQuadrant quadrantOfBlockEvent(PendingBlockEvent e) {
        return quadrantOfBlockEvent(e.eventData());
    }

    private static void flushEntitiesAndBlockEntitiesCombined() {
        flushEntitiesBeAndEventsCombined();
    }

    private static void fanOutBlockEntities(List<Object> copy) {
        YapSpatialTickCoordinator coord = PaperTickBridgeHolder.COORDINATOR;
        if (coord == null || !coord.isOnline()) {
            for (Object t : copy) {
                tickBlockEntity(t);
            }
            return;
        }
        EnumMap<SpatialQuadrant, List<Object>> byQ = new EnumMap<>(SpatialQuadrant.class);
        for (Object t : copy) {
            byQ.computeIfAbsent(quadrantOfBlockEntity(t), k -> new ArrayList<>()).add(t);
        }
        Map<SpatialQuadrant, Runnable> work = new EnumMap<>(SpatialQuadrant.class);
        for (var entry : byQ.entrySet()) {
            List<Object> list = entry.getValue();
            work.put(entry.getKey(), () -> coord.runOwned(() -> {
                for (Object t : list) {
                    tickBlockEntity(t);
                }
            }));
        }
        coord.runParallelTick(work);
    }

    private static void fanOutBlockEvents(List<PendingBlockEvent> copy) {
        YapSpatialTickCoordinator coord = PaperTickBridgeHolder.COORDINATOR;
        if (coord == null || !coord.isOnline()) {
            for (PendingBlockEvent p : copy) {
                applyBlockEvent(p);
            }
            return;
        }
        EnumMap<SpatialQuadrant, List<PendingBlockEvent>> byQ = new EnumMap<>(SpatialQuadrant.class);
        for (PendingBlockEvent p : copy) {
            byQ.computeIfAbsent(quadrantOfBlockEvent(p.eventData), k -> new ArrayList<>()).add(p);
        }
        Map<SpatialQuadrant, Runnable> work = new EnumMap<>(SpatialQuadrant.class);
        for (var entry : byQ.entrySet()) {
            List<PendingBlockEvent> list = entry.getValue();
            work.put(entry.getKey(), () -> coord.runOwned(() -> {
                for (PendingBlockEvent p : list) {
                    applyBlockEvent(p);
                }
            }));
        }
        coord.runParallelTick(work);
    }

    private static void tickBlockEntity(Object ticker) {
        try {
            Method tick = resolveBeTick(ticker.getClass());
            if (tick == null) {
                FAULTS.incrementAndGet();
                return;
            }
            tick.invoke(ticker);
            BLOCK_ENTITIES.incrementAndGet();
        } catch (Throwable t) {
            FAULTS.incrementAndGet();
            if (FAULTS.get() < 5 || (FAULTS.get() % 200) == 0) {
                LOG.log(Level.FINE, "Interior block-entity tick fault", t);
            }
        }
    }

    private static void applyBlockEvent(PendingBlockEvent p) {
        try {
            Method m = resolveDoBlockEvent(p.level.getClass());
            if (m == null) {
                FAULTS.incrementAndGet();
                return;
            }
            Object ok = m.invoke(p.level, p.eventData);
            // Broadcast is best-effort — ServerLevel helper may also broadcast when returning true
            if (Boolean.TRUE.equals(ok)) {
                tryBroadcastBlockEvent(p.level, p.eventData);
            }
            BLOCK_EVENTS.incrementAndGet();
        } catch (Throwable t) {
            FAULTS.incrementAndGet();
            if (FAULTS.get() < 5 || (FAULTS.get() % 200) == 0) {
                LOG.log(Level.FINE, "Interior block-event fault", t);
            }
        }
    }

    private static void tryBroadcastBlockEvent(Object level, Object eventData) {
        try {
            Method broadcaster = level.getClass().getDeclaredMethod("yapBroadcastBlockEvent", Object.class);
            broadcaster.setAccessible(true);
            broadcaster.invoke(level, eventData);
        } catch (ReflectiveOperationException ignored) {
            // Optional — doBlockEvent side effects still applied
        }
    }

    private static Method resolveBeTick(Class<?> cl) {
        Method cached = tickingBeTick;
        if (cached != null && cached.getDeclaringClass().isAssignableFrom(cl)) {
            return cached;
        }
        Class<?> c = cl;
        while (c != null) {
            try {
                Method m = c.getMethod("tick");
                m.setAccessible(true);
                tickingBeTick = m;
                return m;
            } catch (NoSuchMethodException e) {
                c = c.getSuperclass();
            }
        }
        return findPrivate(cl, "tick", 0);
    }

    private static Method resolveDoBlockEvent(Class<?> levelCl) {
        Method cached = doBlockEvent;
        if (cached != null) {
            return cached;
        }
        synchronized (InteriorWorldTickBridge.class) {
            if (doBlockEvent == null) {
                doBlockEvent = findPrivate(levelCl, "doBlockEvent", 1);
            }
            return doBlockEvent;
        }
    }

    private static SpatialQuadrant quadrantOfBlockEntity(Object ticker) {
        try {
            if (tickingBePos == null) {
                Method m = ticker.getClass().getMethod("getPos");
                tickingBePos = m;
            }
            Object pos = tickingBePos.invoke(ticker);
            int x = ((Number) pos.getClass().getMethod("getX").invoke(pos)).intValue();
            int z = ((Number) pos.getClass().getMethod("getZ").invoke(pos)).intValue();
            return SpatialQuadrant.byId(quadrantId(x >> 4, z >> 4));
        } catch (ReflectiveOperationException e) {
            return SpatialQuadrant.SE;
        }
    }

    private static SpatialQuadrant quadrantOfBlockEvent(Object eventData) {
        try {
            if (blockEventPos == null) {
                try {
                    blockEventPos = eventData.getClass().getMethod("pos");
                } catch (NoSuchMethodException e) {
                    blockEventPos = eventData.getClass().getMethod("getPos");
                }
            }
            Object pos = blockEventPos.invoke(eventData);
            int x = ((Number) pos.getClass().getMethod("getX").invoke(pos)).intValue();
            int z = ((Number) pos.getClass().getMethod("getZ").invoke(pos)).intValue();
            return SpatialQuadrant.byId(quadrantId(x >> 4, z >> 4));
        } catch (ReflectiveOperationException e) {
            return SpatialQuadrant.SE;
        }
    }

    private record PendingBlockEvent(Object level, Object eventData) {
    }

    // --- Phase 3.7: border chunk work on T8 under DLM leases ---

    private static final ThreadLocal<List<Object>> PENDING_BORDER_ENTITIES =
            ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<List<Object>> PENDING_BORDER_BLOCK_ENTITIES =
            ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<List<PendingBlockEvent>> PENDING_BORDER_BLOCK_EVENTS =
            ThreadLocal.withInitial(ArrayList::new);

    public static void offerBorderEntity(Object nmsEntity) {
        if (nmsEntity != null) {
            PENDING_BORDER_ENTITIES.get().add(nmsEntity);
        }
    }

    /** Flush deferred border entities onto T8 under a DLM lease (Paper main waits). */
    public static void flushBorderEntities() {
        List<Object> batch = PENDING_BORDER_ENTITIES.get();
        if (batch.isEmpty()) {
            return;
        }
        List<Object> copy = new ArrayList<>(batch);
        batch.clear();
        YapSpatialTickCoordinator coord = PaperTickBridgeHolder.COORDINATOR;
        if (coord == null || !coord.isOnline() || !YapPhase3Flags.spatialBorders()) {
            for (Object e : copy) {
                tickNmsEntity(e);
            }
            BORDER_ENTITIES.addAndGet(copy.size());
            return;
        }
        coord.runBorderTickSync("border:entities", () -> {
            for (Object e : copy) {
                tickNmsEntity(e);
            }
            BORDER_ENTITIES.addAndGet(copy.size());
        });
    }

    public static void offerBorderBlockEntity(Object tickingBlockEntity) {
        if (tickingBlockEntity != null) {
            PENDING_BORDER_BLOCK_ENTITIES.get().add(tickingBlockEntity);
        }
    }

    public static void flushBorderBlockEntities() {
        List<Object> batch = PENDING_BORDER_BLOCK_ENTITIES.get();
        if (batch.isEmpty()) {
            return;
        }
        List<Object> copy = new ArrayList<>(batch);
        batch.clear();
        YapSpatialTickCoordinator coord = PaperTickBridgeHolder.COORDINATOR;
        if (coord == null || !coord.isOnline() || !YapPhase3Flags.spatialBorders()) {
            for (Object t : copy) {
                tickBlockEntity(t);
            }
            BORDER_BLOCK_ENTITIES.addAndGet(copy.size());
            return;
        }
        coord.runBorderTickSync("border:blockentities", () -> {
            for (Object t : copy) {
                tickBlockEntity(t);
            }
            BORDER_BLOCK_ENTITIES.addAndGet(copy.size());
        });
    }

    public static void offerBorderBlockEvent(Object serverLevel, Object blockEventData) {
        if (serverLevel != null && blockEventData != null) {
            PENDING_BORDER_BLOCK_EVENTS.get().add(new PendingBlockEvent(serverLevel, blockEventData));
        }
    }

    public static void flushBorderBlockEvents() {
        List<PendingBlockEvent> batch = PENDING_BORDER_BLOCK_EVENTS.get();
        if (batch.isEmpty()) {
            return;
        }
        List<PendingBlockEvent> copy = new ArrayList<>(batch);
        batch.clear();
        YapSpatialTickCoordinator coord = PaperTickBridgeHolder.COORDINATOR;
        if (coord == null || !coord.isOnline() || !YapPhase3Flags.spatialBorders()) {
            for (PendingBlockEvent p : copy) {
                applyBlockEvent(p);
            }
            BORDER_BLOCK_EVENTS.addAndGet(copy.size());
            return;
        }
        coord.runBorderTickSync("border:blockevents", () -> {
            for (PendingBlockEvent p : copy) {
                applyBlockEvent(p);
            }
            BORDER_BLOCK_EVENTS.addAndGet(copy.size());
        });
    }

    // --- Phase 3.8 / 3.9 / Leaf-gap: non-player tracker sendChanges on spatial cores / T8 ---

    private static final ThreadLocal<List<PendingTrackerSend>> PENDING_TRACKER =
            ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<List<Object>> PENDING_BORDER_TRACKER =
            ThreadLocal.withInitial(ArrayList::new);

    private static volatile MethodHandle sendChangesMh;
    private static volatile boolean sendChangesMhFailed;

    /**
     * Queue non-player {@code ServerEntity.sendChanges} for spatial flush.
     * Prefer {@link #offerTrackerSendChanges(Object, Object, int, int)} from ChunkMap
     * (coords + no per-entity Runnable / chunkPosition reflect).
     */
    public static boolean offerTrackerSendChanges(Object nmsEntity, Runnable sendChanges) {
        return offerTrackerSendChanges(nmsEntity, null, sendChanges);
    }

    /** Legacy 3-arg path: resolves chunk via reflect; prefer the cx/cz overload. */
    public static boolean offerTrackerSendChanges(Object nmsEntity, Object serverEntity, Runnable sendChanges) {
        if (nmsEntity == null || !YapPhase3Flags.spatialTracker()) {
            return false;
        }
        if (serverEntity != null) {
            try {
                int cx;
                int cz;
                if (entityChunkPos == null) {
                    entityChunkPos = nmsEntity.getClass().getMethod("chunkPosition");
                }
                Object pos = entityChunkPos.invoke(nmsEntity);
                if (chunkPosX == null) {
                    chunkPosX = pos.getClass().getMethod("x");
                    chunkPosZ = pos.getClass().getMethod("z");
                }
                cx = ((Number) chunkPosX.invoke(pos)).intValue();
                cz = ((Number) chunkPosZ.invoke(pos)).intValue();
                return offerTrackerSendChanges(nmsEntity, serverEntity, cx, cz);
            } catch (Throwable t) {
                return false;
            }
        }
        if (sendChanges == null) {
            return false;
        }
        try {
            if (entityChunkPos == null) {
                entityChunkPos = nmsEntity.getClass().getMethod("chunkPosition");
            }
            Object pos = entityChunkPos.invoke(nmsEntity);
            if (chunkPosX == null) {
                chunkPosX = pos.getClass().getMethod("x");
                chunkPosZ = pos.getClass().getMethod("z");
            }
            int cx = ((Number) chunkPosX.invoke(pos)).intValue();
            int cz = ((Number) chunkPosZ.invoke(pos)).intValue();
            return queueTrackerSend(cx, cz, serverEntity, sendChanges);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Leaf-gap hot path: ChunkMap passes chunk coords + ServerEntity (no Runnable alloc,
     * no chunkPosition reflect). Clean skips happen in ChunkMap via NMS {@code yapIsCleanTrackerSend}.
     *
     * @return false if the work should run on main (offer fail / borders off)
     */
    public static boolean offerTrackerSendChanges(Object nmsEntity, Object serverEntity, int cx, int cz) {
        if (nmsEntity == null || serverEntity == null || !YapPhase3Flags.spatialTracker()) {
            return false;
        }
        return queueTrackerSend(cx, cz, serverEntity, null);
    }

    /** Count a ChunkMap-side clean skip (NMS early-out mirrored before offer). */
    public static void noteTrackerSkip() {
        TRACKER_SKIPPED.incrementAndGet();
    }

    private static boolean queueTrackerSend(int cx, int cz, Object serverEntity, Runnable sendChanges) {
        try {
            if (YapSpatialTickCoordinator.isBorderChunk(cx, cz)) {
                if (!YapPhase3Flags.spatialBorders()) {
                    return false;
                }
                PENDING_BORDER_TRACKER.get().add(serverEntity != null ? serverEntity : sendChanges);
                return true;
            }
            SpatialQuadrant q = SpatialQuadrant.byId(quadrantId(cx, cz));
            PENDING_TRACKER.get().add(new PendingTrackerSend(q, serverEntity, sendChanges));
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Flush deferred non-player tracker sends onto cores 3–6 / T8 (Paper main waits). */
    public static void flushTrackerSendChanges() {
        YapDistantBrain.bumpTick();
        List<PendingTrackerSend> interior = PENDING_TRACKER.get();
        List<Object> border = PENDING_BORDER_TRACKER.get();
        if (interior.isEmpty() && border.isEmpty()) {
            return;
        }
        List<PendingTrackerSend> intCopy = new ArrayList<>(interior);
        interior.clear();
        List<Object> borderCopy = new ArrayList<>(border);
        border.clear();

        YapSpatialTickCoordinator coord = PaperTickBridgeHolder.COORDINATOR;
        // Tiny batches: run on main — latch wakeup often costs more than a few sendChanges.
        final int tiny = 48;
        boolean interiorSpatial = coord != null && coord.isOnline() && intCopy.size() > tiny;
        boolean borderSpatial = coord != null && coord.isOnline()
                && YapPhase3Flags.spatialBorders() && !borderCopy.isEmpty();

        if (!intCopy.isEmpty() && !interiorSpatial) {
            for (PendingTrackerSend p : intCopy) {
                runTrackerSend(p, false);
            }
            intCopy = List.of();
        }
        if (!borderCopy.isEmpty() && !borderSpatial) {
            for (Object o : borderCopy) {
                runTrackerTarget(o, true);
            }
            borderCopy = List.of();
        }

        if (intCopy.isEmpty() && borderCopy.isEmpty()) {
            return;
        }

        Map<SpatialQuadrant, Runnable> work = null;
        if (!intCopy.isEmpty()) {
            EnumMap<SpatialQuadrant, List<PendingTrackerSend>> byQ = new EnumMap<>(SpatialQuadrant.class);
            for (PendingTrackerSend p : intCopy) {
                byQ.computeIfAbsent(p.quad, k -> new ArrayList<>()).add(p);
            }
            work = new EnumMap<>(SpatialQuadrant.class);
            for (var e : byQ.entrySet()) {
                List<PendingTrackerSend> list = e.getValue();
                work.put(e.getKey(), () -> coord.runOwned(() -> {
                    for (PendingTrackerSend p : list) {
                        runTrackerSend(p, false);
                    }
                }));
            }
        }

        // High-pop: one barrier when both interior + border tracker work exist
        if (work != null && !borderCopy.isEmpty()) {
            List<Object> borderFinal = borderCopy;
            coord.runParallelTickWithBorder(work, "border:tracker", () -> {
                for (Object o : borderFinal) {
                    runTrackerTarget(o, true);
                }
            });
            return;
        }
        if (work != null) {
            coord.runParallelTick(work);
            return;
        }
        if (!borderCopy.isEmpty()) {
            List<Object> borderFinal = borderCopy;
            coord.runBorderTickSync("border:tracker", () -> {
                for (Object o : borderFinal) {
                    runTrackerTarget(o, true);
                }
            });
        }
    }

    private static void runTrackerSend(PendingTrackerSend p, boolean border) {
        if (p.serverEntity != null) {
            runTrackerTarget(p.serverEntity, border);
        } else if (p.send != null) {
            runTrackerTarget(p.send, border);
        }
    }

    private static void runTrackerTarget(Object target, boolean border) {
        try {
            if (target instanceof Runnable r) {
                r.run();
            } else {
                invokeSendChanges(target);
            }
            if (border) {
                BORDER_TRACKER_SENDS.incrementAndGet();
            } else {
                TRACKER_SENDS.incrementAndGet();
            }
        } catch (Throwable t) {
            long n = FAULTS.incrementAndGet();
            if (n <= 8 || (n % 500) == 0) {
                LOG.log(Level.WARNING, "Tracker sendChanges fault #" + n, t);
            }
        }
    }

    private static void invokeSendChanges(Object serverEntity) throws Throwable {
        MethodHandle mh = sendChangesMh;
        if (mh == null && !sendChangesMhFailed) {
            synchronized (InteriorWorldTickBridge.class) {
                if (sendChangesMh == null && !sendChangesMhFailed) {
                    try {
                        sendChangesMh = MethodHandles.publicLookup()
                                .findVirtual(serverEntity.getClass(), "sendChanges",
                                        java.lang.invoke.MethodType.methodType(void.class));
                    } catch (Throwable t) {
                        sendChangesMhFailed = true;
                        throw t;
                    }
                }
                mh = sendChangesMh;
            }
        }
        if (mh == null) {
            serverEntity.getClass().getMethod("sendChanges").invoke(serverEntity);
            return;
        }
        mh.invoke(serverEntity);
    }

    private record PendingTrackerSend(SpatialQuadrant quad, Object serverEntity, Runnable send) {
    }
}
