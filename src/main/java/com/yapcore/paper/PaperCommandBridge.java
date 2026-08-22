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

    /**
     * Resolve a classloader that can load live Paper {@code org.bukkit.Bukkit}
     * (not YaPcore's stub). Paperclip host CL often cannot — Paper boots under
     * the Server thread's context CL.
     */
    public static ClassLoader resolvePaperLoader(ClassLoader preferred) {
        ClassLoader found = tryPaperLoader(preferred);
        if (found != null) {
            return found;
        }
        // Prefer Paper's main thread — most reliable live Bukkit CL
        for (Thread th : Thread.getAllStackTraces().keySet()) {
            if (th == null || th.getContextClassLoader() == null) {
                continue;
            }
            if (!"Server thread".equals(th.getName())) {
                continue;
            }
            found = tryPaperLoader(th.getContextClassLoader());
            if (found != null) {
                return found;
            }
        }
        for (Thread th : Thread.getAllStackTraces().keySet()) {
            if (th == null) {
                continue;
            }
            found = tryPaperLoader(th.getContextClassLoader());
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** Returns loader only if it exposes real Paper Bukkit (has isPrimaryThread). */
    private static ClassLoader tryPaperLoader(ClassLoader cl) {
        if (cl == null) {
            return null;
        }
        try {
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit", false, cl);
            // YaP stub Bukkit lacks isPrimaryThread — reject it
            bukkit.getMethod("isPrimaryThread");
            Object server = bukkit.getMethod("getServer").invoke(null);
            if (server == null) {
                return null;
            }
            return cl;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean scheduleOnMain(Class<?> bukkit, Object server, ClassLoader cl, Runnable run) {
        // Folia: GlobalRegionScheduler first; Paper: BukkitScheduler; else NMS execute.
        return com.yapcore.game.GameSchedulers.scheduleSync(bukkit, server, cl, run);
    }
}
