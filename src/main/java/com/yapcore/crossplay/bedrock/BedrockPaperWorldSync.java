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

    /** Paper overworld spawn (block coords), or null if unavailable. */
    public double[] spawnPosition() {
        if (!isEnabled()) {
            return null;
        }
        try {
            ClassLoader cl = paperLoader.get();
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit", true, cl);
            Method getWorlds = bukkit.getMethod("getWorlds");
            @SuppressWarnings("unchecked")
            java.util.List<Object> worlds = (java.util.List<Object>) getWorlds.invoke(null);
            if (worlds == null || worlds.isEmpty()) {
                return null;
            }
            Object world = worlds.get(0);
            Object spawn = world.getClass().getMethod("getSpawnLocation").invoke(world);
            double x = ((Number) spawn.getClass().getMethod("getX").invoke(spawn)).doubleValue();
            double y = ((Number) spawn.getClass().getMethod("getY").invoke(spawn)).doubleValue();
            double z = ((Number) spawn.getClass().getMethod("getZ").invoke(spawn)).doubleValue();
            return new double[]{x, y, z};
        } catch (Exception e) {
            LOG.log(Level.FINE, "Paper spawn lookup failed", e);
            return null;
        }
    }

    /** Highest solid block Y at chunk center, or -1. */
    public int sampleGroundY(int chunkX, int chunkZ) {
        if (!isEnabled()) {
            return -1;
        }
        try {
            int x = (chunkX << 4) + 8;
            int z = (chunkZ << 4) + 8;
            ClassLoader cl = paperLoader.get();
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit", true, cl);
            Method getWorlds = bukkit.getMethod("getWorlds");
            @SuppressWarnings("unchecked")
            java.util.List<Object> worlds = (java.util.List<Object>) getWorlds.invoke(null);
            if (worlds == null || worlds.isEmpty()) {
                return -1;
            }
            Object world = worlds.get(0);
            Method highest = world.getClass().getMethod("getHighestBlockYAt", int.class, int.class);
            return ((Number) highest.invoke(world, x, z)).intValue();
        } catch (Exception e) {
            return -1;
        }
    }

    public record OnlinePlayer(String name, java.util.UUID uuid, double x, double y, double z) {
    }

    /** Snapshot of Paper online players (JE) for BE entity mirror. */
    public java.util.List<OnlinePlayer> listOnlinePlayers() {
        java.util.List<OnlinePlayer> out = new java.util.ArrayList<>();
        if (!isEnabled()) {
            return out;
        }
        try {
            ClassLoader cl = paperLoader.get();
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit", true, cl);
            Object coll = bukkit.getMethod("getOnlinePlayers").invoke(null);
            if (coll instanceof java.util.Collection<?> c) {
                for (Object p : c) {
                    out.add(readPlayer(p));
                }
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "listOnlinePlayers failed", e);
        }
        return out;
    }

    private OnlinePlayer readPlayer(Object p) throws ReflectiveOperationException {
        String name = (String) p.getClass().getMethod("getName").invoke(p);
        java.util.UUID uuid = (java.util.UUID) p.getClass().getMethod("getUniqueId").invoke(p);
        Object loc = p.getClass().getMethod("getLocation").invoke(p);
        double x = ((Number) loc.getClass().getMethod("getX").invoke(loc)).doubleValue();
        double y = ((Number) loc.getClass().getMethod("getY").invoke(loc)).doubleValue();
        double z = ((Number) loc.getClass().getMethod("getZ").invoke(loc)).doubleValue();
        return new OnlinePlayer(name, uuid, x, y, z);
    }

    /**
     * Snapshot one overworld column as Bedrock hashed state ids (air/stone/dirt/grass/bedrock).
     * Y range −64..319 → 24×4096 ints, XZY index {@code (x<<8)|(z<<4)|localY}.
     * Returns null if Paper is unavailable. Read-only; runs on the calling thread
     * (same pattern as {@link #spawnPosition()}).
     */
    public int[][] snapshotColumnHashedStates(int chunkX, int chunkZ) {
        if (!isEnabled()) {
            return null;
        }
        try {
            return readColumnHashedStates(chunkX, chunkZ);
        } catch (Exception e) {
            LOG.log(Level.FINE, "column snapshot failed", e);
            return null;
        }
    }

    private int[][] readColumnHashedStates(int chunkX, int chunkZ) throws ReflectiveOperationException {
        final int sections = 24;
        final int minY = -64;
        int[][] out = new int[sections][4096];
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        for (int s = 0; s < sections; s++) {
            int y0 = minY + s * 16;
            boolean anyNonAir = false;
            for (int ly = 0; ly < 16; ly++) {
                int y = y0 + ly;
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        int state = materialToHashedState(blockAt(baseX + x, y, baseZ + z));
                        out[s][(x << 8) | (z << 4) | ly] = state;
                        if (state != BedrockPacketCodec.hashedAir()) {
                            anyNonAir = true;
                        }
                    }
                }
            }
            if (!anyNonAir) {
                // leave as air (default 0 fill → remap)
                java.util.Arrays.fill(out[s], BedrockPacketCodec.hashedAir());
            }
        }
        return out;
    }

    private int materialToHashedState(Object block) throws ReflectiveOperationException {
        if (block == null) {
            return BedrockPacketCodec.hashedAir();
        }
        Object type = block.getClass().getMethod("getType").invoke(block);
        if (type == null) {
            return BedrockPacketCodec.hashedAir();
        }
        String name = String.valueOf(type);
        // Enum name or Material.toString
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            name = name.substring(dot + 1);
        }
        name = name.toUpperCase();
        return switch (name) {
            case "AIR", "CAVE_AIR", "VOID_AIR" -> BedrockPacketCodec.hashedAir();
            case "BEDROCK" -> BedrockPacketCodec.hashedBedrock();
            case "GRASS_BLOCK", "GRASS" -> BedrockPacketCodec.hashedGrass();
            case "DIRT", "COARSE_DIRT", "ROOTED_DIRT", "PODZOL", "MUD" -> BedrockPacketCodec.hashedDirt();
            case "WATER", "LAVA" -> BedrockPacketCodec.hashedAir(); // treat fluids as air for silhouette MVP
            default -> {
                // leaves / glass / etc. still solid silhouette
                if (name.contains("AIR")) {
                    yield BedrockPacketCodec.hashedAir();
                }
                yield BedrockPacketCodec.hashedStone();
            }
        };
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
