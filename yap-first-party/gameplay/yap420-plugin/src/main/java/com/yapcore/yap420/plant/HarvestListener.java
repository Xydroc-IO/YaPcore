package com.yapcore.yap420.plant;

import com.yapcore.sched.YapSched;
import com.yapcore.yap420.Yap420Config;
import com.yapcore.yap420.Yap420Keys;
import com.yapcore.yap420.Yap420Plugin;
import com.yapcore.yap420.item.ItemBridge;
import com.yapcore.yap420.item.Yap420ItemIds;
import com.yapcore.yap420.persist.PlotStore;
import com.yapcore.yap420.skill.HerbalismHook;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;

/**
 * Harvest mature plants (right-click / punch / break farmland).
 * Immature plants can be cut down with left-click or break (no bud drop).
 * <p>
 * ItemDisplays are not LivingEntities — punch uses {@link Player#getTargetEntity(int)}
 * (same pattern as drying racks), not {@code EntityDamageByEntityEvent}.
 */
public final class HarvestListener implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final Yap420Plugin plugin;
    private final Yap420Config config;
    private final Yap420Keys keys;
    private final ItemBridge items;
    private final PlotRegistry plots;
    private final PlantDisplayService displays;
    private final PlotStore store;
    private final HerbalismHook herbalism;

    public HarvestListener(
            Yap420Plugin plugin,
            Yap420Config config,
            Yap420Keys keys,
            ItemBridge items,
            PlotRegistry plots,
            PlantDisplayService displays,
            PlotStore store,
            HerbalismHook herbalism
    ) {
        this.plugin = plugin;
        this.config = config;
        this.keys = keys;
        this.items = items;
        this.plots = plots;
        this.displays = displays;
        this.store = store;
        this.herbalism = herbalism;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        findPlotAtBlock(block).ifPresent(plot -> {
            event.setCancelled(true);
            tryHarvest(event.getPlayer(), plot, true);
        });
    }

    /** Left-click air/block while looking at plant ItemDisplay (displays are not damageable). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLeftClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_AIR) {
            return;
        }
        Player player = event.getPlayer();
        Entity looked = player.getTargetEntity(6);
        if (looked != null) {
            Optional<PlotState> fromEnt = findPlotFromEntity(looked);
            if (fromEnt.isPresent()) {
                event.setCancelled(true);
                tryHarvest(player, fromEnt.get(), true);
                return;
            }
            if (clearOrphanDisplay(looked)) {
                event.setCancelled(true);
                player.sendMessage(LEGACY.deserialize("&7Cleared stuck plant visual."));
                return;
            }
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        // Skip seed plant path on right-click only; left-click always harvests/clears
        findPlotAtBlock(block).ifPresent(plot -> {
            event.setCancelled(true);
            tryHarvest(player, plot, true);
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND && event.getHand() != EquipmentSlot.OFF_HAND) {
            return;
        }
        // Let bone meal grow the plant instead of harvesting
        if (isHoldingBoneMeal(event.getPlayer())) {
            return;
        }
        Entity clicked = event.getRightClicked();
        Optional<PlotState> plot = findPlotFromEntity(clicked);
        if (plot.isPresent()) {
            event.setCancelled(true);
            tryHarvest(event.getPlayer(), plot.get(), event.getPlayer().isSneaking());
            return;
        }
        if (clearOrphanDisplay(clicked)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(LEGACY.deserialize("&7Cleared stuck plant visual."));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractBlock(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND && event.getHand() != EquipmentSlot.OFF_HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        // Let bone meal grow the plant instead of harvesting / messaging "not mature"
        if (isHoldingBoneMeal(event.getPlayer())) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        // Skip seed plant path
        if (items.idOf(event.getPlayer().getInventory().getItemInMainHand())
                .flatMap(Yap420ItemIds::strainFromSeed).isPresent()) {
            return;
        }
        findPlotAtBlock(block).ifPresent(plot -> {
            event.setCancelled(true);
            tryHarvest(event.getPlayer(), plot, false);
        });
    }

    private static boolean isHoldingBoneMeal(Player player) {
        return player.getInventory().getItemInMainHand().getType() == org.bukkit.Material.BONE_MEAL
                || player.getInventory().getItemInOffHand().getType() == org.bukkit.Material.BONE_MEAL;
    }

    private Optional<PlotState> findPlotAtBlock(Block block) {
        Optional<PlotState> at = plots.get(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        if (at.isPresent()) {
            return at;
        }
        // Plant lives in the air above farmland — clicking soil still counts
        Block above = block.getRelative(0, 1, 0);
        Optional<PlotState> up = plots.get(above.getWorld().getName(), above.getX(), above.getY(), above.getZ());
        if (up.isPresent()) {
            return up;
        }
        // Clicking the air/cross while looking slightly off — check below
        Block below = block.getRelative(0, -1, 0);
        return plots.get(below.getWorld().getName(), below.getX(), below.getY(), below.getZ());
    }

    private Optional<PlotState> findPlotFromEntity(Entity entity) {
        if (!(entity instanceof ItemDisplay)) {
            return Optional.empty();
        }
        String key = entity.getPersistentDataContainer().get(keys.plotId(), PersistentDataType.STRING);
        if (key != null && !key.isBlank()) {
            String[] parts = key.split("\\|");
            if (parts.length == 4) {
                try {
                    Optional<PlotState> byKey = plots.get(
                            parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
                    if (byKey.isPresent()) {
                        return byKey;
                    }
                } catch (NumberFormatException ignored) {
                    // fall through
                }
            }
        }
        Location loc = entity.getLocation();
        Optional<PlotState> at = plots.get(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        if (at.isPresent()) {
            return at;
        }
        return plots.get(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY() - 1, loc.getBlockZ());
    }

    /** Remove ItemDisplay tagged as YaP420 plant with no matching plot (ghost visual). */
    private boolean clearOrphanDisplay(Entity entity) {
        if (!(entity instanceof ItemDisplay display)) {
            return false;
        }
        String key = display.getPersistentDataContainer().get(keys.plotId(), PersistentDataType.STRING);
        if (key == null || key.isBlank()) {
            return false;
        }
        String[] parts = key.split("\\|");
        if (parts.length == 4) {
            try {
                if (plots.contains(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]))) {
                    return false; // real plot — don't orphan-clear
                }
            } catch (NumberFormatException ignored) {
                // treat as orphan
            }
        }
        display.remove();
        // sweep siblings with same tag
        World world = display.getWorld();
        if (world != null) {
            Location center = display.getLocation();
            for (Entity ent : world.getNearbyEntities(center, 1.5, 4.0, 1.5)) {
                if (ent instanceof ItemDisplay other) {
                    String k = other.getPersistentDataContainer().get(keys.plotId(), PersistentDataType.STRING);
                    if (key.equals(k)) {
                        other.remove();
                    }
                }
            }
        }
        return true;
    }

    private void tryHarvest(Player player, PlotState plot, boolean allowImmatureDestroy) {
        if (!player.hasPermission("yap420.use")) {
            return;
        }
        YapSched.region(plugin, player.getWorld(), plot.x(), plot.z(), () -> {
            PlotState current = plots.get(plot.world(), plot.x(), plot.y(), plot.z()).orElse(null);
            if (current == null) {
                sweepDisplays(plot);
                player.sendMessage(LEGACY.deserialize("&7Cleared stuck plant visual."));
                return;
            }
            if (current.stage() < config.maxStageIndex()) {
                if (allowImmatureDestroy) {
                    clearPlot(current);
                    player.sendMessage(LEGACY.deserialize("&7Cleared immature plant."));
                    return;
                }
                player.sendMessage(LEGACY.deserialize(config.messages().notMature()
                        .replace("{stage}", String.valueOf(current.stage() + 1))
                        .replace("{max}", String.valueOf(config.stages()))));
                return;
            }
            clearPlot(current);
            ItemStack drop = items.create(Yap420ItemIds.wetBud(current.strain()), 1).orElse(null);
            if (drop != null) {
                currentWorldDrop(current, drop);
            }
            herbalism.grantHarvestXp(player);
            player.sendMessage(LEGACY.deserialize(config.messages().harvested()
                    .replace("{strain}", current.strain().id())));
        });
    }

    private void clearPlot(PlotState current) {
        displays.remove(current);
        sweepDisplays(current);
        plots.remove(current.world(), current.x(), current.y(), current.z());
        store.saveAsync();
    }

    private void sweepDisplays(PlotState plot) {
        World world = org.bukkit.Bukkit.getWorld(plot.world());
        if (world == null) {
            return;
        }
        Location center = new Location(world, plot.x() + 0.5, plot.y() + 0.5, plot.z() + 0.5);
        for (Entity ent : world.getNearbyEntities(center, 1.5, 4.0, 1.5)) {
            if (!(ent instanceof ItemDisplay display)) {
                continue;
            }
            String key = display.getPersistentDataContainer().get(keys.plotId(), PersistentDataType.STRING);
            if (plot.key().equals(key)) {
                display.remove();
            }
        }
    }

    private void currentWorldDrop(PlotState plot, ItemStack drop) {
        var world = org.bukkit.Bukkit.getWorld(plot.world());
        if (world == null) {
            return;
        }
        world.dropItemNaturally(new Location(world, plot.x() + 0.5, plot.y() + 0.5, plot.z() + 0.5), drop);
    }
}
