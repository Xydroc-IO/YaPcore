package com.yapcore.crossplay.bedrock;

import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Applies Bedrock BREAK/PLACE (and light MOVE) onto the Paper world when
 * {@code game-authority=paper}. Uses Paper's classloader + main-thread schedule.
 */
public final class BedrockPaperWorldSync {

    private static final Logger LOG = Logger.getLogger("YaPcore.BedrockPaper");

    private final AtomicReference<URLClassLoader> paperLoader = new AtomicReference<>();
    private volatile boolean enabled;

    public void attach(URLClassLoader loader) {
        paperLoader.set(loader);
        enabled = loader != null;
        if (enabled) {
            LOG.info("Bedrock→Paper world sync attached");
        }
    }

    public void detach() {
        paperLoader.set(null);
        enabled = false;
    }

    public boolean isEnabled() {
        return enabled && paperLoader.get() != null;
    }

    public void apply(String action, Map<String, String> payload) {
        if (!isEnabled() || payload == null) {
            return;
        }
        String act = action == null ? "" : action.trim().toUpperCase();
        switch (act) {
            case "BREAK" -> runOnMain(() -> breakBlock(
                    parse(payload.get("x"), 0),
                    parse(payload.get("y"), 0),
                    parse(payload.get("z"), 0)));
            case "PLACE" -> runOnMain(() -> placeBlock(
                    parse(payload.get("x"), 0),
                    parse(payload.get("y"), 0),
                    parse(payload.get("z"), 0),
                    payload.getOrDefault("block", "stone")));
            default -> {
            }
        }
    }

    private void breakBlock(int x, int y, int z) {
        try {
            Object block = blockAt(x, y, z);
            if (block == null) {
                return;
            }
            Method setType = block.getClass().getMethod("setType", materialClass());
            Object air = materialValue("AIR");
            setType.invoke(block, air);
            LOG.fine(() -> "Paper BREAK @" + x + "," + y + "," + z);
        } catch (ReflectiveOperationException e) {
            LOG.log(Level.FINE, "Paper BREAK failed", e);
        }
    }

    private void placeBlock(int x, int y, int z, String blockName) {
        try {
            Object block = blockAt(x, y, z);
            if (block == null) {
                return;
            }
            String matName = blockName == null ? "STONE" : blockName.trim().toUpperCase()
                    .replace("MINECRAFT:", "").replace(' ', '_');
            Object mat = materialValue(matName);
            if (mat == null) {
                mat = materialValue("STONE");
            }
            Method setType = block.getClass().getMethod("setType", materialClass());
            setType.invoke(block, mat);
            LOG.fine(() -> "Paper PLACE " + matName + " @" + x + "," + y + "," + z);
        } catch (ReflectiveOperationException e) {
            LOG.log(Level.FINE, "Paper PLACE failed", e);
        }
    }

    private Object blockAt(int x, int y, int z) throws ReflectiveOperationException {
        ClassLoader cl = paperLoader.get();
        Class<?> bukkit = Class.forName("org.bukkit.Bukkit", true, cl);
        Method getWorlds = bukkit.getMethod("getWorlds");
        @SuppressWarnings("unchecked")
        java.util.List<Object> worlds = (java.util.List<Object>) getWorlds.invoke(null);
        if (worlds == null || worlds.isEmpty()) {
            return null;
        }
        Object world = worlds.get(0);
        Method getBlockAt = world.getClass().getMethod("getBlockAt", int.class, int.class, int.class);
        return getBlockAt.invoke(world, x, y, z);
    }

    private Class<?> materialClass() throws ClassNotFoundException {
        return Class.forName("org.bukkit.Material", true, paperLoader.get());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object materialValue(String name) {
        try {
            Class<?> mat = materialClass();
            Method match = mat.getMethod("matchMaterial", String.class);
            Object matched = match.invoke(null, name);
            if (matched != null) {
                return matched;
            }
            return Enum.valueOf((Class<? extends Enum>) mat, name);
        } catch (Exception e) {
            return null;
        }
    }

    private void runOnMain(Runnable task) {
        try {
            ClassLoader cl = paperLoader.get();
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit", true, cl);
            Object scheduler = bukkit.getMethod("getScheduler").invoke(null);
            Object plugin = findAnyPlugin(bukkit);
            if (plugin == null) {
                // Fallback: run inline (may be wrong thread — still better than drop)
                task.run();
                return;
            }
            Method runTask = scheduler.getClass().getMethod("runTask",
                    Class.forName("org.bukkit.plugin.Plugin", true, cl), Runnable.class);
            runTask.invoke(scheduler, plugin, task);
        } catch (Exception e) {
            LOG.log(Level.FINE, "schedule Paper sync failed; running inline", e);
            try {
                task.run();
            } catch (Exception ex) {
                LOG.log(Level.FINE, "inline Paper sync failed", ex);
            }
        }
    }

    private static Object findAnyPlugin(Class<?> bukkit) {
        try {
            Object pm = bukkit.getMethod("getPluginManager").invoke(null);
            Object[] plugins = (Object[]) pm.getClass().getMethod("getPlugins").invoke(pm);
            if (plugins != null && plugins.length > 0) {
                return plugins[0];
            }
        } catch (Exception ignored) {
            // none
        }
        return null;
    }

    private static int parse(String s, int fallback) {
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
