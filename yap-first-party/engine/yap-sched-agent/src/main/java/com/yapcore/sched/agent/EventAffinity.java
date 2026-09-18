package com.yapcore.sched.agent;

/**
 * Walks Bukkit event types by class name so the agent does not compile against Paper.
 */
final class EventAffinity {

    private EventAffinity() {
    }

    static Object entityOf(Object event) {
        for (Class<?> type = event.getClass(); type != null; type = type.getSuperclass()) {
            String name = type.getName();
            if ("org.bukkit.event.player.PlayerEvent".equals(name)) {
                return invoke(event, "getPlayer");
            }
            if ("org.bukkit.event.entity.EntityEvent".equals(name)
                    || "org.bukkit.event.vehicle.VehicleEvent".equals(name)
                    || "org.bukkit.event.hanging.HangingEvent".equals(name)) {
                return invoke(event, "getEntity");
            }
            if ("org.bukkit.event.inventory.InventoryInteractEvent".equals(name)
                    || "org.bukkit.event.inventory.InventoryCloseEvent".equals(name)
                    || "org.bukkit.event.inventory.InventoryOpenEvent".equals(name)) {
                Object player = invoke(event, "getPlayer");
                if (player != null) {
                    return player;
                }
            }
        }
        return invoke(event, "getPlayer");
    }

    static Object locationOf(Object event) {
        for (Class<?> type = event.getClass(); type != null; type = type.getSuperclass()) {
            String name = type.getName();
            if ("org.bukkit.event.block.BlockEvent".equals(name)) {
                Object block = invoke(event, "getBlock");
                return block == null ? null : invoke(block, "getLocation");
            }
            if ("org.bukkit.event.world.ChunkEvent".equals(name)) {
                Object chunk = invoke(event, "getChunk");
                return chunk == null ? null : chunkLocation(chunk);
            }
            // WorldEvent: do not pin spawn — current-region probe is the ticking chunk.
        }
        Object entity = entityOf(event);
        return entity == null ? null : invoke(entity, "getLocation");
    }

    private static Object chunkLocation(Object chunk) {
        Object loc = invoke(chunk, "getBlock", 0, 0, 0);
        if (loc != null) {
            return loc;
        }
        try {
            Object world = chunk.getClass().getMethod("getWorld").invoke(chunk);
            int x = ((Number) chunk.getClass().getMethod("getX").invoke(chunk)).intValue();
            int z = ((Number) chunk.getClass().getMethod("getZ").invoke(chunk)).intValue();
            if (world == null) {
                return null;
            }
            Object block = world.getClass()
                    .getMethod("getBlockAt", int.class, int.class, int.class)
                    .invoke(world, x << 4, 0, z << 4);
            return block == null ? null : block.getClass().getMethod("getLocation").invoke(block);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Object invoke(Object target, String method) {
        try {
            return target.getClass().getMethod(method).invoke(target);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Object invoke(Object target, String method, int a, int b, int c) {
        try {
            Object block = target.getClass()
                    .getMethod(method, int.class, int.class, int.class)
                    .invoke(target, a, b, c);
            return block == null ? null : block.getClass().getMethod("getLocation").invoke(block);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
