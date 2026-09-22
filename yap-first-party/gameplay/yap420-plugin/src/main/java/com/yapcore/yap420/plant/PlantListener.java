package com.yapcore.yap420.plant;

import com.yapcore.sched.YapSched;
import com.yapcore.yap420.Yap420Config;
import com.yapcore.yap420.Yap420Plugin;
import com.yapcore.yap420.item.ItemBridge;
import com.yapcore.yap420.item.Yap420ItemIds;
import com.yapcore.yap420.persist.PlotStore;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Plant seeds on configured soil. */
public final class PlantListener implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final Yap420Plugin plugin;
    private final Yap420Config config;
    private final ItemBridge items;
    private final PlotRegistry plots;
    private final PlantDisplayService displays;
    private final PlotStore store;

    public PlantListener(
            Yap420Plugin plugin,
            Yap420Config config,
            ItemBridge items,
            PlotRegistry plots,
            PlantDisplayService displays,
            PlotStore store
    ) {
        this.plugin = plugin;
        this.config = config;
        this.items = items;
        this.plots = plots;
        this.displays = displays;
        this.store = store;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlant(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission("yap420.use")) {
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        String itemId = items.idOf(hand).orElse(null);
        StrainId strain = Yap420ItemIds.strainFromSeed(itemId).orElse(null);
        if (strain == null) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }
        event.setCancelled(true);
        Block soil = clicked;
        Block plantAt = clicked.getRelative(BlockFace.UP);
        if (!config.soils().contains(soil.getType())) {
            // Allow clicking air-above by clicking soil face
            if (config.soils().contains(clicked.getRelative(BlockFace.DOWN).getType())
                    && clicked.getType().isAir()) {
                plantAt = clicked;
                soil = clicked.getRelative(BlockFace.DOWN);
            } else {
                player.sendMessage(LEGACY.deserialize(config.messages().wrongSoil()));
                return;
            }
        }
        if (!plantAt.getType().isAir()) {
            player.sendMessage(LEGACY.deserialize(config.messages().noSpace()));
            return;
        }
        if (plots.contains(plantAt.getWorld().getName(), plantAt.getX(), plantAt.getY(), plantAt.getZ())) {
            player.sendMessage(LEGACY.deserialize(config.messages().noSpace()));
            return;
        }
        final Block finalSoil = soil;
        final Block finalPlant = plantAt;
        YapSched.region(plugin, finalPlant.getLocation(), () -> {
            if (!finalSoil.getType().isAir() && !config.soils().contains(finalSoil.getType())) {
                player.sendMessage(LEGACY.deserialize(config.messages().wrongSoil()));
                return;
            }
            if (!finalPlant.getType().isAir()) {
                player.sendMessage(LEGACY.deserialize(config.messages().noSpace()));
                return;
            }
            if (player.getGameMode() != GameMode.CREATIVE) {
                ItemStack again = player.getInventory().getItemInMainHand();
                if (items.idOf(again).filter(id -> id.equals(itemId)).isEmpty()) {
                    return;
                }
                again.setAmount(again.getAmount() - 1);
            }
            PlotState plot = new PlotState(
                    finalPlant.getWorld().getName(),
                    finalPlant.getX(),
                    finalPlant.getY(),
                    finalPlant.getZ(),
                    strain,
                    0,
                    System.currentTimeMillis(),
                    null);
            PlotState spawned = displays.spawn(plot);
            plots.put(spawned);
            store.saveAsync();
            player.sendMessage(LEGACY.deserialize(
                    config.messages().planted().replace("{strain}", strain.id())));
        });
    }
}
