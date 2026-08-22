package com.yapcore.knobs;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Beehive;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Block / world mechanic knobs from {@code knobs.yml}. */
public final class BlockKnobsListener implements Listener {

    private final KnobsConfig config;
    private final Map<UUID, BarrelSession> barrels = new HashMap<>();

    public BlockKnobsListener(KnobsConfig config) {
        this.config = config;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAnvil(PrepareAnvilEvent event) {
        if (!config.enabled()) {
            return;
        }
        var view = event.getView();
        if (!config.anvilCumulativeCost()) {
            if (view.getRepairCost() > 39) {
                view.setRepairCost(39);
            }
        }
        view.setMaximumRepairCost(Math.max(view.getMaximumRepairCost(), 40));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBeehiveInteract(PlayerInteractEvent event) {
        if (!config.enabled() || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        BlockState state = block.getState();
        if (!(state instanceof Beehive hive)) {
            return;
        }
        int max = config.beehiveMaxBees();
        if (hive.getMaxEntities() != max) {
            hive.setMaxEntities(max);
            hive.update();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(PortalCreateEvent event) {
        if (!config.enabled() || config.cryingObsidianPortalFrame()) {
            return;
        }
        if (event.getReason() != PortalCreateEvent.CreateReason.NETHER_PAIR
                && event.getReason() != PortalCreateEvent.CreateReason.END_PLATFORM) {
            // Still scan for crying obsidian in frame
        }
        boolean hasCrying = event.getBlocks().stream()
                .anyMatch(b -> b.getType() == Material.CRYING_OBSIDIAN);
        if (hasCrying) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLightning(LightningStrikeEvent event) {
        if (!config.enabled()) {
            return;
        }
        int range = config.lightningRodRange();
        if (range <= 0) {
            event.setCancelled(true);
            return;
        }
        // Expand/contract rod attraction vs vanilla ~128
        if (range == 128) {
            return;
        }
        var loc = event.getLightning().getLocation();
        int scan = Math.max(range, 128);
        Block nearest = null;
        double best = Double.MAX_VALUE;
        int y0 = loc.getBlockY();
        for (int dx = -scan; dx <= scan; dx++) {
            for (int dz = -scan; dz <= scan; dz++) {
                for (int dy = -16; dy <= 16; dy++) {
                    Block b = loc.getWorld().getBlockAt(
                            loc.getBlockX() + dx, y0 + dy, loc.getBlockZ() + dz);
                    if (b.getType() != Material.LIGHTNING_ROD) {
                        continue;
                    }
                    double d = b.getLocation().distanceSquared(loc);
                    if (d < best) {
                        best = d;
                        nearest = b;
                    }
                }
            }
        }
        if (nearest == null) {
            return;
        }
        double limit = (double) range * range;
        if (best > limit) {
            // Rod exists but outside configured attraction range — do not pull
            event.setCancelled(true);
        } else if (range > 128 && best <= limit) {
            // Extended attraction: snap strike to rod
            event.getLightning().teleport(nearest.getLocation().add(0.5, 1, 0.5));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBed(PlayerBedEnterEvent event) {
        if (!config.enabled() || config.bedExplode()) {
            return;
        }
        // Disallow explosive bed use in dimensions where beds explode
        if (event.getBedEnterResult() == PlayerBedEnterEvent.BedEnterResult.NOT_POSSIBLE_HERE) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("Beds cannot explode here (yap knobs).");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBarrelOpen(InventoryOpenEvent event) {
        if (!config.enabled() || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (event.getInventory().getType() != InventoryType.BARREL) {
            return;
        }
        int rows = config.barrelRows();
        if (rows < 1) {
            rows = 1;
        }
        if (rows > 6) {
            rows = 6;
        }
        if (rows == 3) {
            return;
        }
        Inventory src = event.getInventory();
        event.setCancelled(true);
        Inventory custom = Bukkit.createInventory(player, rows * 9, "Barrel");
        ItemStack[] contents = src.getContents();
        for (int i = 0; i < Math.min(contents.length, custom.getSize()); i++) {
            custom.setItem(i, contents[i]);
        }
        barrels.put(player.getUniqueId(), new BarrelSession(src, custom));
        player.openInventory(custom);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBarrelClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        BarrelSession session = barrels.remove(player.getUniqueId());
        if (session == null || event.getInventory() != session.custom()) {
            return;
        }
        ItemStack[] from = session.custom().getContents();
        ItemStack[] to = session.source().getContents();
        for (int i = 0; i < to.length; i++) {
            to[i] = i < from.length ? from[i] : null;
        }
        session.source().setContents(to);
    }

    private record BarrelSession(Inventory source, Inventory custom) {
    }
}
