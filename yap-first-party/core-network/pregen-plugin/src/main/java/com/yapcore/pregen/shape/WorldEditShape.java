package com.yapcore.pregen.shape;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

/**
 * WorldEdit selection → chunk shape (soft-depend via reflection).
 */
public final class WorldEditShape implements ChunkShape {

    private final List<ChunkPos> coords;
    private final String desc;

    private WorldEditShape(List<ChunkPos> coords, String desc) {
        this.coords = List.copyOf(coords);
        this.desc = desc;
    }

    public static WorldEditShape fromPlayer(Player player, Logger log) throws Exception {
        if (Bukkit.getPluginManager().getPlugin("WorldEdit") == null) {
            throw new IllegalStateException("WorldEdit is not installed");
        }
        Class<?> bukkitAdapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
        Class<?> worldEditCl = Class.forName("com.sk89q.worldedit.WorldEdit");
        Object we = worldEditCl.getMethod("getInstance").invoke(null);
        Object sessionMgr = we.getClass().getMethod("getSessionManager").invoke(we);
        Object actor = bukkitAdapter.getMethod("adapt", Player.class).invoke(null, player);

        Object session = null;
        for (Method m : sessionMgr.getClass().getMethods()) {
            if (!"get".equals(m.getName()) || m.getParameterCount() != 1) {
                continue;
            }
            try {
                session = m.invoke(sessionMgr, actor);
                if (session != null) {
                    break;
                }
            } catch (Exception ignored) {
            }
        }
        if (session == null) {
            throw new IllegalStateException("No WorldEdit session for player");
        }

        Object weWorld = bukkitAdapter.getMethod("adapt", World.class).invoke(null, player.getWorld());
        Object region;
        try {
            region = session.getClass()
                    .getMethod("getSelection", Class.forName("com.sk89q.worldedit.world.World"))
                    .invoke(session, weWorld);
        } catch (Exception e) {
            throw new IllegalStateException("No WorldEdit selection — use //wand first", e);
        }
        if (region == null) {
            throw new IllegalStateException("Empty WorldEdit selection");
        }

        String type = region.getClass().getSimpleName();
        try {
            Method getPoints = region.getClass().getMethod("getPoints");
            @SuppressWarnings("unchecked")
            List<?> points = (List<?>) getPoints.invoke(region);
            if (points != null && points.size() >= 3) {
                int[] xz = new int[points.size() * 2];
                int i = 0;
                for (Object p : points) {
                    xz[i++] = callInt(p, "x", "getX", "getBlockX");
                    xz[i++] = callInt(p, "z", "getZ", "getBlockZ");
                }
                return new WorldEditShape(ChunkShape.materialize(new PolygonShape(xz)), "worldedit " + type);
            }
        } catch (NoSuchMethodException ignored) {
        }

        Object min = region.getClass().getMethod("getMinimumPoint").invoke(region);
        Object max = region.getClass().getMethod("getMaximumPoint").invoke(region);
        int minX = callInt(min, "x", "getX", "getBlockX");
        int minZ = callInt(min, "z", "getZ", "getBlockZ");
        int maxX = callInt(max, "x", "getX", "getBlockX");
        int maxZ = callInt(max, "z", "getZ", "getBlockZ");
        return new WorldEditShape(
                ChunkShape.materialize(new RectShape(minX, minZ, maxX, maxZ)),
                "worldedit " + type);
    }

    private static int callInt(Object o, String... names) throws Exception {
        for (String n : names) {
            try {
                Method m = o.getClass().getMethod(n);
                Object v = m.invoke(o);
                if (v instanceof Number num) {
                    return num.intValue();
                }
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new NoSuchMethodException("No int accessor on " + o.getClass());
    }

    @Override
    public long size() {
        return coords.size();
    }

    @Override
    public String description() {
        return desc;
    }

    @Override
    public Iterator<ChunkPos> iterator() {
        return coords.iterator();
    }
}
