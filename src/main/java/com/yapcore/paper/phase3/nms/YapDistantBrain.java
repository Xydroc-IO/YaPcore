package com.yapcore.paper.phase3.nms;

import com.yapcore.paper.phase3.YapPhase3Flags;

import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * First-party Leaf/Pufferfish-class distant entity brain throttle (YaP code — not Leaf source).
 * <p>
 * Players never use this path (entity offer / ChunkMap skip them). When no players
 * are online, distance is treated as infinite so AI/path still throttle (bench + empty worlds).
 */
public final class YapDistantBrain {

    private static final Logger LOG = Logger.getLogger("YaPcore.Phase3.Brain");
    private static final AtomicLong PATH_SKIPS = new AtomicLong();
    private static final AtomicLong GOAL_SKIPS = new AtomicLong();
    private static final AtomicLong BRAIN_SKIPS = new AtomicLong();
    private static final AtomicLong FULL_THROTTLES = new AtomicLong();
    private static final AtomicInteger TICK = new AtomicInteger();

    /** Per-tick nearest-player distSq cache (cleared on {@link #bumpTick}). */
    private static final ThreadLocal<IdentityHashMap<Object, Integer>> DIST_CACHE =
            ThreadLocal.withInitial(IdentityHashMap::new);
    private static volatile int cacheTick = -1;

    private static volatile Method entityBlockX;
    private static volatile Method entityBlockZ;
    private static volatile Method levelPlayers;
    private static volatile Method entityLevel;
    private static volatile boolean reflectFailed;
    private static volatile boolean reflectReady;

    private YapDistantBrain() {
    }

    public static long pathSkipCount() {
        return PATH_SKIPS.get();
    }

    public static long goalSkipCount() {
        return GOAL_SKIPS.get();
    }

    public static long brainSkipCount() {
        return BRAIN_SKIPS.get();
    }

    public static long brainThrottleCount() {
        return FULL_THROTTLES.get();
    }

    public static void bumpTick() {
        int t = TICK.incrementAndGet();
        cacheTick = t;
        DIST_CACHE.get().clear();
    }

    /** Alias used by PathNavigation.createPath / recomputePath. */
    public static boolean shouldSkipPathRecompute(Object nmsMob) {
        return shouldSkipPathfind(nmsMob);
    }

    public static boolean shouldSkipPathfind(Object nmsMob) {
        if (!YapPhase3Flags.spatialDistantBrain() || nmsMob == null || reflectFailed) {
            return false;
        }
        try {
            int distSq = nearestPlayerDistSqCached(nmsMob);
            if (distSq < 0) {
                return false;
            }
            int start = YapPhase3Flags.distantBrainStartBlocks();
            if (distSq < start * (long) start) {
                return false;
            }
            if (!onInterval(nmsMob, intervalFor(distSq))) {
                PATH_SKIPS.incrementAndGet();
                return true;
            }
            return false;
        } catch (Throwable t) {
            reflectFailed = true;
            LOG.log(Level.FINE, "distant brain path reflect failed", t);
            return false;
        }
    }

    /**
     * Mid/far band: skip GoalSelector / sensing / navigation.tick / customServerAiStep;
     * caller still runs move/look/jump controls.
     */
    public static boolean shouldThrottleGoals(Object nmsMob) {
        if (!YapPhase3Flags.spatialDistantBrain() || nmsMob == null || reflectFailed) {
            return false;
        }
        try {
            int distSq = nearestPlayerDistSqCached(nmsMob);
            if (distSq < 0) {
                return false;
            }
            int start = YapPhase3Flags.distantBrainStartBlocks();
            if (distSq < start * (long) start) {
                return false;
            }
            if (!onInterval(nmsMob, intervalFor(distSq))) {
                GOAL_SKIPS.incrementAndGet();
                return true;
            }
            return false;
        } catch (Throwable t) {
            reflectFailed = true;
            return false;
        }
    }

    /** Brain sensor/behavior tick — keep memory TTL only when throttled. */
    public static boolean shouldThrottleBrain(Object nmsEntity) {
        if (!YapPhase3Flags.spatialDistantBrain() || nmsEntity == null || reflectFailed) {
            return false;
        }
        try {
            int distSq = nearestPlayerDistSqCached(nmsEntity);
            if (distSq < 0) {
                return false;
            }
            int start = YapPhase3Flags.distantBrainStartBlocks();
            if (distSq < start * (long) start) {
                return false;
            }
            if (!onInterval(nmsEntity, intervalFor(distSq))) {
                BRAIN_SKIPS.incrementAndGet();
                return true;
            }
            return false;
        } catch (Throwable t) {
            reflectFailed = true;
            return false;
        }
    }

