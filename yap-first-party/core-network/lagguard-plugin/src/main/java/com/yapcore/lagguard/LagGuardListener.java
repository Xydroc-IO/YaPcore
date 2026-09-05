package com.yapcore.lagguard;

import com.yapcore.regions.RegionServices;
import com.yapcore.sched.StaffBypass;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.InventoryHolder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

public final class LagGuardListener implements Listener {

    private final LagGuardPlugin plugin;
    private final ChunkBudgetTracker tracker;
    private LagGuardConfig config;
    private final AtomicLong lastLogMs = new AtomicLong();
    private final AtomicLong windowStartMs = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong windowTrips = new AtomicLong();
    private final AtomicLong lastAlertMs = new AtomicLong();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

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
        Location loc = event.getLocation();
        if (playerNearbyBypass(loc) || inExemptRegion(loc)) {
            return;
        }
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        Chunk chunk = loc.getChunk();
        String key = ChunkBudgetTracker.key(world.getName(), chunk.getX(), chunk.getZ());
        Entity[] inChunk = chunk.getEntities();

        if (entity instanceof TNTPrimed || entity.getType() == EntityType.TNT) {
            int tnt = 0;
            for (Entity e : inChunk) {
                if (e instanceof TNTPrimed || e.getType() == EntityType.TNT) {
                    tnt++;
                }
            }
            int tntLimit = config.scaled(config.maxPrimedTntPerChunk(), world.getName());
            if (tnt >= tntLimit) {
                event.setCancelled(true);
                tracker.tripTnt(key);
                noteEscalation(key, world);
                onTrip("tnt", world.getName(), chunk.getX(), chunk.getZ(), tnt);
            }
            return;
        }

        if (entity instanceof Minecart) {
            int minecartLimit = config.scaledCategoryCap(config.maxMinecartsPerChunk(), world.getName());
            if (MachineCapPolicy.overMinecartCap(countMinecarts(inChunk), minecartLimit)) {
                event.setCancelled(true);
                tracker.tripMinecart(key);
                noteEscalation(key, world);
                maybeEscalateCull(world, chunk, key);
                onTrip("minecart", world.getName(), chunk.getX(), chunk.getZ(), minecartLimit);
                return;
            }
        }

        EntityCapCategory category = EntityCapCategory.of(entity);
        int categoryLimit = config.scaledCategoryCap(config.entityCapLimit(category), world.getName());
        if (EntityCapPolicy.overLimit(countCategory(inChunk, category), categoryLimit)) {
            event.setCancelled(true);
            tracker.tripEntity(key);
            noteEscalation(key, world);
            maybeEscalateCull(world, chunk, key);
            onTrip("entity-cap-" + category.name().toLowerCase(), world.getName(),
                    chunk.getX(), chunk.getZ(), categoryLimit);
            return;
        }

