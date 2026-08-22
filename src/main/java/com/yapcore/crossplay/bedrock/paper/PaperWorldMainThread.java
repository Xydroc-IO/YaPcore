package com.yapcore.crossplay.bedrock.paper;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

final class PaperWorldMainThread {

    @FunctionalInterface
    interface InvOp {
        boolean run(Object player, Object inv, ClassLoader cl) throws Exception;
    }

    private final PaperWorldSyncBackend backend;

    PaperWorldMainThread(PaperWorldSyncBackend backend) {
        this.backend = backend;
    }

    void runOnMain(Runnable task) {
        try {
            ClassLoader cl = backend.paperLoader.get();
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit", true, cl);
            Object scheduler = bukkit.getMethod("getScheduler").invoke(null);
            Object plugin = findAnyPlugin(bukkit);
            if (plugin == null) {
                task.run();
                return;
            }
            Method runTask = scheduler.getClass().getMethod("runTask",
                    Class.forName("org.bukkit.plugin.Plugin", true, cl), Runnable.class);
            runTask.invoke(scheduler, plugin, task);
        } catch (Exception e) {
            PaperWorldSyncBackend.LOG.log(Level.FINE, "schedule Paper sync failed; running inline", e);
            try {
                task.run();
            } catch (Exception ex) {
                PaperWorldSyncBackend.LOG.log(Level.FINE, "inline Paper sync failed", ex);
            }
        }
    }

    boolean runPlayerInv(String username, InvOp op) {
        if (!backend.isEnabled() || username == null || username.isBlank()) {
            return false;
        }
        try {
            ClassLoader cl = backend.paperLoader.get();
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit", true, cl);
            Object player = findPlayer(bukkit, username);
            if (player == null) {
                return false;
            }
            Object inv = player.getClass().getMethod("getInventory").invoke(player);
            boolean primary = Boolean.TRUE.equals(bukkit.getMethod("isPrimaryThread").invoke(null));
            if (primary) {
                return op.run(player, inv, cl);
            }
            AtomicBoolean ok = new AtomicBoolean(false);
            CountDownLatch done = new CountDownLatch(1);
            Object server = bukkit.getMethod("getServer").invoke(null);
            Object plugin = findAnyPlugin(bukkit);
            Runnable r = () -> {
                try {
                    ok.set(op.run(player, inv, cl));
                } catch (Exception e) {
                    PaperWorldSyncBackend.LOG.log(Level.FINE, "inv op", e);
                } finally {
                    done.countDown();
                }
            };
            if (plugin != null) {
                Object scheduler = server.getClass().getMethod("getScheduler").invoke(server);
                Class<?> pluginCl = Class.forName("org.bukkit.plugin.Plugin", true, cl);
                scheduler.getClass().getMethod("runTask", pluginCl, Runnable.class)
                        .invoke(scheduler, plugin, r);
                done.await(2, TimeUnit.SECONDS);
            } else {
                r.run();
            }
            return ok.get();
        } catch (Exception e) {
            PaperWorldSyncBackend.LOG.log(Level.FINE, "runPlayerInv " + username, e);
            return false;
        }
    }

    static Object findPlayer(Class<?> bukkit, String username) throws Exception {
        Object player = bukkit.getMethod("getPlayerExact", String.class).invoke(null, username);
        if (player == null) {
            player = bukkit.getMethod("getPlayer", String.class).invoke(null, username);
        }
        return player;
    }

    static Object findAnyPlugin(Class<?> bukkit) {
        try {
            Object pm = bukkit.getMethod("getPluginManager").invoke(null);
            Object[] plugins = (Object[]) pm.getClass().getMethod("getPlugins").invoke(pm);
            if (plugins != null && plugins.length > 0) {
                return plugins[0];
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    static int parse(String s, int fallback) {
        if (s == null || s.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