    /** Far band: replace full entity tick with inactiveTick. */
    public static boolean shouldThrottleFullTick(Object nmsEntity) {
        if (!YapPhase3Flags.spatialDistantBrain() || nmsEntity == null || reflectFailed) {
            return false;
        }
        try {
            int distSq = nearestPlayerDistSqCached(nmsEntity);
            if (distSq < 0) {
                return false;
            }
            int far = YapPhase3Flags.distantBrainFarBlocks();
            if (distSq < far * (long) far) {
                return false;
            }
            if (!onInterval(nmsEntity, YapPhase3Flags.distantBrainMaxInterval())) {
                FULL_THROTTLES.incrementAndGet();
                return true;
            }
            return false;
        } catch (Throwable t) {
            reflectFailed = true;
            return false;
        }
    }

    private static int intervalFor(int distSq) {
        int start = YapPhase3Flags.distantBrainStartBlocks();
        int maxInterval = YapPhase3Flags.distantBrainMaxInterval();
        int span = Math.max(1, YapPhase3Flags.distantBrainFarBlocks() - start);
        int over = (int) Math.sqrt(distSq) - start;
        if (over < 0) {
            over = 0;
        }
        int interval = 2 + (over * Math.max(0, maxInterval - 2)) / span;
        return Math.min(maxInterval, Math.max(2, interval));
    }

    private static boolean onInterval(Object entity, int interval) {
        int hash = System.identityHashCode(entity);
        return (TICK.get() + (hash & 0x7fffffff)) % Math.max(1, interval) == 0;
    }

    private static int nearestPlayerDistSqCached(Object nmsEntity) throws ReflectiveOperationException {
        IdentityHashMap<Object, Integer> cache = DIST_CACHE.get();
        if (cacheTick != TICK.get()) {
            cache.clear();
            cacheTick = TICK.get();
        }
        Integer hit = cache.get(nmsEntity);
        if (hit != null) {
            return hit;
        }
        int d = nearestPlayerDistSq(nmsEntity);
        cache.put(nmsEntity, d);
        return d;
    }

    private static int nearestPlayerDistSq(Object nmsEntity) throws ReflectiveOperationException {
        ensureReflect(nmsEntity);
        if (!reflectReady) {
            return -1;
        }
        Object level = entityLevel.invoke(nmsEntity);
        if (level == null) {
            return -1;
        }
        @SuppressWarnings("unchecked")
        Iterable<Object> players = (Iterable<Object>) levelPlayers.invoke(level);
        if (players == null) {
            // Unknown player list — do not invent "infinitely far"
            return -1;
        }
        int ex = ((Number) entityBlockX.invoke(nmsEntity)).intValue();
        int ez = ((Number) entityBlockZ.invoke(nmsEntity)).intValue();
        int best = Integer.MAX_VALUE;
        boolean any = false;
        for (Object p : players) {
            if (p == null) {
                continue;
            }
            any = true;
            int px = ((Number) entityBlockX.invoke(p)).intValue();
            int pz = ((Number) entityBlockZ.invoke(p)).intValue();
            int dx = ex - px;
            int dz = ez - pz;
            int d = dx * dx + dz * dz;
            if (d < best) {
                best = d;
            }
        }
        // Empty player list (MSPT bench / empty world): do not throttle.
        // Throttle only applies when we know every player is far; "no players"
        // must still run full physics (TNT fuse, hoppers, etc.).
        return any ? best : -1;
    }

    private static synchronized void ensureReflect(Object nmsEntity) {
        if (reflectReady || reflectFailed) {
            return;
        }
        try {
            Class<?> c = nmsEntity.getClass();
            while (c != null && entityLevel == null) {
                try {
                    entityLevel = c.getMethod("level");
                } catch (NoSuchMethodException e) {
                    c = c.getSuperclass();
                }
            }
            if (entityLevel == null) {
                throw new NoSuchMethodException("level");
            }
            entityBlockX = nmsEntity.getClass().getMethod("getBlockX");
            entityBlockZ = nmsEntity.getClass().getMethod("getBlockZ");
            Object level = entityLevel.invoke(nmsEntity);
            levelPlayers = level.getClass().getMethod("players");
            reflectReady = true;
        } catch (Throwable t) {
            reflectFailed = true;
            LOG.log(Level.FINE, "YapDistantBrain reflect init failed", t);
        }
    }
}
