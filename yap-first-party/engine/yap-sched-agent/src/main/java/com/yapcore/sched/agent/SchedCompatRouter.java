package com.yapcore.sched.agent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runtime bridge invoked from rewritten {@code CraftScheduler.handle}.
 * Uses reflection so the agent jar does not compile against Paper/Folia.
 *
 * <p>Routing (first match):
 * <ol>
 *   <li>{@link SchedCompatContext#currentEntity()} → EntityScheduler</li>
 *   <li>{@link SchedCompatContext#currentLocation()} → RegionScheduler</li>
 *   <li>Folia region currently ticking this thread → RegionScheduler</li>
 *   <li>else → GlobalRegionScheduler (true global work, or off a tick thread)</li>
 * </ol>
 */
public final class SchedCompatRouter {

    private static final Logger LOG = Logger.getLogger("YaP.SchedCompat");
    private static final long NO_REPEATING = -1L;
    private static final ConcurrentHashMap<String, Boolean> WARNED_PLUGINS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, Object> FOLIA_TASKS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Set<Integer>> TASKS_BY_PLUGIN = new ConcurrentHashMap<>();

    private static volatile Boolean warnGlobal = Boolean.TRUE;

    private SchedCompatRouter() {
    }

    /** Invoked from rewritten CraftScheduler.handle. Returns the CraftTask. */
    public static Object handle(Object scheduler, Object craftTask, long delayTicks) {
        Objects.requireNonNull(craftTask, "craftTask");
        try {
            Object plugin = invoke(craftTask, "getOwner");
            if (plugin == null) {
                return craftTask;
            }
            long period = readPeriod(craftTask);
            Runnable body = () -> runCraftTask(craftTask);

            Object entity = SchedCompatContext.currentEntity();
            Object location = SchedCompatContext.currentLocation();
            CurrentRegionProbe.RegionRef current = null;

            Object scheduled;
            String route;
            if (entity != null && isAliveEntity(entity)) {
                scheduled = scheduleEntity(plugin, entity, body, delayTicks, period);
                route = "entity";
            } else if (location != null && hasWorld(location)) {
                scheduled = scheduleRegion(plugin, location, body, delayTicks, period);
                route = "region";
            } else if ((current = CurrentRegionProbe.probe()) != null) {
                scheduled = scheduleRegionChunk(plugin, current.world(), current.chunkX(), current.chunkZ(),
                        body, delayTicks, period);
                route = "current-region";
            } else {
                maybeWarnGlobal(plugin);
                scheduled = scheduleGlobal(plugin, body, delayTicks, period);
                route = "global";
            }

            SchedCompatMetrics.recordShim(route);
            int id = (Integer) invoke(craftTask, "getTaskId");
            remember(plugin, id, scheduled);
            return craftTask;
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, "yap-sched-agent: failed to route legacy scheduler task", t);
            throw new UnsupportedOperationException(
                    "yap-sched-agent could not route BukkitScheduler task; use Folia region APIs", t);
        }
    }

    public static void setWarnGlobal(boolean warn) {
        warnGlobal = warn;
    }

    /** Cancel the Folia backing task for a Bukkit task id. */
    public static void cancelFolia(int bukkitTaskId) {
        Object st = FOLIA_TASKS.remove(bukkitTaskId);
        if (st == null) {
            return;
        }
        cancelScheduled(st);
        TASKS_BY_PLUGIN.values().forEach(set -> set.remove(bukkitTaskId));
    }

    /** Cancel every Folia backing task owned by a plugin. */
    public static void cancelFoliaForPlugin(Object plugin) {
        if (plugin == null) {
            return;
        }
        String name = String.valueOf(invokeQuiet(plugin, "getName"));
        Set<Integer> ids = TASKS_BY_PLUGIN.remove(name);
        if (ids == null) {
            return;
        }
        for (Integer id : ids) {
            Object st = FOLIA_TASKS.remove(id);
            if (st != null) {
                cancelScheduled(st);
            }
        }
    }

    private static void remember(Object plugin, int id, Object scheduled) {
        if (scheduled == null) {
            return;
        }
        FOLIA_TASKS.put(id, scheduled);
        String name = String.valueOf(invokeQuiet(plugin, "getName"));
        TASKS_BY_PLUGIN.computeIfAbsent(name, k -> ConcurrentHashMap.newKeySet()).add(id);
    }

    private static void cancelScheduled(Object st) {
        try {
            st.getClass().getMethod("cancel").invoke(st);
        } catch (ReflectiveOperationException e) {
            LOG.log(Level.FINE, "cancel Folia task", e);
        }
    }

    private static void maybeWarnGlobal(Object plugin) {
        if (!Boolean.TRUE.equals(warnGlobal)) {
            return;
        }
        String name = plugin != null ? String.valueOf(invokeQuiet(plugin, "getName")) : "unknown";
        if (WARNED_PLUGINS.putIfAbsent(name, Boolean.TRUE) != null) {
            return;
        }
        LOG.warning("yap-sched-agent: legacy sync scheduler from plugin '" + name
                + "' routed to GlobalRegionScheduler (no entity/location/current-region). "
                + "Prefer EntityScheduler / RegionScheduler / YapSched. "
                + "Further warnings for this plugin suppressed.");
    }

    private static void runCraftTask(Object craftTask) {
        try {
            ((Runnable) craftTask).run();
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, "yap-sched-agent: legacy task threw", t);
        }
    }

    private static Object scheduleGlobal(Object plugin, Runnable body, long delay, long period)
            throws Exception {
        Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
        Object global = bukkit.getMethod("getGlobalRegionScheduler").invoke(null);
        Consumer<?> consumer = st -> body.run();
        if (period > 0) {
            long d = Math.max(1L, delay);
            return global.getClass()
                    .getMethod("runAtFixedRate",
                            Class.forName("org.bukkit.plugin.Plugin"),
                            Consumer.class, long.class, long.class)
                    .invoke(global, plugin, consumer, d, period);
        }
        if (delay <= 0) {
            return global.getClass()
                    .getMethod("run",
                            Class.forName("org.bukkit.plugin.Plugin"),
                            Consumer.class)
                    .invoke(global, plugin, consumer);
        }
        return global.getClass()
                .getMethod("runDelayed",
                        Class.forName("org.bukkit.plugin.Plugin"),
                        Consumer.class, long.class)
                .invoke(global, plugin, consumer, delay);
    }

    private static Object scheduleEntity(
            Object plugin, Object entity, Runnable body, long delay, long period) throws Exception {
        Object entitySched = entity.getClass().getMethod("getScheduler").invoke(entity);
        Consumer<?> consumer = st -> body.run();
        Class<?> pluginCl = Class.forName("org.bukkit.plugin.Plugin");
        if (period > 0) {
            long d = Math.max(1L, delay);
            return entitySched.getClass()
                    .getMethod("runAtFixedRate", pluginCl, Consumer.class, Runnable.class, long.class, long.class)
                    .invoke(entitySched, plugin, consumer, null, d, period);
        }
        if (delay <= 0) {
            return entitySched.getClass()
                    .getMethod("run", pluginCl, Consumer.class, Runnable.class)
                    .invoke(entitySched, plugin, consumer, null);
        }
        return entitySched.getClass()
                .getMethod("runDelayed", pluginCl, Consumer.class, Runnable.class, long.class)
                .invoke(entitySched, plugin, consumer, null, delay);
    }

    private static Object scheduleRegion(
            Object plugin, Object location, Runnable body, long delay, long period) throws Exception {
        Object world = location.getClass().getMethod("getWorld").invoke(location);
        if (world == null) {
            CurrentRegionProbe.RegionRef current = CurrentRegionProbe.probe();
            if (current != null) {
                return scheduleRegionChunk(plugin, current.world(), current.chunkX(), current.chunkZ(),
                        body, delay, period);
            }
            maybeWarnGlobal(plugin);
            return scheduleGlobal(plugin, body, delay, period);
        }
        int blockX = ((Number) location.getClass().getMethod("getBlockX").invoke(location)).intValue();
        int blockZ = ((Number) location.getClass().getMethod("getBlockZ").invoke(location)).intValue();
        return scheduleRegionChunk(plugin, world, blockX >> 4, blockZ >> 4, body, delay, period);
    }

    private static Object scheduleRegionChunk(
            Object plugin, Object world, int chunkX, int chunkZ,
            Runnable body, long delay, long period) throws Exception {
        Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
        Object region = bukkit.getMethod("getRegionScheduler").invoke(null);
        Consumer<?> consumer = st -> body.run();
        Class<?> pluginCl = Class.forName("org.bukkit.plugin.Plugin");
        Class<?> worldCl = Class.forName("org.bukkit.World");
        if (period > 0) {
            long d = Math.max(1L, delay);
            return region.getClass()
                    .getMethod("runAtFixedRate", pluginCl, worldCl, int.class, int.class,
                            Consumer.class, long.class, long.class)
                    .invoke(region, plugin, world, chunkX, chunkZ, consumer, d, period);
        }
        if (delay <= 0) {
            try {
                return region.getClass()
                        .getMethod("run", pluginCl, worldCl, int.class, int.class, Consumer.class)
                        .invoke(region, plugin, world, chunkX, chunkZ, consumer);
            } catch (NoSuchMethodException e) {
                region.getClass()
                        .getMethod("execute", pluginCl, worldCl, int.class, int.class, Runnable.class)
                        .invoke(region, plugin, world, chunkX, chunkZ, body);
                return null;
            }
        }
        return region.getClass()
                .getMethod("runDelayed", pluginCl, worldCl, int.class, int.class,
                        Consumer.class, long.class)
                .invoke(region, plugin, world, chunkX, chunkZ, consumer, delay);
    }

    private static long readPeriod(Object craftTask) {
        try {
            Method m = craftTask.getClass().getDeclaredMethod("getPeriod");
            m.setAccessible(true);
            return ((Number) m.invoke(craftTask)).longValue();
        } catch (ReflectiveOperationException e) {
            try {
                Field f = craftTask.getClass().getDeclaredField("period");
                f.setAccessible(true);
                return f.getLong(craftTask);
            } catch (ReflectiveOperationException e2) {
                return NO_REPEATING;
            }
        }
    }

    private static boolean isAliveEntity(Object entity) {
        try {
            Object valid = invokeQuiet(entity, "isValid");
            if (valid instanceof Boolean b && !b) {
                return false;
            }
            Object dead = invokeQuiet(entity, "isDead");
            return !(dead instanceof Boolean b && b);
        } catch (Throwable t) {
            return true;
        }
    }

    private static boolean hasWorld(Object location) {
        try {
            return invokeQuiet(location, "getWorld") != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private static Object invoke(Object target, String method) throws ReflectiveOperationException {
        return target.getClass().getMethod(method).invoke(target);
    }

    private static Object invokeQuiet(Object target, String method) {
        try {
            return invoke(target, method);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
