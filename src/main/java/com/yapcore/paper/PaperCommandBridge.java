package com.yapcore.paper;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Forwards YaP console / GUI lines into real Paper so vanilla + Paper + plugin
 * commands ({@code /give}, {@code /tp}, {@code /gamemode}, …) work from the
 * control panel the same way they do in-game.
 */
public final class PaperCommandBridge {

    private static final Logger LOG = Logger.getLogger("YaPcore.PaperCmd");

    private PaperCommandBridge() {
    }

    /**
     * Dispatch {@code line} as the Paper console (strip leading {@code /}).
     * Runs on Paper's primary thread when possible.
     *
     * @return status message for the YaP console
     */
    public static String dispatchToPaper(String line, ClassLoader paperLoader) {
        if (line == null || line.isBlank()) {
            return "";
        }
        String cmd = line.trim();
        if (cmd.startsWith("/")) {
            cmd = cmd.substring(1).trim();
        }
        if (cmd.isEmpty()) {
            return "";
        }
        try {
            ClassLoader cl = resolvePaperLoader(paperLoader);
            if (cl == null) {
                return "Paper not ready — cannot run: " + cmd;
            }
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit", true, cl);
            Object server = bukkit.getMethod("getServer").invoke(null);
            if (server == null) {
                return "Paper server not online — try again in a moment";
            }
            Class<?> senderCl = Class.forName("org.bukkit.command.CommandSender", true, cl);
            Object console = server.getClass().getMethod("getConsoleSender").invoke(server);
            Method dispatch = server.getClass().getMethod("dispatchCommand", senderCl, String.class);

            boolean primary = Boolean.TRUE.equals(bukkit.getMethod("isPrimaryThread").invoke(null));
            AtomicReference<Throwable> fault = new AtomicReference<>();
            CountDownLatch done = new CountDownLatch(1);
            String command = cmd;
            Runnable run = () -> {
                try {
                    dispatch.invoke(server, console, command);
                } catch (Throwable t) {
                    fault.set(t);
                } finally {
                    done.countDown();
                }
            };

            if (primary) {
                run.run();
            } else if (!scheduleOnMain(bukkit, server, cl, run)) {
                // Last resort: run here (may warn about async command)
                run.run();
            } else if (!done.await(5, TimeUnit.SECONDS)) {
                return "Paper command still running: /" + cmd
                        + " (check paper-kernel/logs or GUI console)";
            }

            Throwable err = fault.get();
            if (err != null) {
                Throwable c = err.getCause() != null ? err.getCause() : err;
                LOG.log(Level.WARNING, "Paper command failed: /" + cmd, c);
                return "Paper command error: " + c.getMessage();
            }
            return "Paper: /" + cmd;
        } catch (Throwable t) {
            LOG.log(Level.WARNING, "Forward to Paper failed", t);
            return "Could not reach Paper commands: " + t.getMessage();
        }
    }

    private static ClassLoader resolvePaperLoader(ClassLoader preferred) {
        if (preferred != null) {
            try {
                Class.forName("org.bukkit.Bukkit", false, preferred);
                Object server = Class.forName("org.bukkit.Bukkit", true, preferred)
                        .getMethod("getServer").invoke(null);
                if (server != null) {
                    return preferred;
                }
            } catch (Throwable ignored) {
            }
        }
        for (Thread th : Thread.getAllStackTraces().keySet()) {
            ClassLoader cl = th.getContextClassLoader();
            if (cl == null) {
                continue;
            }
            try {
                Class<?> bukkit = Class.forName("org.bukkit.Bukkit", false, cl);
                Object server = bukkit.getMethod("getServer").invoke(null);
                if (server != null) {
                    return cl;
                }
            } catch (Throwable ignored) {
            }
        }
        return preferred;
    }

    private static boolean scheduleOnMain(Class<?> bukkit, Object server, ClassLoader cl, Runnable run) {
        try {
            Object pm = server.getClass().getMethod("getPluginManager").invoke(server);
            Object[] plugins = (Object[]) pm.getClass().getMethod("getPlugins").invoke(pm);
            Object plugin = null;
            for (Object p : plugins) {
                if (p == null) {
                    continue;
                }
                if (Boolean.TRUE.equals(p.getClass().getMethod("isEnabled").invoke(p))) {
                    plugin = p;
                    break;
                }
            }
            if (plugin == null) {
                return executeOnMinecraftServer(cl, run);
            }
            Class<?> pluginCl = Class.forName("org.bukkit.plugin.Plugin", true, cl);
            Object scheduler = server.getClass().getMethod("getScheduler").invoke(server);
            scheduler.getClass()
                    .getMethod("runTask", pluginCl, Runnable.class)
                    .invoke(scheduler, plugin, run);
            return true;
        } catch (Throwable t) {
            LOG.log(Level.FINE, "scheduleOnMain", t);
            return executeOnMinecraftServer(cl, run);
        }
    }

    private static boolean executeOnMinecraftServer(ClassLoader cl, Runnable run) {
        try {
            Class<?> ms = Class.forName("net.minecraft.server.MinecraftServer", true, cl);
            Object server = ms.getMethod("getServer").invoke(null);
            if (server == null) {
                return false;
            }
            // DedicatedServer / MinecraftServer implement execute(Runnable) via tick thread
            for (String name : new String[]{"execute", "scheduleOnMain", "tell"}) {
                try {
                    Method m = server.getClass().getMethod(name, Runnable.class);
                    m.invoke(server, run);
                    return true;
                } catch (NoSuchMethodException ignored) {
                }
            }
            // CraftServer path
            return false;
        } catch (Throwable t) {
            LOG.log(Level.FINE, "executeOnMinecraftServer", t);
            return false;
        }
    }
}