        int entities = inChunk.length;
        int entityLimit = config.scaled(config.maxEntitiesPerChunk(), world.getName());
        if (entities >= entityLimit) {
            event.setCancelled(true);
            tracker.tripEntity(key);
            noteEscalation(key, world);
            maybeEscalateCull(world, chunk, key);
            onTrip("entities", world.getName(), chunk.getX(), chunk.getZ(), entities);
        }
    }

    private static int countMinecarts(Entity[] entities) {
        int n = 0;
        for (Entity e : entities) {
            if (e instanceof Minecart) {
                n++;
            }
        }
        return n;
    }

    private static int countCategory(Entity[] entities, EntityCapCategory category) {
        int n = 0;
        for (Entity e : entities) {
            if (EntityCapCategory.of(e) == category) {
                n++;
            }
        }
        return n;
    }

    private void noteEscalation(String key, World world) {
        if (!config.escalationEnabled()) {
            return;
        }
        tracker.noteTripForEscalation(key, config.escalationWindowTicks(), world.getFullTime());
    }

    /**
     * Optional once-per-window item cull when trips exceed threshold.
     * Dangerous — removes ground Item entities; gated off by default.
     */
    private void maybeEscalateCull(World world, Chunk chunk, String key) {
        if (!EscalationCullPolicy.shouldCull(
                config.escalationEnabled(),
                tracker.escalationTrips(key),
                config.escalationTripsThreshold())) {
            return;
        }
        if (!tracker.tryEscalationCull(key, config.escalationTripsThreshold(),
                config.escalationWindowTicks(), world.getFullTime())) {
            return;
        }
        Entity[] entities = chunk.getEntities();
        List<Item> items = new ArrayList<>();
        for (Entity e : entities) {
            if (e instanceof Item item) {
                items.add(item);
            }
        }
        int itemCap = config.scaledCategoryCap(config.entityCapItems(), world.getName());
        int remove = EscalationCullPolicy.itemsToRemove(
                items.size(), itemCap, config.escalationMaxItemsRemoved());
        if (remove <= 0) {
            return;
        }
        for (int i = 0; i < remove && i < items.size(); i++) {
            items.get(i).remove();
        }
        tracker.recordEscalationCull();
        plugin.getLogger().warning("escalation cull removed " + remove + " items @ "
                + world.getName() + "," + chunk.getX() + "," + chunk.getZ()
                + " (DANGER: drops destroyed — disable escalation.enabled if unexpected)");
    }

    /** Soft-depend YaPRegions: skip budgets inside named admin regions from {@code exempt-regions}. */
    private boolean inExemptRegion(Location loc) {
        if (config.exemptRegions().isEmpty()) {
            return false;
        }
        try {
            return RegionServices.find()
                    .flatMap(svc -> svc.at(loc))
                    .map(r -> config.isExemptRegionName(r.name()))
                    .orElse(false);
        } catch (NoClassDefFoundError | Exception ignored) {
            return false;
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
        if (playerNearbyBypass(loc) || inExemptRegion(loc)) {
            return;
        }
        World world = loc.getWorld();
        int cx = loc.getBlockX() >> 4;
        int cz = loc.getBlockZ() >> 4;
        String key = ChunkBudgetTracker.key(world.getName(), cx, cz);
        long tick = world.getFullTime();
        int hopperLimit = config.scaled(config.maxHopperTransfersPerWindow(), world.getName());
        if (!tracker.tryHopper(key, hopperLimit, config.hopperWindowTicks(), tick)) {
            event.setCancelled(true);
            tracker.tripHopper(key);
            noteEscalation(key, world);
            onTrip("hopper", world.getName(), cx, cz, -1);
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
        Location loc = block.getLocation();
        if (playerNearbyBypass(loc) || inExemptRegion(loc)) {
            return;
        }
        World world = block.getWorld();
        int cx = block.getX() >> 4;
        int cz = block.getZ() >> 4;
        String key = ChunkBudgetTracker.key(world.getName(), cx, cz);
        long tick = world.getFullTime();

        if (block.getType() == Material.OBSERVER && config.maxObserverEventsPerWindow() > 0) {
            int limit = config.scaledCategoryCap(config.maxObserverEventsPerWindow(), world.getName());
            if (!tracker.tryObserver(key, limit, config.observerWindowTicks(), tick)) {
                event.setNewCurrent(event.getOldCurrent());
                tracker.tripObserver(key);
                noteEscalation(key, world);
                onTrip("observer", world.getName(), cx, cz, -1);
            }
            return;
        }

        int redstoneLimit = config.scaled(config.maxRedstoneEventsPerWindow(), world.getName());
        if (!tracker.tryRedstone(key, redstoneLimit, config.redstoneWindowTicks(), tick)) {
            event.setNewCurrent(event.getOldCurrent());
            tracker.tripRedstone(key);
            noteEscalation(key, world);
            onTrip("redstone", world.getName(), cx, cz, -1);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (shouldCancelPiston(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (shouldCancelPiston(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /** @return true when the piston event should be cancelled */
    private boolean shouldCancelPiston(Block block) {
        if (!config.enabled() || config.maxPistonEventsPerWindow() <= 0) {
            return false;
        }
        Location loc = block.getLocation();
        if (playerNearbyBypass(loc) || inExemptRegion(loc)) {
            return false;
        }
        World world = block.getWorld();
        int cx = block.getX() >> 4;
        int cz = block.getZ() >> 4;
        String key = ChunkBudgetTracker.key(world.getName(), cx, cz);
        long tick = world.getFullTime();
        int limit = config.scaledCategoryCap(config.maxPistonEventsPerWindow(), world.getName());
        if (tracker.tryPiston(key, limit, config.pistonWindowTicks(), tick)) {
            return false;
        }
        tracker.tripPiston(key);
        noteEscalation(key, world);
        onTrip("piston", world.getName(), cx, cz, -1);
        return true;
    }

    /** Creative / staff / {@code yaplagguard.bypass} in the same chunk. */
    private boolean playerNearbyBypass(Location loc) {
        World world = loc.getWorld();
        if (world == null) {
            return false;
        }
        int cx = loc.getBlockX() >> 4;
        int cz = loc.getBlockZ() >> 4;
        for (Player player : world.getPlayers()) {
            Location pl = player.getLocation();
            if (pl.getBlockX() >> 4 != cx || pl.getBlockZ() >> 4 != cz) {
                continue;
            }
            if (StaffBypass.lag(player)) {
                return true;
            }
        }
        return false;
    }

    private void onTrip(String kind, String world, int cx, int cz, int value) {
        noteTripForAlert(kind, world, cx, cz);
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

    private void noteTripForAlert(String kind, String world, int cx, int cz) {
        int threshold = config.alertTripsPerMinute();
        if (threshold <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        long start = windowStartMs.get();
        if (now - start >= 60_000L) {
            windowStartMs.set(now);
            windowTrips.set(0);
        }
        long count = windowTrips.incrementAndGet();
        if (count < threshold) {
            return;
        }
        long last = lastAlertMs.get();
        if (now - last < 60_000L) {
            return;
        }
        if (!lastAlertMs.compareAndSet(last, now)) {
            return;
        }
        String msg = "YaPLagGuard alert: " + count + " trips/min (threshold=" + threshold
                + ") last=" + kind + " @" + world + "," + cx + "," + cz;
        plugin.getLogger().warning(msg);
        String webhook = config.alertWebhookUrl();
        if (webhook != null && !webhook.isBlank()) {
            postWebhook(webhook.trim(), msg);
        }
    }

    private void postWebhook(String url, String content) {
        com.yapcore.sched.YapSched.async(plugin, () -> {
            try {
                String json = "{\"content\":\"" + content.replace("\\", "\\\\").replace("\"", "\\\"")
                        + "\",\"allowed_mentions\":{\"parse\":[]}}";
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                        .build();
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() >= 400) {
                    plugin.getLogger().fine("alert webhook HTTP " + resp.statusCode());
                }
            } catch (Exception e) {
                plugin.getLogger().fine("alert webhook failed: " + e.getMessage());
            }
        });
    }
}
