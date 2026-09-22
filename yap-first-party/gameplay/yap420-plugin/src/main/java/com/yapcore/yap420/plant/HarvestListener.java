package com.yapcore.yap420.plant;

import com.yapcore.sched.YapSched;
import com.yapcore.yap420.Yap420Config;
import com.yapcore.yap420.Yap420Plugin;
import com.yapcore.yap420.item.ItemBridge;
import com.yapcore.yap420.item.Yap420ItemIds;
import com.yapcore.yap420.persist.PlotStore;
import com.yapcore.yap420.skill.HerbalismHook;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Location;
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

/** Harvest mature plants (interact or break). */
public final class HarvestListener implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final Yap420Plugin plugin;
    private final Yap420Config config;
    private final ItemBridge items;
    private final PlotRegistry plots;
    private final PlantDisplayService displays;
    private final PlotStore store;
    private final HerbalismHook herbalism;

    public HarvestListener(
            Yap420Plugin plugin,
            Yap420Config config,
            ItemBridge items,
            PlotRegistry plots,
            PlantDisplayService displays,
            PlotStore store,
            HerbalismHook herbalism
    ) {
        this.plugin = plugin;
        this.config = config;
        this.items = items;
        this.plots = plots;
        this.displays = displays;
        this.store = store;
        this.herbalism = herbalism;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        plots.get(block.getWorld().getName(), block.getX(), block.getY(), block.getZ()).ifPresent(plot -> {
            event.setCancelled(true);
            tryHarvest(event.getPlayer(), plot, true);
        });
        // Also check plant sitting in air above farmland break
        Block above = block.getRelative(0, 1, 0);
        plots.get(above.getWorld().getName(), above.getX(), above.getY(), above.getZ()).ifPresent(plot -> {
            event.setCancelled(true);
            tryHarvest(event.getPlayer(), plot, true);
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Entity entity = event.getRightClicked();
        if (!(entity instanceof ItemDisplay)) {
            return;
        }
        Location loc = entity.getLocation();
        plots.get(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ())
                .ifPresent(plot -> {
                    event.setCancelled(true);
                    tryHarvest(event.getPlayer(), plot, false);
                });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractBlock(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        // Skip seed plant path
        if (items.idOf(event.getPlayer().getInventory().getItemInMainHand())
                .flatMap(Yap420ItemIds::strainFromSeed).isPresent()
                && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        plots.get(block.getWorld().getName(), block.getX(), block.getY(), block.getZ())
                .ifPresent(plot -> {
                    event.setCancelled(true);
                    tryHarvest(event.getPlayer(), plot, event.getAction() == Action.LEFT_CLICK_BLOCK);
                });
    }

    private void tryHarvest(Player player, PlotState plot, boolean allowImmatureDestroy) {
        if (!player.hasPermission("yap420.use")) {
            return;
        }
        YapSched.region(plugin, player.getWorld(), plot.x(), plot.z(), () -> {
            PlotState current = plots.get(plot.world(), plot.x(), plot.y(), plot.z()).orElse(null);
            if (current == null) {
                return;
            }
            if (current.stage() < config.maxStageIndex()) {
                if (allowImmatureDestroy && player.getGameMode() == GameMode.CREATIVE) {
                    destroyOnly(current);
                    return;
                }
                player.sendMessage(LEGACY.deserialize(config.messages().notMature()
                        .replace("{stage}", String.valueOf(current.stage() + 1))
                        .replace("{max}", String.valueOf(config.stages()))));
                return;
            }
            displays.remove(current);
            plots.remove(current.world(), current.x(), current.y(), current.z());
            store.saveAsync();
            ItemStack drop = items.create(Yap420ItemIds.wetBud(current.strain()), 1).orElse(null);
            if (drop != null) {
                currentWorldDrop(current, drop);
            }
            herbalism.grantHarvestXp(player);
            player.sendMessage(LEGACY.deserialize(config.messages().harvested()
                    .replace("{strain}", current.strain().id())));
        });
    }

    private void destroyOnly(PlotState current) {
        displays.remove(current);
        plots.remove(current.world(), current.x(), current.y(), current.z());
        store.saveAsync();
    }

    private void currentWorldDrop(PlotState plot, ItemStack drop) {
        var world = org.bukkit.Bukkit.getWorld(plot.world());
        if (world == null) {
            return;
        }
        world.dropItemNaturally(new Location(world, plot.x() + 0.5, plot.y() + 0.5, plot.z() + 0.5), drop);
    }
}
