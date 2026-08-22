package com.yapcore.game.sched;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Schedule work onto Folia {@code GlobalRegionScheduler} when present,
 * else Paper {@code BukkitScheduler.runTask}, else NMS {@code execute}.
 */
public final class GameSchedulers {

    private static final Logger LOG = Logger.getLogger("YaPcore.GameSched");

    private GameSchedulers() {
    }

    /**
     * @return true if {@code task} was handed to a scheduler (may still be pending)
     */
    public static boolean scheduleSync(Class<?> bukkit, Object server, ClassLoader cl, Runnable task) {
        if (tryGlobalRegion(bukkit, server, cl, task)) {
            return true;
        }
        if (tryBukkitScheduler(server, cl, task)) {
            return true;
        }
        return executeOnMinecraftServer(cl, task);
    }

    private static boolean tryGlobalRegion(Class<?> bukkit, Object server, ClassLoader cl, Runnable task) {
        try {
            Object global = bukkit.getMethod("getGlobalRegionScheduler").invoke(null);
            if (global == null) {
                return false;
            }
            Object plugin = firstEnabledPlugin(server);
            if (plugin == null) {
                // Folia execute(Plugin, Consumer) needs a plugin; fall through
                return false;
            }
            Class<?> pluginCl = Class.forName("org.bukkit.plugin.Plugin", true, cl);
            Class<?> consumerCl = Class.forName("java.util.function.Consumer", true, cl);
            Object consumer = java.lang.reflect.Proxy.newProxyInstance(
                    cl,
                    new Class<?>[]{consumerCl},
                    (proxy, method, args) -> {
                        if ("accept".equals(method.getName())) {
                            task.run();
                            return null;
                        }
                        if ("toString".equals(method.getName())) {
                            return "YapGameSchedConsumer";
                        }
                        if ("hashCode".equals(method.getName())) {
                            return System.identityHashCode(proxy);
                        }
                        if ("equals".equals(method.getName())) {
                            return proxy == args[0];
                        }
                        return null;
                    });
            // Folia / Paper: run(Plugin, Consumer) or execute(Plugin, Consumer)
            for (String name : new String[]{"run", "execute"}) {
                try {
                    global.getClass().getMethod(name, pluginCl, consumerCl)
                            .invoke(global, plugin, consumer);
                    return true;
                } catch (NoSuchMethodException ignored) {
                }
            }
            try {
                global.getClass().getMethod("execute", pluginCl, Runnable.class)
                        .invoke(global, plugin, task);
                return true;
            } catch (NoSuchMethodException ignored) {
                return false;
            }
        } catch (Throwable t) {
            LOG.log(Level.FINE, "GlobalRegionScheduler unavailable", t);
            return false;
        }
    }

    private static boolean tryBukkitScheduler(Object server, ClassLoader cl, Runnable task) {
        try {
            Object plugin = firstEnabledPlugin(server);
            if (plugin == null) {
                return false;
            }
            Class<?> pluginCl = Class.forName("org.bukkit.plugin.Plugin", true, cl);
            Object scheduler = server.getClass().getMethod("getScheduler").invoke(server);
            scheduler.getClass()
                    .getMethod("runTask", pluginCl, Runnable.class)
                    .invoke(scheduler, plugin, task);
            return true;
        } catch (Throwable t) {
            LOG.log(Level.FINE, "BukkitScheduler.runTask failed (expected on Folia)", t);
            return false;
        }
    }

    private static Object firstEnabledPlugin(Object server) {
        try {
            Object pm = server.getClass().getMethod("getPluginManager").invoke(server);
            Object[] plugins = (Object[]) pm.getClass().getMethod("getPlugins").invoke(pm);
            if (plugins == null) {
                return null;
            }
            for (Object p : plugins) {
                if (p != null && Boolean.TRUE.equals(p.getClass().getMethod("isEnabled").invoke(p))) {
                    return p;
                }
            }
        } catch (Throwable ignored) {
            // none
        }
        return null;
    }

    private static boolean executeOnMinecraftServer(ClassLoader cl, Runnable run) {
        try {
            Class<?> ms = Class.forName("net.minecraft.server.MinecraftServer", true, cl);
            Object server = ms.getMethod("getServer").invoke(null);
            if (server == null) {
                return false;
            }
            for (String name : new String[]{"execute", "scheduleOnMain", "tell"}) {
                try {
                    server.getClass().getMethod(name, Runnable.class).invoke(server, run);
                    return true;
                } catch (NoSuchMethodException ignored) {
                }
            }
            return false;
        } catch (Throwable t) {
            LOG.log(Level.FINE, "executeOnMinecraftServer", t);
            return false;
        }
    }
}
