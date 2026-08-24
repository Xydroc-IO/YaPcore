package com.yapcore.lagguard;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

public final class LagGuardListener implements Listener {

    private final LagGuardPlugin plugin;
    private final ChunkBudgetTracker tracker;
    private LagGuardConfig config;
    private final AtomicLong lastLogMs = new AtomicLong();

    public LagGuardListener(LagGuardPlugin plugin, LagGuardConfig config, ChunkBudgetTracker tracker) {
        this.plugin = plugin;
        this.config = config;
        this.tracker = tracker;
    }

    public void setConfig(LagGuardConfig config) {
        this.config = config;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(EntitySpawnEvent event) {
        if (!config.enabled()) {
            return;
        }
        Entity entity = event.getEntity();
        if (entity instanceof Player) {
            return;
        }
        if (bypass(entity)) {
            return;
        }
        Location loc = event.getLocation();
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        Chunk chunk = loc.getChunk();
        int entities = chunk.getEntities().length;
        if (entity instanceof TNTPrimed || entity.getType() == EntityType.TNT) {
            int tnt = 0;
            for (Entity e : chunk.getEntities()) {
                if (e instanceof TNTPrimed || e.getType() == EntityType.TNT) {
                    tnt++;
                }
            }
            if (tnt >= config.maxPrimedTntPerChunk()) {
                event.setCancelled(true);
                tracker.tripTnt();
                logTrip("tnt", world.getName(), chunk.getX(), chunk.getZ(), tnt);
            }
            return;
        }
        if (entities >= config.maxEntitiesPerChunk()) {
            event.setCancelled(true);
            tracker.tripEntity();
            logTrip("entities", world.getName(), chunk.getX(), chunk.getZ(), entities);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHopper(InventoryMoveItemEvent event) {
        if (!config.enabled()) {
            return;
        }
        Location loc = holderLocation(event.getSource().getHolder());
        if (loc == null) {
            loc = holderLocation(event.getDestination().getHolder());
        }
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        World world = loc.getWorld();
        int cx = loc.getBlockX() >> 4;
        int cz = loc.getBlockZ() >> 4;
        String key = ChunkBudgetTracker.key(world.getName(), cx, cz);
        long tick = world.getFullTime();
        if (!tracker.tryHopper(key, config.maxHopperTransfersPerWindow(),
                config.hopperWindowTicks(), tick)) {
            event.setCancelled(true);
            tracker.tripHopper();
            logTrip("hopper", world.getName(), cx, cz, -1);
        }
    }

    private static Location holderLocation(InventoryHolder holder) {
        if (holder instanceof org.bukkit.block.BlockState state) {
            return state.getLocation();
        }
        if (holder instanceof Entity entity) {
            return entity.getLocation();
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRedstone(BlockRedstoneEvent event) {
        if (!config.enabled()) {
            return;
        }
        Block block = event.getBlock();
        World world = block.getWorld();
        String key = ChunkBudgetTracker.key(world.getName(), block.getX() >> 4, block.getZ() >> 4);
        long tick = world.getFullTime();
        if (!tracker.tryRedstone(key, config.maxRedstoneEventsPerWindow(),
                config.redstoneWindowTicks(), tick)) {
            // Freeze current — prevent further propagation this tick
            event.setNewCurrent(event.getOldCurrent());
            tracker.tripRedstone();
            logTrip("redstone", world.getName(), block.getX() >> 4, block.getZ() >> 4, -1);
        }
    }

    private boolean bypass(Entity entity) {
        // No player-attached bypass on spawn; ops use permission on commands only.
        return false;
    }

    private void logTrip(String kind, String world, int cx, int cz, int value) {
        if (!config.logTrips()) {
            return;
        }
        long now = System.currentTimeMillis();
        long prev = lastLogMs.get();
        if (now - prev < 2_000L) {
            return;
        }
        if (!lastLogMs.compareAndSet(prev, now)) {
            return;
        }
        Logger log = plugin.getLogger();
        if (value >= 0) {
            log.info("budget trip kind=" + kind + " chunk=" + world + "," + cx + "," + cz + " n=" + value);
        } else {
            log.info("budget trip kind=" + kind + " chunk=" + world + "," + cx + "," + cz);
        }
    }
}
