package com.yapcore.paper.phase3.nms;

import com.yapcore.paper.phase3.PaperTickBridgeHolder;
import com.yapcore.paper.phase3.YapPhase3Flags;
import com.yapcore.paper.phase3.YapSpatialTickCoordinator;
import com.yaplabs.yapengine.core.spatial.SpatialQuadrant;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Phase 3.5 / 3.6 — interior world work deferred from Paper main onto YapEngine
 * cores 3–6 under DLM leases: scheduled block/fluid/random, block entities,
 * and redstone block events.
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

    public static long entityCount() {
        return ENTITIES.get();
    }

    public static void offerEntity(Object nmsEntity) {
        if (nmsEntity != null) {
            PENDING_ENTITIES.get().add(nmsEntity);
        }
    }

    /** Flush deferred interior entities onto spatial cores (call on Paper main after entity forEach). */
    public static void flushEntities() {
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
            work.put(q, () -> {
                String key = "q:nms:" + q.name();
                coord.runLeased(key, () -> {
                    for (Object e : list) {
                        tickNmsEntity(e);
                    }
                });
            });
        }
        coord.runParallelTick(work);
    }

    private static void tickNmsEntity(Object nmsEntity) {
        try {
            Method tick = resolveEntityTick(nmsEntity.getClass());
            if (tick == null) {
                return;
            }
            tick.invoke(nmsEntity);
            ENTITIES.incrementAndGet();
        } catch (Throwable t) {
            FAULTS.incrementAndGet();
            if (FAULTS.get() < 5 || (FAULTS.get() % 200) == 0) {
                LOG.log(Level.FINE, "Interior entity tick fault", t);
            }
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

    private static final int PARALLEL_TICK_THRESHOLD = 32;
    private static final int PARALLEL_RANDOM_THRESHOLD = 32;

    /** Flush deferred block/fluid ticks onto spatial cores (call on Paper main). */
    public static void flushBlockFluid() {
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
        List<PendingRandom> batch = PENDING_RANDOM.get();
        if (batch.isEmpty()) {
            return;
        }
        List<PendingRandom> copy = new ArrayList<>(batch);
        batch.clear();
        // Small batches: avoid 4-way spatial wakeup overhead on Paper main
        if (copy.size() < PARALLEL_RANDOM_THRESHOLD) {
            System.setProperty("yapcore.phase3.spatial-tick.flushing", "true");
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

    private static void fanOutTicks(List<PendingTick> copy) {
        YapSpatialTickCoordinator coord = PaperTickBridgeHolder.COORDINATOR;
        if (coord == null || !coord.isOnline()) {
            // Fallback: run on main if Phase 3 host not ready
            for (PendingTick p : copy) {
                applyTick(p);
            }
            return;
        }
        EnumMap<SpatialQuadrant, List<PendingTick>> byQ = new EnumMap<>(SpatialQuadrant.class);
        for (PendingTick p : copy) {
            SpatialQuadrant q = quadrantOfPos(p.pos);
            byQ.computeIfAbsent(q, k -> new ArrayList<>()).add(p);
        }
        Map<SpatialQuadrant, Runnable> work = new EnumMap<>(SpatialQuadrant.class);
        for (var e : byQ.entrySet()) {
            List<PendingTick> list = e.getValue();
            work.put(e.getKey(), () -> {
                for (PendingTick p : list) {
                    String key = leaseKey(p.level, p.pos);
                    coord.runLeased(key, () -> applyTick(p));
                }
            });
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
            SpatialQuadrant q = quadrantOfChunk(p.chunk);
            byQ.computeIfAbsent(q, k -> new ArrayList<>()).add(p);
        }
        Map<SpatialQuadrant, Runnable> work = new EnumMap<>(SpatialQuadrant.class);
        for (var e : byQ.entrySet()) {
            List<PendingRandom> list = e.getValue();
            work.put(e.getKey(), () -> {
                for (PendingRandom p : list) {
                    String key = leaseKeyChunk(p.level, p.chunk);
                    coord.runLeased(key, () -> applyRandom(p));
                }
            });
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

    /** Flush deferred interior block-entity tickers onto spatial cores (Paper main). */
    public static void flushBlockEntities() {
        List<Object> batch = PENDING_BLOCK_ENTITIES.get();
        if (batch.isEmpty()) {
            return;
        }
        List<Object> copy = new ArrayList<>(batch);
        batch.clear();
        fanOutBlockEntities(copy);
    }

    public static void offerBlockEvent(Object serverLevel, Object blockEventData) {
        if (serverLevel != null && blockEventData != null) {
            PENDING_BLOCK_EVENTS.get().add(new PendingBlockEvent(serverLevel, blockEventData));
        }
    }

    /**
     * Flush deferred interior redstone/piston block events onto spatial cores.
     * Caller must have already handled border events on Paper main.
     */
    public static void flushBlockEvents() {
        List<PendingBlockEvent> batch = PENDING_BLOCK_EVENTS.get();
        if (batch.isEmpty()) {
            return;
        }
        List<PendingBlockEvent> copy = new ArrayList<>(batch);
        batch.clear();
        fanOutBlockEvents(copy);
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
            SpatialQuadrant q = quadrantOfBlockEntity(t);
            byQ.computeIfAbsent(q, k -> new ArrayList<>()).add(t);
        }
        Map<SpatialQuadrant, Runnable> work = new EnumMap<>(SpatialQuadrant.class);
        for (var entry : byQ.entrySet()) {
            List<Object> list = entry.getValue();
            SpatialQuadrant q = entry.getKey();
            work.put(q, () -> {
                // One lease per quadrant — hoppers/furnaces batch without per-TE acquire churn
                String key = "q:be:" + q.name();
                coord.runLeased(key, () -> {
                    for (Object t : list) {
                        tickBlockEntity(t);
                    }
                });
            });
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
            SpatialQuadrant q = quadrantOfBlockEvent(p.eventData);
            byQ.computeIfAbsent(q, k -> new ArrayList<>()).add(p);
        }
        Map<SpatialQuadrant, Runnable> work = new EnumMap<>(SpatialQuadrant.class);
        for (var entry : byQ.entrySet()) {
            List<PendingBlockEvent> list = entry.getValue();
            SpatialQuadrant q = entry.getKey();
            work.put(q, () -> {
                String key = "q:bevt:" + q.name();
                coord.runLeased(key, () -> {
                    for (PendingBlockEvent p : list) {
                        applyBlockEvent(p);
                    }
                });
            });
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
}
