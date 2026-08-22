package com.yapcore.crossplay.bedrock.paper;

import com.yapcore.crossplay.bedrock.BedrockPaperWorldSync;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

final class PaperWorldEntities {

    private final PaperWorldSyncBackend backend;

    PaperWorldEntities(PaperWorldSyncBackend backend) {
        this.backend = backend;
    }

    Object findOnlinePlayer(String username) {
        if (!backend.isEnabled() || username == null) {
            return null;
        }
        try {
            ClassLoader cl = backend.paperLoader.get();
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit", true, cl);
            return PaperWorldMainThread.findPlayer(bukkit, username);
        } catch (Exception e) {
            return null;
        }
    }

    double[] spawnPosition() {
        if (!backend.isEnabled()) {
            return null;
        }
        try {
            ClassLoader cl = backend.paperLoader.get();
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit", true, cl);
            Method getWorlds = bukkit.getMethod("getWorlds");
            @SuppressWarnings("unchecked")
            List<Object> worlds = (List<Object>) getWorlds.invoke(null);
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
            PaperWorldSyncBackend.LOG.log(Level.FINE, "Paper spawn lookup failed", e);
            return null;
        }
    }

    int sampleGroundY(int chunkX, int chunkZ) {
        if (!backend.isEnabled()) {
            return -1;
        }
        try {
            int x = (chunkX << 4) + 8;
            int z = (chunkZ << 4) + 8;
            ClassLoader cl = backend.paperLoader.get();
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit", true, cl);
            Method getWorlds = bukkit.getMethod("getWorlds");
            @SuppressWarnings("unchecked")
            List<Object> worlds = (List<Object>) getWorlds.invoke(null);
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

    List<BedrockPaperWorldSync.NearbyLiving> listNearbyLiving(double x, double y, double z, double radius) {
        List<BedrockPaperWorldSync.NearbyLiving> out = new ArrayList<>();
        if (!backend.isEnabled()) {
            return out;
        }
        try {
            ClassLoader cl = backend.paperLoader.get();
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit", true, cl);
            Method getWorlds = bukkit.getMethod("getWorlds");
            @SuppressWarnings("unchecked")
            List<Object> worlds = (List<Object>) getWorlds.invoke(null);
            if (worlds == null || worlds.isEmpty()) {
                return out;
            }
            Object world = worlds.get(0);
            Class<?> locCl = Class.forName("org.bukkit.Location", true, cl);
            Object loc = locCl.getConstructor(
                    Class.forName("org.bukkit.World", true, cl),
                    double.class, double.class, double.class)
                    .newInstance(world, x, y, z);
            Class<?> living = Class.forName("org.bukkit.entity.LivingEntity", true, cl);
            Class<?> playerCl = Class.forName("org.bukkit.entity.Player", true, cl);
            @SuppressWarnings("unchecked")
            Collection<Object> nearby = (Collection<Object>) world.getClass()
                    .getMethod("getNearbyEntities", locCl, double.class, double.class, double.class)
                    .invoke(world, loc, radius, radius, radius);
            for (Object e : nearby) {
                if (e == null || !living.isInstance(e) || playerCl.isInstance(e)) {
                    continue;
                }
                java.util.UUID uuid = (java.util.UUID) e.getClass().getMethod("getUniqueId").invoke(e);
                String name = String.valueOf(e.getClass().getMethod("getName").invoke(e));
                String type = e.getClass().getSimpleName();
                try {
                    Object et = e.getClass().getMethod("getType").invoke(e);
                    if (et != null) {
                        type = "minecraft:" + String.valueOf(et).toLowerCase(Locale.ROOT);
                    }
                } catch (Exception ignored) {
                }
                Object el = e.getClass().getMethod("getLocation").invoke(e);
                double ex = ((Number) el.getClass().getMethod("getX").invoke(el)).doubleValue();
                double ey = ((Number) el.getClass().getMethod("getY").invoke(el)).doubleValue();
                double ez = ((Number) el.getClass().getMethod("getZ").invoke(el)).doubleValue();
                float health = 20f;
                float maxHealth = 20f;
                try {
                    health = ((Number) e.getClass().getMethod("getHealth").invoke(e)).floatValue();
                    maxHealth = ((Number) e.getClass().getMethod("getMaxHealth").invoke(e)).floatValue();
                } catch (Exception ignored) {
                }
                out.add(new BedrockPaperWorldSync.NearbyLiving(name, uuid, type, ex, ey, ez, health, maxHealth));
            }
        } catch (Exception e) {
            PaperWorldSyncBackend.LOG.log(Level.FINE, "listNearbyLiving failed", e);
        }
        return out;
    }

    List<BedrockPaperWorldSync.OnlinePlayer> listOnlinePlayers() {
        List<BedrockPaperWorldSync.OnlinePlayer> out = new ArrayList<>();
        if (!backend.isEnabled()) {
            return out;
        }
        try {
            ClassLoader cl = backend.paperLoader.get();
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit", true, cl);
            Object coll = bukkit.getMethod("getOnlinePlayers").invoke(null);
            if (coll instanceof Collection<?> c) {
                for (Object p : c) {
                    out.add(readPlayer(p));
                }
            }
        } catch (Exception e) {
            PaperWorldSyncBackend.LOG.log(Level.FINE, "listOnlinePlayers failed", e);
        }
        return out;
    }

    float[] snapshotPlayerHealth(String username) {
        if (!backend.isEnabled() || username == null) {
            return null;
        }
        try {
            Object player = findOnlinePlayer(username);
            if (player == null) {
                return null;
            }
            float health = ((Number) player.getClass().getMethod("getHealth").invoke(player)).floatValue();
            float max = ((Number) player.getClass().getMethod("getMaxHealth").invoke(player)).floatValue();
            return new float[]{health, max};
        } catch (Exception e) {
            return null;
        }
    }

    private BedrockPaperWorldSync.OnlinePlayer readPlayer(Object p) throws ReflectiveOperationException {
        String name = (String) p.getClass().getMethod("getName").invoke(p);
        java.util.UUID uuid = (java.util.UUID) p.getClass().getMethod("getUniqueId").invoke(p);
        Object loc = p.getClass().getMethod("getLocation").invoke(p);
        double x = ((Number) loc.getClass().getMethod("getX").invoke(loc)).doubleValue();
        double y = ((Number) loc.getClass().getMethod("getY").invoke(loc)).doubleValue();
        double z = ((Number) loc.getClass().getMethod("getZ").invoke(loc)).doubleValue();
        return new BedrockPaperWorldSync.OnlinePlayer(name, uuid, x, y, z);
    }
}
