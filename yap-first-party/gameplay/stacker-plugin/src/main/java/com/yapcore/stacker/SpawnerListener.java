package com.yapcore.stacker;

import com.yapcore.sched.YapSched;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Spawner place/break stacking + wand UI. */
public final class SpawnerListener implements Listener {

    private final StackerPlugin plugin;
    private final SpawnerStackService spawners;
    private final StackerItems tools;

    public SpawnerListener(StackerPlugin plugin, SpawnerStackService spawners, StackerItems tools) {
        this.plugin = plugin;
        this.spawners = spawners;
        this.tools = tools;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!spawners.enabled() || !plugin.stackerConfig().stackOnPlace()) {
            return;
        }
        if (event.getBlockPlaced().getType() != Material.SPAWNER) {
            return;
        }
        YapSched.region(plugin, event.getBlockPlaced().getLocation(), () -> {
            if (spawners.tryAbsorbIntoNearby(event.getBlockPlaced())) {
                // item already consumed by place; block removed into host
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!spawners.enabled() || !plugin.stackerConfig().breakOne()) {
            return;
        }
        Block block = event.getBlock();
        CreatureSpawner cs = spawners.asSpawner(block);
        if (cs == null) {
            return;
        }
        int size = spawners.getStack(cs);
        Player player = event.getPlayer();
        if (size <= 1) {
            return;
        }
        if (player.isSneaking()) {
            // break entire stack — drop all
            event.setDropItems(false);
            EntityType type = spawners.spawnType(cs);
            if (player.getGameMode() != GameMode.CREATIVE) {
                block.getWorld().dropItemNaturally(block.getLocation(),
                        spawners.createSpawnerItem(type, size));
            }
            return;
        }
        // remove one from stack, keep block
        event.setCancelled(true);
        event.setDropItems(false);
        spawners.setStack(cs, size - 1);
        if (player.getGameMode() != GameMode.CREATIVE) {
            block.getWorld().dropItemNaturally(block.getLocation(),
                    spawners.createSpawnerItem(spawners.spawnType(cs), 1));
        }
        player.sendMessage("Spawner stack: " + (size - 1));
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onWand(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!tools.isWand(hand)) {
            return;
        }
        if (!player.hasPermission("yapstacker.wand")) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        CreatureSpawner cs = spawners.asSpawner(block);
        if (cs == null) {
            return;
        }
        event.setCancelled(true);
        switch (event.getAction()) {
            case RIGHT_CLICK_BLOCK -> {
                if (player.isSneaking()) {
                    int n = spawners.absorbNearbyInto(block);
                    player.sendMessage("Absorbed " + n + " nearby spawners. Stack=" + spawners.getStack(cs));
                } else {
                    plugin.spawnerGui().open(player, block);
                }
            }
            case LEFT_CLICK_BLOCK -> player.sendMessage(
                    "Spawner " + spawners.spawnType(cs) + " stack=" + spawners.getStack(cs));
            default -> {
            }
        }
    }
}
