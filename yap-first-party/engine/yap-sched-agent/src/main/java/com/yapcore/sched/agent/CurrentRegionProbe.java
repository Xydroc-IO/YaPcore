package com.yapcore.sched.agent;

/**
 * World + chunk owned by the Folia region currently ticking this thread.
 * Used when a legacy {@code runTask} has no event/entity ThreadLocal.
 */
final class CurrentRegionProbe {

    record RegionRef(Object world, int chunkX, int chunkZ) {
    }

    private CurrentRegionProbe() {
    }

    static RegionRef probe() {
        try {
            Class<?> trs = Class.forName("io.papermc.paper.threadedregions.TickRegionScheduler");
            Object worldData = trs.getMethod("getCurrentRegionizedWorldData").invoke(null);
            if (worldData == null) {
                return null;
            }
            Object bukkitWorld = bukkitWorld(worldData);
            if (bukkitWorld == null) {
                return null;
            }
            int[] chunk = ownedChunk(worldData);
            if (chunk == null) {
                chunk = ownedChunkFromRegion(trs);
            }
            if (chunk == null) {
                return null;
            }
            return new RegionRef(bukkitWorld, chunk[0], chunk[1]);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object bukkitWorld(Object worldData) {
        Object nms = invoke(worldData, "getWorld");
        if (nms == null) {
            nms = field(worldData, "world");
        }
        if (nms == null) {
            return null;
        }
        Object bukkit = invoke(nms, "getWorld");
        return bukkit != null ? bukkit : nms;
    }

    private static int[] ownedChunk(Object worldData) {
        int[] fromPlayers = chunkFromList(invoke(worldData, "getLocalPlayers"));
        if (fromPlayers != null) {
            return fromPlayers;
        }
        Object entities = invoke(worldData, "getLocalEntitiesCopy");
        if (entities instanceof Object[] arr && arr.length > 0) {
            return chunkFromEntity(arr[0]);
        }
        return null;
    }

    private static int[] ownedChunkFromRegion(Class<?> trs) {
        try {
            Object region = trs.getMethod("getCurrentRegion").invoke(null);
            if (region == null) {
                return null;
            }
            Integer cx = number(invoke(region, "getCenterChunkX"));
            Integer cz = number(invoke(region, "getCenterChunkZ"));
            if (cx != null && cz != null) {
                return new int[]{cx, cz};
            }
            Object data = invoke(region, "getData");
            if (data != null) {
                cx = number(invoke(data, "getCenterChunkX"));
                cz = number(invoke(data, "getCenterChunkZ"));
                if (cx != null && cz != null) {
                    return new int[]{cx, cz};
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static int[] chunkFromList(Object list) {
        if (!(list instanceof java.util.List<?> players) || players.isEmpty()) {
            return null;
        }
        return chunkFromEntity(players.get(0));
    }

    private static int[] chunkFromEntity(Object nmsOrBukkit) {
        if (nmsOrBukkit == null) {
            return null;
        }
        Object loc = invoke(nmsOrBukkit, "getLocation");
        if (loc == null) {
            Object bukkit = invoke(nmsOrBukkit, "getBukkitEntity");
            loc = bukkit == null ? null : invoke(bukkit, "getLocation");
        }
        if (loc == null) {
            Object pos = invoke(nmsOrBukkit, "chunkPosition");
            if (pos != null) {
                Integer x = number(invoke(pos, "getX"));
                if (x == null) {
                    x = number(field(pos, "x"));
                }
                Integer z = number(invoke(pos, "getZ"));
                if (z == null) {
                    z = number(field(pos, "z"));
                }
                if (x != null && z != null) {
                    return new int[]{x, z};
                }
            }
            return null;
        }
        Integer bx = number(invoke(loc, "getBlockX"));
        Integer bz = number(invoke(loc, "getBlockZ"));
        if (bx == null || bz == null) {
            return null;
        }
        return new int[]{bx >> 4, bz >> 4};
    }

    private static Integer number(Object v) {
        return v instanceof Number n ? n.intValue() : null;
    }

    private static Object invoke(Object target, String method) {
        if (target == null) {
            return null;
        }
        try {
            return target.getClass().getMethod(method).invoke(target);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Object field(Object target, String name) {
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                var f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (ReflectiveOperationException e) {
                c = c.getSuperclass();
            }
        }
        return null;
    }
}
